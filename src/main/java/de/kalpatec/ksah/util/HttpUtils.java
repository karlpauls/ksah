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
package de.kalpatec.ksah.util;

import de.kalpatec.ksah.HttpServerRequest;
import de.kalpatec.ksah.HttpServerResponse;
import de.kalpatec.ksah.handler.impl.StaticHandlerImpl;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
public class HttpUtils
{
    public static final CompletionHandler<Void,Void> IGNORE = new CompletionHandler<Void, Void>()
    {
        @Override
        public void completed(Void result, Void attachment)
        {
        }

        @Override
        public void failed(Throwable exc, Void attachment)
        {
            Logger.getLogger(HttpUtils.class.getName()).log(Level.FINEST, exc.getMessage(), exc);
        }
    };

    public static void sendNotFound(HttpServerResponse response, CompletionHandler<Void,Void> handler)
    {
        send(HttpServerResponse.HTTP_NOT_FOUND, "<!DOCTYPE html><html><body><h1>File Not Found</h1></body></html>", response, handler);
    }

    public static void sendMoved(String location, boolean head, HttpServerResponse response, CompletionHandler<Void,Void> handler)
    {
        send(HttpServerResponse.HTTP_MOVED_PERMANENTLY, head ? "" : "<!DOCTYPE html><html><body><a href=\"" + location + "\">" + location + "</a></body></html>", response.setHeader("Location", location), handler);
    }

    public static void sendNotImplemented(HttpServerResponse response, CompletionHandler<Void,Void> handler)
    {
        send(HttpServerResponse.HTTP_METHOD_NOT_ALLOWED, "<!DOCTYPE html><html><body><h1>Method Not Allowed</h1></body></html>", response, handler);
    }

    public static void sendInternalServerError(HttpServerResponse response, CompletionHandler<Void,Void> handler)
    {
        send(HttpServerResponse.HTTP_INTERNAL_ERROR, "<!DOCTYPE html><html><body><h1>Internal Server Error</h1></body></html>", response, handler);
    }

    public static void sendForbidden(HttpServerResponse response, CompletionHandler<Void,Void> handler)
    {
        send(HttpServerResponse.HTTP_FORBIDDEN, "<!DOCTYPE html><html><body><h1>Forbidden</h1></body></html>", response, handler);
    }

    public static void sendToLarge(HttpServerResponse response, CompletionHandler<Void,Void> handler)
    {
        send(HttpServerResponse.HTTP_ENTITY_TOO_LARGE,
            "<!DOCTYPE html><html><body><h1>Request Entity Too Large</h1></body></html>", response, handler);
    }

    public static void sendContinue(HttpServerResponse response)
    {
        response.setStatus(HttpServerResponse.HTTP_CONTINUE);
        response.setHeader("Content-Length", "0");

        response.end();
    }

    public static void sendUnavailable(HttpServerResponse response, CompletionHandler<Void, Void> handler)
    {
        send(HttpServerResponse.HTTP_UNAVAILABLE,
            "<!DOCTYPE html><html><body><h1>Service Unavailable</h1></body></html>", response, handler);
    }

    public static void sendBadRequest(HttpServerResponse response, CompletionHandler<Void, Void> handler)
    {
        send(HttpServerResponse.HTTP_BAD_REQUEST, "<!DOCTYPE html><html><body><h1>Bad Request</h1></body></html>", response, handler);
    }

    private static void send(String status, String body, HttpServerResponse response, CompletionHandler<Void, Void> handler)
    {
        byte[] message = null;
        try
        {
            message = body.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(HttpUtils.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            throw new RuntimeException(ex); // Will not happen.
        }

        response.setStatus(status);
        response.setHeader("Content-Type", "text/html; charset=UTF-8");
        response.setHeader("Content-Length", Integer.toString(message.length));

        response.write(ByteBuffer.wrap(message), null, new CompletionHandler<Void, Void>()
            {
                @Override
                public void completed(Void result, Void attachment)
                {
                    response.end();
                    handler.completed(result, attachment);
                }

                @Override
                public void failed(Throwable exc, Void attachment)
                {
                    response.end();
                    handler.failed(exc, attachment);
                }
            });
    }

    public static String getConnectionHeaderValue(HttpServerRequest request)
    {
        if ((
                "HTTP/1.0".equals(request.version())
                &&
                !"keep-alive".equals(request.getHeader("Connection"))
            )
            ||
            "close".equals(request.getHeader("Connection")))
        {
            return "close";
        }
        else
        {
            return "keep-alive";
        }
    }

    public static String formatDate(Date date)
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(date);
    }

    public static String getContentType(String fileName, String charset)
    {
        String contentType;
        if (fileName.endsWith(".html"))
        {
            contentType = "text/html";
        }
        else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg"))
        {
            contentType = "image/jpeg";
        }
        else if (fileName.endsWith(".png"))
        {
            contentType = "image/png";
        }
        else if (fileName.endsWith(".pdf"))
        {
            contentType = "application/pdf";
        }
        else if (fileName.endsWith(".ps"))
        {
            contentType = "application/postscript";
        }
        else if (fileName.endsWith(".css"))
        {
            contentType = "text/css";
        }
        else if (fileName.endsWith(".js"))
        {
            contentType = "application/javascript";
        }
        else if (fileName.endsWith(".gif"))
        {
            contentType = "image/gif";
        }
        else if (fileName.endsWith(".swf"))
        {
            contentType = "application/x-shockwave-flash";
        }
        else if (fileName.endsWith(".txt"))
        {
            contentType = "text/plain";
        }
        else if (fileName.endsWith(".json"))
        {
            contentType = "application/json";
        }
        else if (fileName.endsWith(".xml"))
        {
            contentType = "application/xhtml+xml";
        }
        else if (fileName.endsWith(".properties"))
        {
            contentType = "text/plain";
        }
        else
        {
            contentType = "application/octet-stream";
        }
        if (contentType.equals("application/xhtml+xml") || contentType.equals("application/json") || contentType.startsWith("text/"))
        {
            contentType = contentType + "; charset=" + charset;
        }
        return contentType;
    }

    public static String listDir(Path path, Path rootPath) throws IOException
    {
        boolean root = rootPath.equals(path);
        StringBuilder builder =
            new StringBuilder(
                "<!DOCTYPE html>\n" +
                "<html>\n" +
                "    <head>\n" +
                "        <title>")
                .append(root ? "/" : encodeHTML(path.getFileName().toString()))
                .append("</title>\n" +
                "        <meta charset=\"UTF-8\">\n" +
                "        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    </head>\n" +
                "    <body>\n" +
                "        <h1>")
                .append(root ? "/" : encodeHTML(path.getFileName().toString())).append("</h1>\n");

        builder.append("        <ul>\n"
            + "            <li>");
        if (root)
        {
            addDir("/", builder);
        }
        else
        {
            addDir("..", builder);
        }
        builder.append("</li>");

        Files
            .list(path)
                .map(subpath ->
                    Files.isDirectory(subpath)
                        ?
                        subpath.getFileName().toString() + "/"
                        :
                        subpath.getFileName().toString())
                .sorted().forEach(
                    name ->
                        addDir(name, builder.append("\n            <li>")).append("</li>"));

        builder.append(
            "\n        </ul>\n" +
            "    </body>\n" +
            "</html>"
        );

        return builder.toString();
    }


    private static StringBuilder addDir(String name, StringBuilder builder)
    {
        builder
            .append("<a href=\"")
            .append(encodeURL(name))
            .append("\">")
            .append(encodeHTML(name))
            .append("</a>");

        return builder;
    }

    public static String encodeURL(String name)
    {
        try
        {
            return new URL(new URI("http", "127.0.0.1", "/" + name, null).toASCIIString()).getPath().substring(1);
        }
        catch (MalformedURLException | URISyntaxException ex)
        {
            Logger.getLogger(StaticHandlerImpl.class.getName()).log(Level.WARNING, ex.getMessage(), ex);
            return "";
        }
    }

    public static String encodeHTML(String name)
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);
            if (c > 127 || c == '"' || c == '<' || c == '>' || c == '&')
            {
                builder.append("&#");
                builder.append((int) c);
                builder.append(';');
            }
            else
            {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
