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

import de.kalpatec.ksah.HttpServerResponse;
import de.kalpatec.ksah.util.HttpUtils;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
class HttpResponseImpl implements HttpServerResponse
{
    private final HttpContextImpl m_context;

    private volatile String m_status = HTTP_OK;

    private final Map<String,String> m_headerMap = new HashMap<>();

    private volatile boolean m_committed = false;

    private final long m_started;

    private final long m_maxConnectionTime;

    public HttpResponseImpl(HttpContextImpl context, long started, long maxConnectionTime)
    {
        m_context = context;
        m_started = started;
        m_maxConnectionTime = maxConnectionTime;
    }

    @Override
    public <A> HttpResponseImpl write(ByteBuffer buffer, A attachment, CompletionHandler<Void,? super A> handler)
    {
        // as we say in the javadoc - this is not thread save but it is ok as
        // we want to be asynchronous and really don't expect concurrent access.
        if (!m_committed)
        {
            m_committed = true;
            m_context.send(new ByteBuffer[]{ByteBuffer.wrap(getHeaders().getBytes()), buffer}, attachment, handler);
        }
        else
        {
            m_context.send(new ByteBuffer[]{buffer}, attachment, handler);
        }

        return this;
    }

    @Override
    public HttpResponseImpl end()
    {
        // as we say in the javadoc - this is not thread save but it is ok as
        // we want to be asynchronous and really don't expect concurrent access.
        if (!m_committed)
        {
            m_committed = true;
            m_context.send(new ByteBuffer[]{ByteBuffer.wrap(getHeaders().getBytes())}, new CompletionHandler<Void, Void>()
            {
                @Override
                public void completed(Void result, Void attachment)
                {
                    m_context.finish();
                }

                @Override
                public void failed(Throwable exc, Void attachment)
                {
                    Logger.getLogger(HttpResponseImpl.class.getName()).log(Level.INFO, exc.getMessage(), exc);
                    m_context.finish();
                }
            });
        }
        else
        {
            m_context.finish();
        }

        return this;
    }

    @Override
    public HttpServerResponse setStatus(String status)
    {
        m_status = status;

        return this;
    }

    public String status()
    {
        return m_status;
    }

    @Override
    public HttpServerResponse setHeader(String header, String value)
    {
        m_headerMap.put(header, value);
        return this;
    }

    // We'll append all necessary headers and the given ones.
    private String getHeaders()
    {
        StringBuilder buffer = new StringBuilder("HTTP/1.1 ");

        buffer.append(m_status).append("\r\n");

        m_headerMap.put("Server", "ksah/0.1.0");
        m_headerMap.put("Connection", getConnectionValue());
        m_headerMap.put("Date", HttpUtils.formatDate(new Date()));

        // The caching headers should be set by the handler (oh well, for now we set some no caching defaults)
        m_headerMap.putIfAbsent("Cache-Control", "no-cache, no-store, must-revalidate");
        m_headerMap.putIfAbsent("Pragma", "no-cache");
        m_headerMap.putIfAbsent("Expires", "0");

        m_headerMap.forEach((key,value) -> {
            buffer.append(key).append(": ").append(value).append("\r\n");
        });

        buffer.append("\r\n");

        return buffer.toString();
    }

    // is this a keep-alive or not?
    String getConnectionValue()
    {
        return m_headerMap.getOrDefault("Connection",
            (m_status.equals(HTTP_BAD_REQUEST)
            ||
            m_status.equals(HTTP_ENTITY_TOO_LARGE)
            ||
            m_status.equals(HTTP_UNAVAILABLE)
            ||
            System.currentTimeMillis() - m_started > m_maxConnectionTime)
                ?
                "close"
                :
                HttpUtils.getConnectionHeaderValue(m_context.request()));
    }
}
