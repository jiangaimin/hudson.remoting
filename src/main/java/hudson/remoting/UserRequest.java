/*******************************************************************************
 *
 * Copyright (c) 2004-2009 Oracle Corporation.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: 
*
*    Kohsuke Kawaguchi
 *     
 *
 *******************************************************************************/ 

package hudson.remoting;

import hudson.remoting.RemoteClassLoader.IClassLoader;
import hudson.remoting.ExportTable.ExportList;
import hudson.remoting.RemoteInvocationHandler.RPCRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;

/**
 * {@link Request} that can take {@link Callable} whose actual implementation
 * may not be known to the remote system in advance.
 *
 * <p>
 * This code assumes that the {@link Callable} object and all reachable code
 * are loaded by a single classloader.
 *
 * @author Kohsuke Kawaguchi
 */
final class UserRequest<RSP,EXC extends Throwable> extends Request<UserResponse<RSP,EXC>,EXC> {

    private final byte[] request;
    private final IClassLoader classLoaderProxy;
    private final String toString;
    /**
     * Objects exported by the request. This value will remain local
     * and won't be sent over to the remote side.
     */
    private transient final ExportList exports;

    public UserRequest(Channel local, Callable<?,EXC> c) throws IOException {
        exports = local.startExportRecording();
        try {
            request = serialize(c,local);
        } finally {
            exports.stopRecording();
        }

        this.toString = c.toString();
        ClassLoader cl = getClassLoader(c);
        classLoaderProxy = RemoteClassLoader.export(cl,local);
    }

    /*package*/ static ClassLoader getClassLoader(Callable<?,?> c) {
    	ClassLoader result = null;
        
    	if(c instanceof DelegatingCallable) {
        	result =((DelegatingCallable)c).getClassLoader();
        }
        else {
        	result = c.getClass().getClassLoader();
        }
        
        if (result == null) {
        	result = ClassLoader.getSystemClassLoader();
        }
        
        return result;
    }

    protected UserResponse<RSP,EXC> perform(Channel channel) throws EXC {
        try {
            ClassLoader cl = channel.importedClassLoaders.get(classLoaderProxy);

            RSP r = null;
            Channel oldc = Channel.setCurrent(channel);
            try {
                Object o;
                try {
                    o = deserialize(channel,request,cl);
                } catch (ClassNotFoundException e) {
                    throw new ClassNotFoundException("Failed to deserialize the Callable object. Perhaps you needed to implement DelegatingCallable?",e);
                }

                Callable<RSP,EXC> callable = (Callable<RSP,EXC>)o;
                if(channel.isRestricted && !(callable instanceof RPCRequest))
                    // if we allow restricted channel to execute arbitrary Callable, the remote JVM can pick up many existing
                    // Callable implementations (such as ones in Hudson's FilePath) and do quite a lot. So restrict that.
                    // OTOH, we need to allow RPCRequest so that method invocations on exported objects will go through.
                    throw new SecurityException("Execution of "+callable.toString()+" is prohibited because the channel is restricted");

                ClassLoader old = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(cl);
                // execute the service
                try {
                    r = callable.call();
                } finally {
                    Thread.currentThread().setContextClassLoader(old);
                }
            } finally {
                Channel.setCurrent(oldc);
            }

            return new UserResponse<RSP,EXC>(serialize(r,channel),false);
        } catch (Throwable e) {
            // propagate this to the calling process
            try {
                byte[] response;
                try {
                    response = _serialize(e, channel);
                } catch (NotSerializableException x) {
                    // perhaps the thrown runtime exception is of type we can't handle
                    response = serialize(new ProxyException(e), channel);
                }
                return new UserResponse<RSP,EXC>(response,true);
            } catch (IOException x) {
                // throw it as a lower-level exception
                throw (EXC)x;
            }
        }
    }

    private byte[] _serialize(Object o, final Channel channel) throws IOException {
        Channel old = Channel.setCurrent(channel);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos;
            if (channel.remoteCapability.supportsMultiClassLoaderRPC())
                oos = new MultiClassLoaderSerializer.Output(channel,baos);
            else
                oos = new ObjectOutputStream(baos);

            oos.writeObject(o);
            return baos.toByteArray();
        } finally {
            Channel.setCurrent(old);
        }
    }

    private byte[] serialize(Object o, Channel localChannel) throws IOException {
        try {
            return _serialize(o,localChannel);
        } catch( NotSerializableException e ) {
            IOException x = new IOException("Unable to serialize " + o);
            x.initCause(e);
            throw x;
        }
    }

    /*package*/ static Object deserialize(final Channel channel, byte[] data, ClassLoader defaultClassLoader) throws IOException, ClassNotFoundException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);

        ObjectInputStream ois;
        if (channel.remoteCapability.supportsMultiClassLoaderRPC()) {
            // this code is coupled with the ObjectOutputStream subtype above
            ois = new MultiClassLoaderSerializer.Input(channel, in);
        } else {
            ois = new ObjectInputStreamEx(in, defaultClassLoader);
        }
        return ois.readObject();
    }

    public void releaseExports() {
        exports.release();
    }

    public String toString() {
        return "UserRequest:"+toString;
    }

    private static final long serialVersionUID = 1L;
}

final class UserResponse<RSP,EXC extends Throwable> implements Serializable {
    private final byte[] response;
    private final boolean isException;

    public UserResponse(byte[] response, boolean isException) {
        this.response = response;
        this.isException = isException;
    }

    /**
     * Deserializes the response byte stream into an object.
     */
    public RSP retrieve(Channel channel, ClassLoader cl) throws IOException, ClassNotFoundException, EXC {
        Channel old = Channel.setCurrent(channel);
        try {
            Object o = UserRequest.deserialize(channel,response,cl);

            if(isException)
                throw (EXC)o;
            else
                return (RSP) o;
        } finally {
            Channel.setCurrent(old);
        }
    }

    private static final long serialVersionUID = 1L;
}
