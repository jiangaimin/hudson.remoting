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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.Writer;

/**
 * {@link Writer} that can be sent over to the remote {@link Channel},
 * so that the remote {@link Callable} can write to a local {@link Writer}.
 *
 * <h2>Usage</h2>
 * <pre>
 * final Writer out = new RemoteWriter(w);
 *
 * channel.call(new Callable() {
 *   public Object call() {
 *     // this will write to 'w'.
 *     out.write(...);
 *   }
 * });
 * </pre>
 *
 * @see RemoteInputStream
 * @author Kohsuke Kawaguchi
 */
public final class RemoteWriter extends Writer implements Serializable {
    /**
     * On local machine, this points to the {@link Writer} where
     * the data will be sent ultimately.
     *
     * On remote machine, this points to {@link ProxyOutputStream} that
     * does the network proxy.
     */
    private transient Writer core;

    public RemoteWriter(Writer core) {
        this.core = core;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        int id = Channel.current().export(core);
        oos.writeInt(id);
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        final Channel channel = Channel.current();
        assert channel !=null;

        this.core = new ProxyWriter(channel, ois.readInt());
    }

    private static final long serialVersionUID = 1L;


//
//
// delegation to core
//
//
    public void write(int c) throws IOException {
        core.write(c);
    }

    public void write(char[] cbuf) throws IOException {
        core.write(cbuf);
    }

    public void write(char[] cbuf, int off, int len) throws IOException {
        core.write(cbuf, off, len);
    }

    public void write(String str) throws IOException {
        core.write(str);
    }

    public void write(String str, int off, int len) throws IOException {
        core.write(str, off, len);
    }

    public Writer append(CharSequence csq) throws IOException {
        return core.append(csq);
    }

    public Writer append(CharSequence csq, int start, int end) throws IOException {
        return core.append(csq, start, end);
    }

    public Writer append(char c) throws IOException {
        return core.append(c);
    }

    public void flush() throws IOException {
        core.flush();
    }

    public void close() throws IOException {
        core.close();
    }
}
