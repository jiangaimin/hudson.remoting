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
import java.net.URL;

/**
 * {@link Callable} used to deliver a jar file to {@link RemoteClassLoader}.
 *
 * @author Kohsuke Kawaguchi
 */
final class PreloadJarTask implements DelegatingCallable<Boolean,IOException> {
    /**
     * Jar file to be preloaded.
     */
    private final URL[] jars;

    private transient ClassLoader target;

    PreloadJarTask(URL[] jars, ClassLoader target) {
        this.jars = jars;
        this.target = target;
    }

    public ClassLoader getClassLoader() {
        return target;
    }

    public Boolean call() throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (!(cl instanceof RemoteClassLoader))
            return false;

        RemoteClassLoader rcl = (RemoteClassLoader) cl;
        boolean r = false;
        for (URL jar : jars)
            r |= rcl.prefetch(jar);
        return r;
    }
}
