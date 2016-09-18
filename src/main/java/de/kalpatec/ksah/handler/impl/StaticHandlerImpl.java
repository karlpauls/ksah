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
package de.kalpatec.ksah.handler.impl;

import de.kalpatec.ksah.HttpContext;
import de.kalpatec.ksah.HttpServerResponse;
import de.kalpatec.ksah.handler.StaticHandler;
import de.kalpatec.ksah.util.ByteBufferCache;
import de.kalpatec.ksah.util.HttpUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This handler does the actual work of handling file requests.
 *
 * It is not completly non blocking alas. Some simple file operations are done
 * via blocking calls.
 *
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
public class StaticHandlerImpl implements StaticHandler
{
    private final ByteBufferCache m_bufferCache;

    private final Path m_root;

    private final String m_charset;

    private final boolean m_list;

    private final boolean m_write;

    public StaticHandlerImpl(Path root, ByteBufferCache bufferCache, String charset, boolean list, boolean write)
    {
        if (!Files.isDirectory(root))
        {
            throw new IllegalArgumentException("Root dir not found");
        }
        m_root = root.toAbsolutePath().normalize();
        m_bufferCache = bufferCache;
        m_charset = charset;
        m_list = list;
        m_write = write;
    }

    @Override
    public void handle(HttpContext context)
    {
        Path path;
        try
        {
            path = FileSystems.getDefault().getPath(m_root + context.request().path()).toAbsolutePath().normalize();
        }
        catch(InvalidPathException ex)
        {
            Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.WARNING, ex.getMessage(), ex);
            HttpUtils.sendNotFound(context.response(), HttpUtils.IGNORE);
            return;
        }

        if (!path.startsWith(m_root))
        {
            HttpUtils.sendForbidden(context.response(), HttpUtils.IGNORE);
        }
        else if (context.request().path().endsWith("/") && !Files.isDirectory(path))
        {
            HttpUtils.sendNotFound(context.response(), HttpUtils.IGNORE);
        }
        else
        {
            switch (context.request().method())
            {
                case "HEAD":
                    doHead(path, context);
                    break;
                case "GET":
                    doGet(path, context);
                    break;
                case "OPTIONS":
                    doOptions(path, context);
                    break;
                case "PUT":
                    if (m_write)
                    {
                        doPut(path, context);
                    }
                    else
                    {
                        HttpUtils.sendNotImplemented(context.response(), HttpUtils.IGNORE);
                    }
                    break;
                case "DELETE":
                    if (m_write)
                    {
                        doDelete(path, context);
                    }
                    else
                    {
                        HttpUtils.sendNotImplemented(context.response(), HttpUtils.IGNORE);
                    }
                    break;
                case "LINK":
                    doLink(path, context);
                    break;
                case "UNLINK":
                    doUnlink(path, context);
                case "TRACE":
                    doTrace(path, context);
                    break;
                case "POST":
                    doPost(path, context);
                    break;
                default:
                    doOther(path, context);
                    break;
            }
        }
    }

    protected void doHead(Path path, HttpContext context)
    {
        doGetOrHead(true, path, context);
    }

    protected void doGet(Path path, HttpContext context)
    {
        doGetOrHead(false, path, context);
    }

    private void doGetOrHead(boolean head, Path path, HttpContext context)
    {
        if (!Files.exists(path))
        {
            HttpUtils.sendNotFound(context.response(), HttpUtils.IGNORE);
        }
        else
        {
            if (Files.isDirectory(path))
            {
                Path index = FileSystems.getDefault().getPath(path.toString() + "/index.html");
                if (Files.exists(index))
                {
                    path = index.toAbsolutePath();
                }
            }

            if (Files.isDirectory(path))
            {
                if (!m_list)
                {
                    HttpUtils.sendForbidden(context.response(), HttpUtils.IGNORE);
                }
                else if (!context.request().path().endsWith("/"))
                {
                    HttpUtils.sendMoved(
                        context.request().uri() + "/",
                        head, context.response(), HttpUtils.IGNORE);
                }
                else
                {
                    sendDir(context, path, head);
                }
            }
            else
            {
                sendFile(context, path, head);
            }
        }
    }

    protected void doOptions(Path path, HttpContext context)
    {
        if (!Files.exists(path))
        {
            if (!Files.exists(path.getParent()))
            {
                HttpUtils.sendNotFound(context.response(), HttpUtils.IGNORE);
            }
            else
            {
                context.response().setHeader("Allow", "PUT, OPTIONS").end();
            }
        }
        else
        {
            if (Files.isDirectory(path))
            {
                Path index = FileSystems.getDefault().getPath(path.toString() + "/index.html");
                if (Files.exists(index))
                {
                    path = index.toAbsolutePath();
                }
            }

            if (Files.isDirectory(path))
            {
                if (!m_list)
                {
                    HttpUtils.sendForbidden(context.response(), HttpUtils.IGNORE);
                }
                else if (!context.request().path().endsWith("/"))
                {
                    HttpUtils.sendMoved(
                        context.request().uri() + "/",
                        false, context.response(), HttpUtils.IGNORE);
                }
                else
                {
                    context.response().setHeader("Allow", "GET, HEAD, OPTIONS").end();
                }
            }
            else
            {
                context.response().setHeader("Allow", "GET, HEAD, PUT, DELETE, OPTIONS").end();
            }
        }
    }

    protected void doPut(Path path, HttpContext context)
    {
        if (!Files.isDirectory(path.getParent()) || Files.isDirectory(path) || path.toString().endsWith("/"))
        {
            HttpUtils.sendForbidden(context.response(), HttpUtils.IGNORE);
        }
        else if (isUnsupportedContent(context))
        {
            HttpUtils.sendNotImplemented(context.response(), HttpUtils.IGNORE);
        }
        else
        {
            try
            {
                long size = Long.parseLong(context.request().getHeader("Content-Length"));

                context.response()
                    .setStatus(Files.exists(path) ? HttpServerResponse.HTTP_NO_CONTENT : HttpServerResponse.HTTP_CREATED)
                    .setHeader("Content-Length", Long.toString(0L));

                // This should really be writting to a tmp file first and only move the result if the
                // request succeeds. Like this, a failed request will corrupt the file.
                AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

                if (size > 0)
                {
                    ByteBuffer readBuffer = m_bufferCache.checkout();

                    context.request().read(readBuffer, 0L, new CompletionHandler<Integer, Long>(){
                        @Override
                        public void completed(Integer result, Long attachment)
                        {
                            if (result < 0)
                            {
                                failed(new IOException("Unexpect end of stream"), attachment);
                            }
                            else if (result > 0)
                            {
                                readBuffer.flip();
                                channel.write(readBuffer, attachment, this, new CompletionHandler<Integer, CompletionHandler<Integer, Long>>(){
                                    volatile long sendCount = 0;
                                    @Override
                                    public void completed(Integer count, CompletionHandler<Integer, Long> handler)
                                    {
                                        sendCount += count;
                                        if (result - count > 0)
                                        {
                                            channel.write(readBuffer, attachment + sendCount, handler, this);
                                        }
                                        else if (attachment + sendCount == size)
                                        {
                                            m_bufferCache.checkin(readBuffer);

                                            context.response().end();

                                            try
                                            {
                                                channel.close();
                                            }
                                            catch (IOException ex)
                                            {
                                                Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.FINE, ex.getMessage(), ex);
                                            }
                                        }
                                        else
                                        {
                                            readBuffer.clear();
                                            context.request().read(readBuffer, attachment + sendCount, handler);
                                        }
                                    }

                                    @Override
                                    public void failed(Throwable exc, CompletionHandler<Integer, Long> handler)
                                    {
                                        handler.failed(exc, attachment + result);
                                    }
                                });
                            }
                            else
                            {
                                context.request().read(readBuffer, attachment, this);
                            }
                        }

                        @Override
                        public void failed(Throwable exc, Long attachment)
                        {
                            Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.WARNING, exc.getMessage(), exc);

                            m_bufferCache.checkin(readBuffer);

                            context.response().end();

                            try
                            {
                                channel.close();
                            }
                            catch (IOException ex)
                            {
                                Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.FINE, ex.getMessage(), ex);
                            }
                        }
                    });
                }
            }
            catch (IOException ex)
            {
                Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);

                HttpUtils.sendInternalServerError(context.response(), HttpUtils.IGNORE);
            }
        }
    }

    protected void doDelete(Path path, HttpContext context)
    {
        if (Files.isDirectory(path))
        {
            HttpUtils.sendForbidden(context.response(), HttpUtils.IGNORE);
        }
        else if (!Files.exists(path))
        {
            HttpUtils.sendNotFound(context.response(), HttpUtils.IGNORE);
        }
        else
        {
            try
            {
                Files.delete(path);
                context.response().setStatus(HttpServerResponse.HTTP_NO_CONTENT)
                    .setHeader("Content-Length", Long.toString(0L)).end();
            }
            catch (Exception ex)
            {
                Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);

                HttpUtils.sendInternalServerError(context.response(), HttpUtils.IGNORE);
            }
        }
    }

    protected void doPost(Path path, HttpContext context)
    {
        HttpUtils.sendNotImplemented(context.response(), HttpUtils.IGNORE);
    }

    protected void doTrace(Path path, HttpContext context)
    {
        HttpUtils.sendNotImplemented(context.response(), HttpUtils.IGNORE);
    }

    protected void doLink(Path path, HttpContext context)
    {
        HttpUtils.sendNotImplemented(context.response(), HttpUtils.IGNORE);
    }

    protected void doUnlink(Path path, HttpContext context)
    {
        HttpUtils.sendNotImplemented(context.response(), HttpUtils.IGNORE);
    }

    protected void doOther(Path path, HttpContext context)
    {
        HttpUtils.sendNotImplemented(context.response(), HttpUtils.IGNORE);
    }

    private void sendDir(HttpContext context, Path path, boolean head)
    {
        try
        {
            String content = HttpUtils.listDir(path, m_root);
            ByteBuffer buffer = ByteBuffer.wrap(content.getBytes("UTF-8"));

            context.response()
                .setHeader("Content-Type", "text/html; charset=UTF-8")
                .setHeader("Content-Length", Integer.toString(buffer.limit()));

            if (!head)
            {
                context.response().write(buffer, null,
                    new CompletionHandler<Void, Void>(){
                        @Override
                        public void completed(Void result, Void attachment)
                        {
                            context.response().end();
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment)
                        {
                            Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.WARNING, exc.getMessage(), exc);
                            context.response().end();
                        }
                });
            }
            else
            {
                context.response().end();
            }
        }
        catch (IOException ex)
        {
            Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.WARNING, ex.getMessage(), ex);

            HttpUtils.sendInternalServerError(context.response(), HttpUtils.IGNORE);
        }

    }

    private void sendFile(HttpContext context, Path path, boolean head)
    {
        try
        {
            AsynchronousFileChannel channel = AsynchronousFileChannel.open(path,StandardOpenOption.READ);

            try
            {
                long size = channel.size();

                context.response()
                    .setStatus(HttpServerResponse.HTTP_OK)
                    .setHeader("Content-Type", HttpUtils.getContentType(path.getFileName().toString(), m_charset))
                    .setHeader("Content-Length", Long.toString(size))
                    .setHeader("Last-Modified", HttpUtils.formatDate(new Date(Files.getLastModifiedTime(path).toMillis())));

                if (head || size == 0)
                {
                    try
                    {
                        channel.close();
                    }
                    catch (IOException ex)
                    {
                        Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.FINE, ex.getMessage(), ex);
                    }
                    context.response().end();
                }
                else
                {
                    ByteBuffer readBuffer = m_bufferCache.checkout();

                    channel.read(readBuffer, 0, 0L, new CompletionHandler<Integer, Long>(){

                        @Override
                            public void completed(Integer result, Long attachment)
                            {
                                if (result < 0)
                                {
                                    failed(new IOException("Unexpect end of stream"), attachment);
                                }
                                else if (result > 0)
                                {
                                    readBuffer.flip();

                                    context.response().write(readBuffer, this, new CompletionHandler<Void, CompletionHandler<Integer, Long>>()
                                    {
                                        @Override
                                        public void completed(Void v, CompletionHandler<Integer,Long> handler)
                                        {
                                           if (attachment + result == size)
                                           {
                                                m_bufferCache.checkin(readBuffer);

                                                context.response().end();

                                                try
                                                {
                                                    channel.close();
                                                }
                                                catch (IOException ex)
                                                {
                                                    Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.FINE, ex.getMessage(), ex);
                                                }
                                            }
                                            else
                                            {
                                                readBuffer.clear();

                                                channel.read(readBuffer, attachment + result, attachment + result, handler);
                                            }
                                        }

                                        @Override
                                        public void failed(Throwable exc, CompletionHandler<Integer,Long> handler)
                                        {
                                            handler.failed(exc, attachment + result);
                                        }
                                    });
                                }
                                else
                                {
                                    channel.read(readBuffer, attachment + result, attachment + result, this);
                                }
                            }

                            @Override
                            public void failed(Throwable exc, Long attachment)
                            {
                                Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.WARNING, exc.getMessage(), exc);

                                m_bufferCache.checkin(readBuffer);

                                try
                                {
                                    channel.close();
                                }
                                catch (IOException ex)
                                {
                                    Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.FINE, ex.getMessage(), ex);
                                }

                                HttpUtils.sendInternalServerError(context.response(), HttpUtils.IGNORE);
                            }
                        });
                }
            }
            catch (Exception ex)
            {
                if (channel != null)
                {
                    try
                    {
                        channel.close();
                    }
                    catch (IOException ignore)
                    {
                        Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.FINE, ignore.getMessage(), ignore);
                    }
                }
                throw new IOException(ex);
            }
        }
        catch (IOException ex)
        {
            Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.WARNING, ex.getMessage(), ex);

            HttpUtils.sendInternalServerError(context.response(), HttpUtils.IGNORE);
        }
    }

    // We don't support most content-* headers which is required for PUT so fail if they are there
    // Plus we don't do any encoding or chunked stuff - hence fail in that case as well.
    private boolean isUnsupportedContent(HttpContext context)
    {
        return context.request().headers().keySet().stream().anyMatch(
                header -> (!header.toLowerCase().equals("content-length")
                    && (!header.toLowerCase().equals("content-type"))
                    && header.toLowerCase().startsWith("content-")
                    && (!header.toLowerCase().equals("content-encoding") || !"identity".equalsIgnoreCase(context.request().getHeader("Content-Encoding")))
                    )
                    ||
                    (header.toLowerCase().equals("transfer-encoding")
                        && !"identity".equalsIgnoreCase(context.request().getHeader("Transfer-Encoding")))
            );
    }
}
