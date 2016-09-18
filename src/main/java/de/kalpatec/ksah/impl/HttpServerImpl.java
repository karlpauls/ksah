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
package de.kalpatec.ksah.impl;

import de.kalpatec.ksah.HttpContext;
import de.kalpatec.ksah.HttpServer;
import de.kalpatec.ksah.util.ByteBufferCache;
import de.kalpatec.ksah.util.HttpUtils;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
public class HttpServerImpl implements HttpServer, CompletionHandler<AsynchronousSocketChannel,Void>
{
    private final AsynchronousServerSocketChannel m_assc;
    private final AsynchronousChannelGroup m_group;
    private final ByteBufferCache m_bufferCache;
    private final Options m_options;
    private final Consumer<HttpContext> m_handler;
    private final ConcurrentHashMap<AsynchronousSocketChannel,AsynchronousSocketChannel> m_openSockets = new ConcurrentHashMap();

    public HttpServerImpl(InetSocketAddress address, Options options, Consumer<HttpContext> handler) throws IOException
    {
        m_options = options.copy();
        m_group = AsynchronousChannelGroup.withThreadPool(options.executor());
        m_assc = AsynchronousServerSocketChannel.open(m_group);
        m_assc.setOption(StandardSocketOptions.SO_RCVBUF, options.rcvBuf());
        m_assc.setOption(StandardSocketOptions.SO_REUSEADDR, options.reuseAddresse());
        try
        {
            m_assc.bind(address, options.backlog());
        }
        catch (IOException ex)
        {
            try
            {
                m_assc.close();
            }
            catch (IOException ignore)
            {
                Logger.getLogger(HttpServerImpl.class.getName()).log(Level.FINE, ignore.getMessage(), ignore);
            }
            throw ex;
        }
        m_bufferCache = ByteBufferCache.create(options.nativeBufferCacheSize() / options.rcvBuf(), options.rcvBuf());

        m_handler = handler;
    }

    public HttpServerImpl start()
    {
        m_assc.accept(null, this);

        return this;
    }

    @Override
    public void completed(AsynchronousSocketChannel socket, Void attachment)
    {
        m_openSockets.put(socket, socket);

        m_assc.accept(null, this);

        try
        {
            socket.setOption(StandardSocketOptions.SO_KEEPALIVE, m_options.keepAlive());
            socket.setOption(StandardSocketOptions.TCP_NODELAY, m_options.noDelay());
            socket.setOption(StandardSocketOptions.SO_SNDBUF, m_options.sndBuf());
        }
        catch (Exception ex)
        {
            Logger.getLogger(HttpServerImpl.class.getName()).log(Level.WARNING, ex.getMessage(), ex);
        }

        HttpContextImpl.handle(
            socket,
            m_options,
            m_bufferCache,
            m_openSockets.size() < m_options.maxConnections()
                ?
                m_handler
                :
                (context) -> HttpUtils.sendUnavailable(context.response(), HttpUtils.IGNORE),
            () -> m_openSockets.remove(socket));
    }

    @Override
    public void failed(Throwable exc, Void attachment)
    {
        if (m_assc.isOpen())
        {
            Logger.getLogger(HttpServerImpl.class.getName()).log(Level.INFO, exc.getMessage(), exc);

            m_assc.accept(null, this);
        }
    }

    @Override
    public HttpServer close()
    {
        try
        {
            m_assc.close();
        }
        catch (IOException ex)
        {
            Logger.getLogger(HttpServerImpl.class.getName()).log(Level.WARNING, ex.getMessage(), ex);
        }

        m_openSockets.keySet().forEach((socket) ->
        {
            try
            {
                socket.close();
            }
            catch (Exception ex)
            {
                Logger.getLogger(HttpServerImpl.class.getName()).log(Level.WARNING, null, ex);
            }
        });
        
        m_group.shutdown();

        return this;
    }

    @Override
    public boolean awaitClose(long timeout, TimeUnit unit) throws InterruptedException
    {
        try
        {
            m_group.awaitTermination(timeout, unit);
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
        return m_group.isTerminated();
    }
}
