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
import de.kalpatec.ksah.HttpServer.Options;
import de.kalpatec.ksah.HttpServerRequest;
import de.kalpatec.ksah.HttpServerResponse;
import de.kalpatec.ksah.util.ByteBufferCache;
import de.kalpatec.ksah.util.HttpUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is where we handle the actual IO for a given connection.
 *
 * The context first creates a request object and uses it to parse the request.
 * Next, it calls the handler with itself and waits for date to send back or the
 * end of the request.
 *
 * Finally, it handles connection keep-alive and 100-continue messages.
 *
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
class HttpContextImpl implements HttpContext, CompletionHandler<Integer, ByteBuffer>
{
    private final AsynchronousSocketChannel m_channel;

    private final Consumer<HttpContext> m_handler;

    private final Runnable m_closeHandler;

    private final long m_started;

    private final long m_maxConnectionTime;

    private final long m_timeout;

    private final TimeUnit m_timeoutUnit;

    private final ByteBufferCache m_bufferCache;

    private volatile HttpRequestImpl m_request;

    private volatile HttpResponseImpl m_response;

    private HttpContextImpl(
        AsynchronousSocketChannel channel,
        long maxConnectionTime,
        long timeout,
        TimeUnit timeoutUnit,
        Consumer<HttpContext> handler,
        ByteBufferCache bufferCache,
        Runnable closeHandler)
    {
        m_channel = channel;
        m_started = System.currentTimeMillis();
        m_handler = handler;
        m_closeHandler = closeHandler;
        m_maxConnectionTime = maxConnectionTime;
        m_timeout = timeout;
        m_timeoutUnit = timeoutUnit;
        m_request = new HttpRequestImpl(this);
        m_response = new HttpResponseImpl(this, m_started, m_maxConnectionTime);
        m_bufferCache = bufferCache;
    }

    @Override
    public void completed(Integer result, ByteBuffer buffer)
    {
        if (result > -1)
        {
            String next = null;

            try
            {
                buffer.flip();
                next = m_request.process(buffer);
            }
            finally
            {
                m_bufferCache.checkin(buffer);
            }

            if (next == null)
            {
                receive();
            }
            else if (next.equals(HttpServerResponse.HTTP_OK))
            {
                m_handler.accept(this);
            }
            else if (next.equals(HttpServerResponse.HTTP_ENTITY_TOO_LARGE))
            {
                HttpUtils.sendToLarge(m_response, HttpUtils.IGNORE);
            }
            else if (!next.equals(HttpServerResponse.HTTP_CONTINUE))
            {
                HttpUtils.sendBadRequest(m_response, HttpUtils.IGNORE);
            }
        }
        else
        {
            failed(new IOException("Connection reset by peer"), buffer);
        }
    }


    @Override
    public void failed(Throwable exc, ByteBuffer buffer)
    {
        Logger.getLogger(HttpContextImpl.class.getName()).log(Level.FINE, "Timeout connection", exc);

        try
        {
            m_channel.close();
        }
        catch (IOException ex)
        {
            Logger.getLogger(HttpContextImpl.class.getName()).log(Level.FINE, ex.getMessage(), ex);
        }
        m_bufferCache.checkin(buffer);
        m_closeHandler.run();
    }

    void receive()
    {
        ByteBuffer buffer = m_bufferCache.checkout();
        m_channel.read(buffer, m_timeout, m_timeoutUnit, buffer, this);
    }

    void send(ByteBuffer[] buffers, CompletionHandler<Void,Void> handler)
    {
        send(buffers, null, handler);
    }

    <A> void send(ByteBuffer[] buffers, A attachment, CompletionHandler<Void,? super A> handler)
    {
        long total = 0;
        for (ByteBuffer buffer : buffers)
        {
            total += buffer.remaining();
        }
        m_channel.write(buffers, 0, buffers.length, m_timeout, m_timeoutUnit, total, new CompletionHandler<Long, Long>()
        {
            @Override
            public void completed(Long result, Long remaining)
            {
                if (result < remaining)
                {
                    m_channel.write(buffers, 0, buffers.length,m_timeout, m_timeoutUnit, remaining - result, this);
                }
                else
                {
                    handler.completed(null, attachment);
                }
            }

            @Override
            public void failed(Throwable exc, Long remaining)
            {
                handler.failed(exc, attachment);
            }
        });
    }

    private volatile boolean m_send100 = false;

    void send100()
    {
        m_send100 = true;
        HttpUtils.sendContinue(new HttpResponseImpl(this, m_started, m_maxConnectionTime));
    }

    void finish()
    {
        if (m_send100)
        {
            m_send100 = false;
            receive();
        }
        else if ("keep-alive".equals(m_response.getConnectionValue()))
        {
            m_request = new HttpRequestImpl(this);
            m_response = new HttpResponseImpl(this, m_started, m_maxConnectionTime);
            receive();
        }
        else
        {
            try
            {
                m_channel.close();
            }
            catch (IOException ex)
            {
                Logger.getLogger(HttpContextImpl.class.getName()).log(Level.FINE, ex.getMessage(), ex);
            }
            m_closeHandler.run();
        }
    }

    static void handle(AsynchronousSocketChannel channel,
        Options options,
        ByteBufferCache bufferCache,
        Consumer<HttpContext> handler,
        Runnable closeHandler)
    {
        HttpContextImpl context = new HttpContextImpl(channel,
            options.maxConnectionTime(),
            options.timeoutValue(),
            options.timeoutUnit(),
            handler, bufferCache, closeHandler);

        context.receive();
    }

    @Override
    public HttpServerRequest request()
    {
        return m_request;
    }

    @Override
    public HttpServerResponse response()
    {
        return m_response;
    }
}
