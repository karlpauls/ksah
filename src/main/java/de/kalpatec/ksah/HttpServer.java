/*
 * Copyright 2016 Karl Pauls (karlpauls@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.kalpatec.ksah;

import de.kalpatec.ksah.impl.HttpServerImpl;
import de.kalpatec.ksah.util.ByteBufferCache;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A lightweight, asynchronous, and non blocking web server.
 *
 * <p>
 *
 * Example:
 * <pre>
 *  HttpServer server = HttpServer.create(new InetSocketAddress(8080),StaticHandler.create():handle);
 * </pre>
 *
* @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
public interface HttpServer
{
    /**
     * Create a new HttpServer
     * @param address The address to listen on.
     * @param handler The handler to pass incomming requests to.
     * @return the instance.
     *
     * @throws java.io.IOException if there is a problem listing on the given address.
     */
    public static HttpServer create(InetSocketAddress address, Consumer<HttpContext> handler) throws IOException
    {
        return create(address, options(), handler);
    }

    /**
     * Create a new HttpServer
     * @param address The address to listen on.
     * @param options The options.
     * @param handler The handler to pass incomming requests to.
     * @return the instance.
     *
     * @throws java.io.IOException if there is a problem listing on the given address.
     */
    public static HttpServer create(InetSocketAddress address, Options options, Consumer<HttpContext> handler) throws IOException
    {
        return new HttpServerImpl(address, options, handler).start();
    }
    /**
     * Stop listening.
     *
     * This method return directly without waiting for the actual shutdown.
     *
     * @return the instance.
     */
    public HttpServer close();

    /**
     * Await close.
     * <p>
     * This method will block until the server is shutdown after a call to close.
     *
     * @param timeout how long to wait.
     * @param unit the unti for the timeout.
     * @return true if the server is shutdown otherwise, false (i.e., the timeout was hit).
     * @throws InterruptedException if the current thread gets interrupted.
     */
    public boolean awaitClose(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Get a new options object.
     *
     * @return a new options object configured with the defaults.
     */
    public static Options options()
    {
        return new Options();
    }

    /**
     * Options are used to configure a HttpServer
     */
    public static class Options
    {
        private volatile boolean SO_KEEPALIVE = true;
        private volatile boolean TCP_NODELAY = true;
        private volatile boolean SO_REUSEADDR = true;
        private volatile int SO_RCVBUF = 64 * 1024;
        private volatile int SO_SNDBUF = 64 * 1024;
        private volatile int backlog = 1024;
        private volatile ExecutorService executor = Executors.newCachedThreadPool();
        private volatile int nativeBufferCacheSize = 16 * 1024 * 1024;
        private volatile long timeout = 2;
        private volatile TimeUnit timeoutUnit = TimeUnit.SECONDS;
        private volatile long maxConnectionTime = 10000;
        private volatile int maxConnections = 1024;

        /**
         * Clone this instance
         *
         * @return a clone of this instance.
         */
        public Options copy()
        {
            return new Options()
                .keepAlive(keepAlive())
                .noDelay(noDelay())
                .reuseAddress(reuseAddresse())
                .rcvBuf(rcvBuf())
                .sndBuf(sndBuf())
                .backlog(backlog())
                .executor(executor())
                .nativeBufferCacheSize(nativeBufferCacheSize())
                .timeout(timeoutValue(), timeoutUnit())
                .maxConnectionTime(maxConnectionTime())
                .maxConnections(maxConnections());
        }

        /**
         * Default is true.
         *
         * @see StandardSocketOptions#SO_KEEPALIVE.
         *
         * @param value the value.
         * @return this instance.
         */
        public Options keepAlive(boolean value)
        {
            SO_KEEPALIVE = value;
            return this;
        }

        /**
         * Default is true
         *
         * @see StandardSocketOptions#SO_KEEPALIVE.
         *
         * @param value the value.
         * @return this instance.
         */
        public Options noDelay(boolean value)
        {
            TCP_NODELAY = value;
            return this;
        }

        /**
         * Default is true
         *
         * @see StandardSocketOptions#SO_REUSEADDR.
         *
         * @param value the value.
         * @return this instance.
         */
        public Options reuseAddress(boolean value)
        {
            SO_REUSEADDR = value;
            return this;
        }

        /**
         * Default is 64K
         *
         * also the max header size.
         *
         * @see StandardSocketOptions#SO_RCVBUF.
         *
         * @param value the value (will be ignored if not > 1k.
         * @return this instance.
         */
        public Options rcvBuf(int value)
        {
            if (value > 1024)
            {
                SO_RCVBUF = value;
            }
            return this;
        }

        /**
         * Default is 64K
         *
         * @see StandardSocketOptions#SO_SNDBUF.
         *
         * @param value the value (will be ignored if not > 1k.
         * @return this instance.
         */
        public Options sndBuf(int value)
        {
            if (value > 1024)
            {
                SO_SNDBUF = value;
            }
            return this;
        }

        /**
         * The backlog size (optional, default: 1024).
         *
         * @see AsynchronousServerSocketChannel#bind(java.net.SocketAddress, int)
         *
         * @param value backlog (will be ignored if not > 0).
         * @return the options.
         */
        public Options backlog(int value)
        {
            if (backlog > 0)
            {
                backlog = value;
            }
            return this;
        }

        /**
         * Default is Executors.newCachedThreadPool().
         *
         * @see Executors#newCachedThreadPool()
         *
         * @param value the value that needs to be exclusive for the HttpServer configured with it.
         * (ignored if null)
         * @return this instance.
         */
        public Options executor(ExecutorService value)
        {
            if (value != null)
            {
                executor = value;
            }
            return this;
        }

        /**
         * Default is 16M.
         *
         * @see ByteBufferCache
         *
         * @param value the value that needs to be exclusive for the HttpServer configured with it.
         * (ignored if null)
         * @return this instance.
         */
        public Options nativeBufferCacheSize(int value)
        {
            if (value < 1024)
            {
                nativeBufferCacheSize = 0;
            }
            else
            {
                nativeBufferCacheSize = 1 << (31 - Integer.numberOfLeadingZeros(value));
            }
            return this;
        }

        /**
         * Default is 2 SECONDS.
         *
         * The read/write timeout for a socket
         *
         * @param value the value (ignored if not > 0 ).
         * @param unit the unit (ignored if not > 0 or unit != null).
         * @return this instance.
         */
        public Options timeout(long value, TimeUnit unit)
        {
            if (value > 0 && unit != null)
            {
                timeout = value;
                timeoutUnit = unit;
            }

            return this;
        }

        /**
         * The max number of concurrent connections (optional, default: \"1024\").
         *
         * @param value the value (ignored if not > 0)
         * @return this instance
         */
        public Options maxConnections(int value)
        {
            if (value > 0)
            {
                maxConnections = value;
            }
            return this;
        }

        /**
         * @see Options#maxConnections(int)
         * @return the value.
         */
        public int maxConnections()
        {
            return maxConnections;
        }

        /**
         * @see Options#timeout(long, java.util.concurrent.TimeUnit)
         * @return the value.
         */
        public long timeoutValue()
        {
            return timeout;
        }

        /**
         * @see Options#timeoutUnit()
         * @return the value.
         */
        public TimeUnit timeoutUnit()
        {
            return timeoutUnit;
        }

        /**
         * @see Options#keepAlive(boolean)
         * @return the value.
         */
        public boolean keepAlive()
        {
            return SO_KEEPALIVE;
        }

        /**
         * @see Options#noDelay(boolean)
         * @return the value.
         */
        public boolean noDelay()
        {
            return TCP_NODELAY;
        }

        /**
         * @see Options#reuseAddress(boolean)
         * @return the value.
         */
        public boolean reuseAddresse()
        {
            return SO_REUSEADDR;
        }

        /**
         * @see Options#rcvBuf(int)
         * @return the value.
         */
        public int rcvBuf()
        {
            return SO_RCVBUF;
        }

        /**
         * @see Options#sndBuf(int)
         * @return the value.
         */
        public int  sndBuf()
        {
            return SO_SNDBUF;
        }

        /**
         * @see Options#backlog(int)
         * @return the value.
         */
        public int backlog()
        {
            return backlog;
        }

        /**
         * @see Options#executor(java.util.concurrent.ExecutorService)
         * @return the value.
         */
        public ExecutorService executor()
        {
            return executor;
        }

        /**
         * @see Options#nativeBufferCacheSize(int)
         * @return the value.
         */
        public int nativeBufferCacheSize()
        {
            return nativeBufferCacheSize;
        }

        /**
         * The max time a connection is keep-alive in milliseconds, 0 for no keep-alive (optional, default: 10000)."
         *
         * @param value the value (ignored if not >= 0)
         *
         * @return the instance.
         */
        public Options maxConnectionTime(long value)
        {
            if (value >= 0)
            {
                maxConnectionTime = value;
            }

            return this;
        }

        /**
         * @see Options#maxConnectionTime(long)
         * @return the value.
         */
        public long maxConnectionTime()
        {
            return maxConnectionTime;
        }
    }
}
