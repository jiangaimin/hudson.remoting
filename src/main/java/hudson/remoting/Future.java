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
 * Alias to {@link Future}.
 *
 * <p>
 * This alias is defined so that retro-translation won't affect
 * the publicly committed signature of the API.
 * 
 * @author Kohsuke Kawaguchi
 */
public interface Future<V> extends java.util.concurrent.Future<V> {
}
