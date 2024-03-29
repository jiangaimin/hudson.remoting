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

import java.util.concurrent.CancellationException;
import junit.framework.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Testing the basic features.
 * 
 * @author Kohsuke Kawaguchi
 */
public class SimpleTest extends RmiTestBase {
    public void test1() throws Exception {
        int r = channel.call(new Callable1());
        System.out.println("result=" + r);
        assertEquals(5,r);
    }

    public void test1Async() throws Exception {
        Future<Integer> r = channel.callAsync(new Callable1());
        System.out.println("result="+r.get());
        assertEquals(5,(int)r.get());
    }

    private static class Callable1 implements Callable<Integer, RuntimeException> {
        public Integer call() throws RuntimeException {
            System.err.println("invoked");
            return 5;
        }
    }


    public void test2() throws Exception {
        try {
            channel.call(new Callable2());
            fail();
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(),"foo");
        }
    }

    public void test2Async() throws Exception {
        try {
            Future<Integer> r = channel.callAsync(new Callable2());
            r.get();
            fail();
        } catch (ExecutionException e) {
            assertEquals(e.getCause().getMessage(),"foo");
        }
    }

    private static class Callable2 implements Callable<Integer, RuntimeException> {
        public Integer call() throws RuntimeException {
            throw new RuntimeException("foo");
        }
    }

    /**
     * Makes sure that proxied object can be sent back to the origin and resolve correctly.
     */
    public void test3() throws Exception {
        Foo c = new Foo() {};

        Foo r = channel.call(new Echo<Foo>(channel.export(Foo.class,c)));
        assertSame(c,r);
    }

    public static interface Foo {}

    private static class Echo<T> implements Callable<T,RuntimeException> {
        private final T t;

        Echo(T t) {
            this.t = t;
        }

        public T call() throws RuntimeException {
            return t;
        }
    }

    /**
     * Checks whether {@link Future#cancel} behaves according to spec.
     * Currently seems to be used by MavenBuilder.call and Proc.RemoteProc.kill
     * (in turn used by MercurialSCM.joinWithTimeout when polling on remote host).
     */
    //@Bug(4611)
    public void testCancellation() throws Exception {
        Cancellable task = new Cancellable();
        Future<Integer> r = channel.callAsync(task);
        r.cancel(true);
        try {
            r.get();
            fail("should not return normally");
        } catch (CancellationException x) {
            // right
        }
        assertTrue(r.isCancelled());
        assertFalse(task.ran);
        // XXX ought to also test various other aspects: cancelling before start, etc.
    }
    private static class Cancellable implements Callable<Integer, InterruptedException> {
        boolean ran;
        public Integer call() throws InterruptedException {
            Thread.sleep(9999);
            ran = true;
            return 0;
        }
    }

    public static Test suite() throws Exception {
        return buildSuite(SimpleTest.class);
    }
}
