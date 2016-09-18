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
package de.kalpatec.ksah.tests;

import de.kalpatec.ksah.HttpServer;
import de.kalpatec.ksah.handler.StaticHandler;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
public class AsyncHttpTests
{
    private volatile InetSocketAddress m_address;
    private volatile File m_www;
    private volatile HttpServer m_server;

    @Before
    public void setUP() throws IOException
    {
        try (Socket s = new Socket())
        {
            s.bind(new InetSocketAddress(0));
            m_address = (InetSocketAddress) s.getLocalSocketAddress();
        }
        m_www = File.createTempFile("www", null);
        m_www.delete();
        m_www.mkdirs();
        m_server = HttpServer.create(m_address,HttpServer.options().timeout(10, TimeUnit.SECONDS),
            StaticHandler.create(
                StaticHandler.options()
                .root(m_www.toPath()).list(false).write(true)
            )::handle);
    }

    @After
    public void tearDown() throws InterruptedException
    {
        m_server.close();
        boolean closed = m_server.awaitClose(2, TimeUnit.SECONDS);
        delete(m_www);
        if (!closed)
        {
            throw new IllegalStateException("Server not shutdown");
        }
    }

    private void delete(File file)
    {
        if (file.isDirectory())
        {
            for (File child : file.listFiles())
            {
                delete(child);
            }
        }
        else
        {
            file.delete();
        }
    }

    @Test
    public void testServerHeader() throws Exception
    {
        withConnectionTo("",
            con ->
            {
                Assert.assertNotNull(con.getHeaderField("Server"));
            }
        );

    }

    @Test
    public void testFileNotFound() throws Exception
    {
        withConnectionTo("/foo/bar/baz.html",
            con ->
            {
                Assert.assertEquals(404, con.getResponseCode());
                Assert.assertEquals("File Not Found", con.getResponseMessage());
            }
        );
    }

    @Test
    public void testIndexNotFound() throws Exception
    {
        new File(m_www, "dir1").mkdir();

        withConnectionTo("/dir1/",
            con ->
            {
                Assert.assertEquals(403, con.getResponseCode());
            }
        );
    }

    @Test
    public void testIndex() throws Exception
    {
        File dir1 = new File(m_www, "dir1");
        dir1.mkdir();
        File index = new File(dir1, "index.html");

        try (FileWriter writer = new FileWriter(index))
        {
            writer.write("<html><body>Test Index</body></html>");
        }

        withConnectionTo("/dir1/",
            con ->
            {
                Assert.assertEquals(200, con.getResponseCode());
                Assert.assertEquals("<html><body>Test Index</body></html>".getBytes().length, con.getContentLengthLong());
                Assert.assertEquals("<html><body>Test Index</body></html>", read(con));
            }
        );
    }

    @Test
    public void testNested() throws Exception
    {
        File dir1 = new File(m_www, "dir1/dir2/dir3/dir4/dir5/");
        dir1.mkdirs();

        try (FileWriter writer = new FileWriter(new File(dir1, "test.html")))
        {
            writer.write("<html><body>Test Nesteds</body></html>");
        }

        withConnectionTo("dir1/dir2/dir3/dir4/dir5/test.html",
            con ->
            {
                Assert.assertEquals(200, con.getResponseCode());
                Assert.assertEquals("<html><body>Test Nesteds</body></html>".getBytes().length, con.getContentLengthLong());
                Assert.assertEquals("<html><body>Test Nesteds</body></html>", read(con));
            }
        );
    }

    @Test
    public void testWithQuery() throws Exception
    {
        File dir1 = new File(m_www, "dir1/dir2/dir3/dir4/dir5/");
        dir1.mkdirs();

        try (FileWriter writer = new FileWriter(new File(dir1, "test.html")))
        {
            writer.write("<html><body>Test with Query</body></html>");
        }

        withConnectionTo("dir1/dir2/dir3/dir4/dir5/test.html?foo=bar&baz=foobar",
            con ->
            {
                Assert.assertEquals(200, con.getResponseCode());
                Assert.assertEquals("<html><body>Test with Query</body></html>".getBytes().length, con.getContentLengthLong());
                Assert.assertEquals("<html><body>Test with Query</body></html>", read(con));
            }
        );
    }

    @Test
    public void testWithSlash() throws Exception
    {
        File dir1 = new File(m_www, "dir1/dir2/dir3/dir4/dir5/");
        dir1.mkdirs();

        try (FileWriter writer = new FileWriter(new File(dir1, "test.html")))
        {
            writer.write("<html><body>Test With slash</body></html>");
        }

        withConnectionTo("dir1/dir2/dir3/dir4/dir5/test.html/",
            con ->
            {
                Assert.assertEquals(404, con.getResponseCode());
            }
        );
    }

    @Test
    public void testWithSpaces() throws Exception
    {
        File dir1 = new File(m_www, "dir1/dir  2/dir3/di  r4/di   r5/");
        dir1.mkdirs();

        try (FileWriter writer = new FileWriter(new File(dir1, "te   st.html")))
        {
            writer.write("<html><body>Test with spaces</body></html>");
        }

        withConnectionTo("dir1/dir  2/dir3/di  r4/di   r5/te   st.html",
            con ->
            {
                Assert.assertEquals(200, con.getResponseCode());
                Assert.assertEquals("<html><body>Test with spaces</body></html>".getBytes().length, con.getContentLengthLong());
                Assert.assertEquals("<html><body>Test with spaces</body></html>", read(con));
            }
        );
    }

    @Test
    public void testWithEncodes() throws Exception
    {
        File dir1 = new File(m_www, "foo/bar baz/ääüßß<<<×××");
        dir1.mkdirs();

        try (FileWriter writer = new FileWriter(new File(dir1, "te ××  st.html")))
        {
            writer.write("<html><body>Test with encodes</body></html>");
        }

        withConnectionTo("foo/bar baz/ääüßß<<<×××/te ××  st.html",
            con ->
            {
                Assert.assertEquals(200, con.getResponseCode());
                Assert.assertEquals("<html><body>Test with encodes</body></html>".getBytes().length, con.getContentLengthLong());
                Assert.assertEquals("<html><body>Test with encodes</body></html>", read(con));
            }
        );
    }

    @Test
    public void testPutAndDelete() throws Exception
    {
        byte[] content = new byte[1024 * 42];
        new Random().nextBytes(content);
        withConnectionTo("test.txt",
            con -> {
                con.setDoOutput(true);
                con.setRequestMethod("PUT");
                con.setRequestProperty("Content-Length", Integer.toString(content.length));
                try(OutputStream output = con.getOutputStream())
                {
                    output.write(content);
                }
                Assert.assertEquals(201, con.getResponseCode());
                withConnectionTo("test.txt",
                    con2 -> {
                        Assert.assertEquals(200, con2.getResponseCode());
                        Assert.assertEquals(content.length, con2.getContentLengthLong());
                        Assert.assertArrayEquals(content, readBytes(con2));
                        Assert.assertTrue("File created", new File(m_www, "test.txt").isFile());
                        withConnectionTo("test.txt", con3 -> {
                            con3.setRequestMethod("DELETE");
                            Assert.assertEquals(204, con3.getResponseCode());
                            withConnectionTo("test.txt", con4 -> {
                                Assert.assertEquals(404, con4.getResponseCode());
                                Assert.assertFalse("File not deleted", new File(m_www, "test.txt").isFile());
                            });
                        });
                    }
                );
            }
        );
    }

    @Test
    public void testPutWithExisting() throws Exception
    {
        new File(m_www, "foo").mkdir();
        byte[] content = new byte[1024 * 42];
        new Random().nextBytes(content);
        withConnectionTo("foo/test.txt",
            con -> {
                con.setDoOutput(true);
                con.setRequestMethod("PUT");
                con.setRequestProperty("Content-Length", Integer.toString(content.length));
                try(OutputStream output = con.getOutputStream())
                {
                    output.write(content);
                }
                Assert.assertEquals(201, con.getResponseCode());

                withConnectionTo("foo/test.txt",
                    con2 -> {
                        Assert.assertEquals(200, con2.getResponseCode());
                        Assert.assertEquals(content.length, con2.getContentLengthLong());
                        Assert.assertArrayEquals(content, readBytes(con2));
                        Assert.assertTrue("File created", new File(m_www, "foo/test.txt").isFile());
                        new Random().nextBytes(content);
                        withConnectionTo("foo/test.txt", con3 -> {
                            con3.setDoOutput(true);
                            con3.setRequestMethod("PUT");
                            con3.setRequestProperty("Content-Length", Integer.toString(content.length));
                            try(OutputStream output = con3.getOutputStream())
                            {
                                output.write(content);
                            }
                            Assert.assertEquals(204, con3.getResponseCode());
                            withConnectionTo("foo/test.txt", con4 -> {
                                Assert.assertEquals(200, con4.getResponseCode());
                                Assert.assertEquals(content.length, con4.getContentLengthLong());
                                Assert.assertArrayEquals(content, readBytes(con4));
                                Assert.assertTrue("File created", new File(m_www, "foo/test.txt").isFile());
                            });
                        });
                    }
                );
            }
        );
    }

    @Test
    public void testPutWithContentHeader() throws Exception
    {
        byte[] content = new byte[1024];
        new Random().nextBytes(content);
        withConnectionTo("test.txt",
            con -> {
                con.setDoOutput(true);
                con.setRequestMethod("PUT");
                con.setRequestProperty("Content-Length", Integer.toString(content.length));
                con.setRequestProperty("Content-Encoding", "gzip");
                try(OutputStream output = con.getOutputStream())
                {
                    output.write(content);
                }
                Assert.assertEquals(405, con.getResponseCode());
            }
        );

        withConnectionTo("test.txt",
            con -> {
                con.setDoOutput(true);
                con.setRequestMethod("PUT");
                con.setRequestProperty("Content-Length", Integer.toString(content.length));
                con.setRequestProperty("Content-Encoding", "identity");
                try(OutputStream output = con.getOutputStream())
                {
                    output.write(content);
                }
                Assert.assertEquals(201, con.getResponseCode());
            }
        );
    }

    @Test
    public void testNotImplemented() throws Exception
    {
        new File(m_www, "test.txt").createNewFile();
        withConnectionTo("test.txt",
            con ->
            {
                con.setDoOutput(true);
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Length", Integer.toString(0));
                con.getOutputStream().close();
                Assert.assertEquals(405, con.getResponseCode());
            });
    }

    @Test
    public void testOptions() throws Exception
    {
        new File(m_www, "test.txt").createNewFile();
        withConnectionTo("test.txt",
            con ->
            {
                con.setDoOutput(true);
                con.setRequestMethod("OPTIONS");
                con.setRequestProperty("Content-Length", Integer.toString(0));
                con.getOutputStream().close();
                Assert.assertEquals(200, con.getResponseCode());
                Assert.assertEquals("GET, HEAD, PUT, DELETE, OPTIONS", con.getHeaderField("Allow"));
            });
    }

    @Test
    public void testPutWithTransferEncodingFail() throws Exception
    {
        byte[] content = new byte[1024];
        new Random().nextBytes(content);
        withConnectionTo("test.txt",
            con -> {
                con.setDoOutput(true);
                con.setRequestMethod("PUT");
                con.setChunkedStreamingMode(0);
                try(OutputStream output = con.getOutputStream())
                {
                    output.write(content);
                }
                Assert.assertEquals(405, con.getResponseCode());
            }
        );
    }

        @Test
    public void testListAndNoWrite() throws Exception
    {
        InetSocketAddress address;
        try (Socket s = new Socket())
        {
            s.bind(new InetSocketAddress(0));
            address = (InetSocketAddress) s.getLocalSocketAddress();
        }
        File www = File.createTempFile("www", null);
        www.delete();
        www.mkdirs();
        HttpServer server = HttpServer.create(address,HttpServer.options().timeout(10, TimeUnit.SECONDS),
            StaticHandler.create(
                StaticHandler.options()
                .root(www.toPath()).list(true).write(false)
            )::handle);
        try
        {
            File dir = new File(www, "foo/bar baz/blub");
            dir.mkdirs();
            new File(www, "foo/bar baz/blub/test.html").createNewFile();
            withConnectionTo("foo/bar baz/blub/", con -> {
                //con.setInstanceFollowRedirects(true);
                Assert.assertEquals(200, con.getResponseCode());
                Assert.assertEquals("<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "    <head>\n" +
                    "        <title>blub</title>\n" +
                    "        <meta charset=\"UTF-8\">\n" +
                    "        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "    </head>\n" +
                    "    <body>\n" +
                    "        <h1>blub</h1>\n" +
                    "        <ul>\n" +
                    "            <li><a href=\"..\">..</a></li>\n" +
                    "            <li><a href=\"test.html\">test.html</a></li>\n" +
                    "        </ul>\n" +
                    "    </body>\n" +
                    "</html>", read(con));
            }, address);

            withConnectionTo("foo/bar baz/blub", con -> {
                Assert.assertEquals(200, con.getResponseCode());
                Assert.assertEquals("<!DOCTYPE html>\n" +
                "<html>\n" +
                "    <head>\n" +
                "        <title>blub</title>\n" +
                "        <meta charset=\"UTF-8\">\n" +
                "        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    </head>\n" +
                "    <body>\n" +
                "        <h1>blub</h1>\n" +
                "        <ul>\n" +
                "            <li><a href=\"..\">..</a></li>\n" +
                "            <li><a href=\"test.html\">test.html</a></li>\n" +
                "        </ul>\n" +
                "    </body>\n" +
                "</html>", read(con));
            }, address);
        }
        finally
        {
            server.close();
            boolean closed = server.awaitClose(2, TimeUnit.SECONDS);
            delete(www);
            if (!closed)
            {
                throw new IllegalStateException("Server not shutdown");
            }
        }
    }

    private void withConnectionTo(String path, TestConsumer<HttpURLConnection> handler, InetSocketAddress... optionalAddress) throws Exception
    {
        if (!path.isEmpty() && !path.startsWith("/"))
        {
            path = "/" + path;
        }
        InetSocketAddress address = optionalAddress.length == 0 ? m_address : optionalAddress[0];
        HttpURLConnection con = (HttpURLConnection)
            new URL(new URI("http",null, address.getHostString(), address.getPort(), path.contains("?") ? path.substring(0, path.indexOf('?')) : path, path.contains("?") ? path.substring(path.indexOf('?') + 1) : null, null).toASCIIString()).openConnection();

        try
        {
            handler.accept(con);
        }
        finally
        {
            con.disconnect();
        }
    }

    private byte[] readBytes(HttpURLConnection con) throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = con.getInputStream())
        {
            byte[] buffer = new byte[16 * 1024];
            for (int i = input.read(buffer); i != -1; i = input.read(buffer))
            {
                output.write(buffer, 0 ,i);
            }
        }
        return output.toByteArray();
    }


    private String read(HttpURLConnection con) throws IOException
    {
        StringBuilder builder = new StringBuilder(con.getContentLength());
        try (InputStreamReader reader = new InputStreamReader(con.getInputStream(), "UTF-8"))
        {
            char[] buffer = new char[16 * 1024];
            for (int i = reader.read(buffer);i != -1;i = reader.read(buffer))
            {
                builder.append(buffer, 0, i);
            }
        }
        return builder.toString();
    }

    @FunctionalInterface
    private interface TestConsumer<T>
    {
        public void accept(T t) throws Exception;
    }
}
