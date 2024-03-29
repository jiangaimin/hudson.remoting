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

/**
 * Performs GC.
 * 
 * @author Kohsuke Kawaguchi
 */
class GCCommand extends Command {
    protected void execute(Channel channel) {
        System.gc();
    }

    private static final long serialVersionUID = 1L;
}
