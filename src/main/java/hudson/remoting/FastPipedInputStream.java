/*******************************************************************************
 *
 * Copyright (c) 2006-2008 Makoto YUI
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *       Makoto YUI - initial implementation 
 *
 *******************************************************************************/ 

/*
 * @(#)$Id: FastPipedInputStream.java 3619 2008-03-26 07:23:03Z yui $
 *
 * Copyright 2006-2008 Makoto YUI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Makoto YUI - initial implementation
 */
package hudson.remoting;

import java.io.InputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * This class is equivalent to <code>java.io.PipedInputStream</code>. In the
 * interface it only adds a constructor which allows for specifying the buffer
 * size. Its implementation, however, is much simpler and a lot more efficient
 * than its equivalent. It doesn't rely on polling. Instead it uses proper
 * synchronization with its counterpart {@link FastPipedOutputStream}.
 *
 * @author WD
 * @link http://developer.java.sun.com/developer/bugParade/bugs/4404700.html
 * @see FastPipedOutputStream
 */
public class FastPipedInputStream extends InputStream {

    final byte[] buffer;
    /**
     * Once closed, this is set to the stack trace of who closed it.
     */
    ClosedBy closed = null;
    int readLaps = 0;
    int readPosition = 0;
    WeakReference<FastPipedOutputStream> source;
    int writeLaps = 0;
    int writePosition = 0;

    private final Throwable allocatedAt = new Throwable();
    
    /**
     * Creates an unconnected PipedInputStream with a default buffer size.
     */
    public FastPipedInputStream() {
        this.buffer = new byte[0x10000];
    }

    /**
     * Creates a PipedInputStream with a default buffer size and connects it to
     * <code>source</code>.
     * @exception IOException It was already connected.
     */
    public FastPipedInputStream(FastPipedOutputStream source) throws IOException {
        this(source, 0x10000 /* 65536 */);
    }

    /**
     * Creates a PipedInputStream with buffer size <code>bufferSize</code> and
     * connects it to <code>source</code>.
     * @exception IOException It was already connected.
     */
    public FastPipedInputStream(FastPipedOutputStream source, int bufferSize) throws IOException {
        if(source != null) {
            connect(source);
        }
        this.buffer = new byte[bufferSize];
    }

    private FastPipedOutputStream source() throws IOException {
        FastPipedOutputStream s = source.get();
        if (s==null)    throw (IOException)new IOException("Writer side has already been abandoned").initCause(allocatedAt);
        return s;
    }

    @Override
    public int available() throws IOException {
        /* The circular buffer is inspected to see where the reader and the writer
         * are located.
         */
        synchronized (buffer) {
            return writePosition > readPosition /* The writer is in the same lap. */? writePosition
                    - readPosition
                    : (writePosition < readPosition /* The writer is in the next lap. */? buffer.length
                            - readPosition + 1 + writePosition
                            :
                            /* The writer is at the same position or a complete lap ahead. */
                            (writeLaps > readLaps ? buffer.length : 0));
        }
    }

    /**
     * @exception IOException The pipe is not connected.
     */
    @Override
    public void close() throws IOException {
        if(source == null) {
            throw new IOException("Unconnected pipe");
        }

        synchronized(buffer) {
            closed = new ClosedBy();
            // Release any pending writers.
            buffer.notifyAll();
        }
    }

    /**
     * @exception IOException The pipe is already connected.
     */
    public void connect(FastPipedOutputStream source) throws IOException {
        if(this.source != null) {
            throw new IOException("Pipe already connected");
        }
        this.source = new WeakReference<FastPipedOutputStream>(source);
        source.sink = new WeakReference<FastPipedInputStream>(this);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

    @Override
    public void mark(int readLimit) {
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    public int read() throws IOException {
        byte[] b = new byte[1];
        return read(b, 0, b.length) == -1 ? -1 : (255 & b[0]);
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * @exception IOException The pipe is not connected.
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if(source == null) {
            throw new IOException("Unconnected pipe");
        }

        while (true) {
            synchronized(buffer) {
                if(writePosition == readPosition && writeLaps == readLaps) {
                    if(closed != null) {
                        return -1;
                    }
                    source(); // make sure the sink is still trying to read, or else fail the write.

                    // Wait for any writer to put something in the circular buffer.
                    try {
                        buffer.wait(FastPipedOutputStream.TIMEOUT);
                    } catch (InterruptedException e) {
                        throw new IOException(e.getMessage());
                    }
                    // Try again.
                    continue;
                }

                // Don't read more than the capacity indicated by len or what's available
                // in the circular buffer.
                int amount = Math.min(len, (writePosition > readPosition ? writePosition
                        : buffer.length)
                        - readPosition);
                System.arraycopy(buffer, readPosition, b, off, amount);
                readPosition += amount;

                if(readPosition == buffer.length) {// A lap was completed, so go back.
                    readPosition = 0;
                    ++readLaps;
                }

                buffer.notifyAll();
                return amount;
            }
        }
    }

    static final class ClosedBy extends Throwable {
        ClosedBy() {
            super("The pipe was closed at...");
        }
    }
}
