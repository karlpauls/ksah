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

import de.kalpatec.ksah.HttpServerRequest;
import de.kalpatec.ksah.HttpServerResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
class HttpRequestImpl implements HttpServerRequest
{
    private final List<String> m_header = new ArrayList<>();
    private final Map<String,String> m_headerMap = new LinkedHashMap<>();
    private final Map<String,String> m_lowerCaseHeaderMap = new HashMap<>();

    private volatile String m_method;
    private volatile String m_version;
    private volatile String m_uri;
    private volatile String m_path;

    private volatile ByteArrayOutputStream m_headerBuilder = new ByteArrayOutputStream();

    private volatile int m_current = 0;
    private volatile ByteArrayOutputStream m_rest = new ByteArrayOutputStream();
    private volatile ByteArrayInputStream m_body = null;

    private volatile boolean m_headersDone;

    private volatile long m_contentLength = 0;
    private volatile boolean m_send100 = false;
    private final HttpContextImpl m_context;

    private volatile Consumer<ByteBuffer> m_handler = null;
    private volatile long m_sendCount;
    private volatile boolean m_r = false;
    private volatile boolean m_e = true;

    HttpRequestImpl(HttpContextImpl context)
    {
        m_context = context;
    }

    String process(ByteBuffer buffer)
    {
        // if we have a handler here we are waiting for body data
        if (m_handler != null)
        {
            // hence send it to the buffer and return.
            m_handler.accept(buffer);
            return HttpServerResponse.HTTP_CONTINUE;
        }

        while (buffer.hasRemaining())
        {
            byte current = buffer.get();
            if (!m_headersDone)
            {
                m_headerBuilder.write(current & 0xFF);

                // was the last char a \r and this one is the \n - i.e.,
                // we are at the ned of a line?
                if (m_r && (current & 0xFF) == '\n')
                {
                    // if so, is this an empty line?
                    if (m_e)
                    {
                        m_headersDone = true;
                        try
                        {
                            for (String line : new String(m_headerBuilder.toByteArray(), "UTF-8").split("\r\n"))
                            {
                                if (!line.isEmpty())
                                {
                                    m_header.add(line);
                                }
                            }
                            parseHeader();
                        }
                        catch (UnsupportedEncodingException ex)
                        {
                            return HttpServerResponse.HTTP_BAD_REQUEST;
                        }
                    }
                    m_e = true;
                    m_r = false;
                }
                else
                {
                    m_r = (current & 0xFF) == '\r';
                    if (!m_r)
                    {
                        m_e = false;
                    }
                }
            }
            else if (m_contentLength > 0)
            {
                // The header is done but we have more data and expect a body - hence, buffer it.
                m_rest.write(current);
                byte[] tmp = new byte[buffer.remaining() + 1 > m_contentLength ? (int) m_contentLength : buffer.remaining()];
                buffer.get(tmp);
                try
                {
                    m_rest.write(tmp);
                }
                catch (IOException ex)
                {
                    Logger.getLogger(HttpRequestImpl.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else
            {
                return HttpServerResponse.HTTP_BAD_REQUEST;
            }
            m_current++;
        }

        if (m_headersDone)
        {
            if (validate())
            {
                m_send100 = send100();
                if (m_rest.size() > 0)
                {
                    m_body = new ByteArrayInputStream(m_rest.toByteArray());
                }
                return HttpServerResponse.HTTP_OK;
            }
            else
            {
                return HttpServerResponse.HTTP_BAD_REQUEST;
            }
        }
        else if (m_current < buffer.capacity())
        {
            return null;
        }
        else
        {
           // we did read more bytes than would fit in a rcv buffer so we better stop.
           return HttpServerResponse.HTTP_ENTITY_TOO_LARGE;
        }
    }

    // is this a valid http request?
    private boolean validate()
    {
        return
            !((m_method == null || m_method.isEmpty())
            ||
            (m_version == null || !(m_version.equals("HTTP/1.0") || m_version.equals("HTTP/1.1")))
            ||
            (m_path == null || m_path.isEmpty())
            ||
            m_version.equals("HTTP/1.1") && getHeader("Host") == null
            ||
            m_contentLength < 0);
    }

    // are we asked ot send a 100-continue to get the body data?
    private boolean send100()
    {
        return
            m_rest.size() < m_contentLength
            &&
            m_rest.size() == 0
            && "HTTP/1.1".equals(m_version)
            && "100-continue".equals(getHeader("Expect"));
    }

    private void parseHeader()
    {
        parseRequest();

        parseHeaderMap();

        parseContentLength();
    }

    private void parseRequest()
    {
        String request = m_header.get(0);

        StringTokenizer tok = new StringTokenizer(request, " ");

        if (tok.countTokens() != 3)
        {
            return;
        }

        m_method = tok.nextToken().trim().toUpperCase();

        m_uri = tok.nextToken().trim();

        int idx = m_uri.indexOf("://");
        if (idx != -1)
        {
            idx = m_uri.indexOf('/', idx);
            if (idx == -1)
            {
                m_uri = "/";
            }
            else
            {
                m_uri = m_uri.substring(idx);
            }
        }
        if (!m_uri.startsWith("/"))
        {
            m_uri = "/" + m_uri;
        }

        try
        {
            m_path = new URL("http://127.0.0.1" + m_uri).toURI().getPath();
        }
        catch (MalformedURLException | URISyntaxException ex)
        {
            Logger.getLogger(HttpRequestImpl.class.getName()).log(Level.WARNING, ex.getMessage(), ex);
        }

        m_version = tok.nextToken().trim().toUpperCase();
    }

    private void parseHeaderMap()
    {
        m_header.subList(1, m_header.size()).stream().forEach(
            (header) ->
                {
                    int idx = header.indexOf(':');
                    if (idx != -1 && idx + 1 < header.length())
                    {
                        String key = header.substring(0, idx).trim();
                        String value = header.substring(idx + 1).trim();
                        if (!key.isEmpty())
                        {
                            m_headerMap.put(key, value);
                            m_lowerCaseHeaderMap.put(key.toLowerCase(), value);
                        }
                    }
                });
    }

    private void parseContentLength()
    {
        String contentLenghtHeader = getHeader("Content-Length");

        if (contentLenghtHeader != null)
        {
            try
            {
                m_contentLength = Long.parseLong(contentLenghtHeader);
            }
            catch (NumberFormatException ex)
            {
                m_contentLength = -1;
            }
        }
    }

    @Override
    public Map<String,String> headers()
    {
        return Collections.unmodifiableMap(m_headerMap);
    }

    @Override
    public String getHeader(String header)
    {
        return m_lowerCaseHeaderMap.get(header.toLowerCase());
    }

    @Override
    public String method()
    {
        return m_method;
    }

    @Override
    public String uri()
    {
        return m_uri;
    }

    @Override
    public String version()
    {
        return m_version;
    }

    @Override
    public String path()
    {
        return m_path;
    }

    @Override
    public <A> void read(ByteBuffer buffer, A attachment, CompletionHandler<Integer, ? super A> handler)
    {
        // we first have to write out any buffered data.
        if (!buffer.hasRemaining())
        {
            handler.completed(0, attachment);
        }
        else if (m_body != null && m_body.available() > 0)
        {
            byte[] tmp = new byte[buffer.remaining() > m_body.available() ? m_body.available() : buffer.remaining()];

            int count = -1;
            try
            {
                count = m_body.read(tmp);
            }
            catch (IOException ex) {
                // Ignore
            }

            buffer.put(tmp);

            m_sendCount += count;

            if (m_body.available() == 0)
            {
                m_body = null;
            }

            handler.completed(count, attachment);
        }
        else if (m_sendCount < m_contentLength)
        {
            // and next, need to request more - hence, set-up a handler
            m_handler = (readBuffer) -> {
                // we got more data so hand it to the requester
                ByteBuffer slice = readBuffer.slice();
                if (buffer.remaining() < slice.remaining())
                {
                    slice.limit(buffer.remaining());
                }
                int count = slice.limit();
                buffer.put(slice);
                m_sendCount += count;

                readBuffer.position(count);

                // did we get more data than we can hand to the request? if so, buffer
                if (readBuffer.hasRemaining() && m_sendCount < m_contentLength)
                {
                    byte[] tmp = new byte[readBuffer.remaining() + m_sendCount > m_contentLength ? (int) m_contentLength : readBuffer.remaining()];
                    readBuffer.get(tmp);
                    m_body = new ByteArrayInputStream(tmp);
                }
                else
                {
                    m_body = null;
                }
                m_handler = null;
                handler.completed(count, attachment);
            };
            // if we need to send a 100-continue to get more do it now.
            if (m_send100)
            {
                m_send100 = false;

                m_context.send100();
            }
            else
            {
                // else receive more data.
                m_context.receive();
            }
        }
        else
        {
            handler.completed(-1, attachment);
        }
    }
}
