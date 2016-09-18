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
package de.kalpatec.ksah.handler;

import de.kalpatec.ksah.HttpContext;
import de.kalpatec.ksah.handler.impl.StaticHandlerImpl;
import de.kalpatec.ksah.util.ByteBufferCache;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The interface for the file handler
 *
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
public interface StaticHandler
{
    /**
     * Create the handler.
     *
     * with default options.
     *
     * @return the handler.
     */
    public static StaticHandler create()
    {
        return create(options());
    }

    /**
     * Create the handler.
     *
     * with the given options.
     * @param options the options.
     * @return the handler.
     */
    public static StaticHandler create(Options options)
    {
        options = options.copy();
        return new StaticHandlerImpl(options.root(),
            ByteBufferCache.create(options.nativeBufferCacheSize() / options.bufferSize, options.bufferSize()),
            options.charset(),
            options.list(),
            options.write()
        );
    }

    /**
     * The actual request handler method.
     *
     * @param context the context of the request.
     */
    public void handle(HttpContext context);

    /**
     * Get a new options object with defaults.
     *
     * @return the new options.
     */
    public static Options options()
    {
        return new Options();
    }

    /**
     * This are the options for the file handler.
     */
    public static class Options
    {
        private volatile Path root = FileSystems.getDefault().getPath("www").toAbsolutePath();
        private volatile int bufferSize = 64 * 1024;
        private volatile int nativeBufferCacheSize = 16 * 1024 * 1024;
        private volatile String charset = Charset.defaultCharset().name();
        private volatile boolean list = true;
        private volatile boolean write = false;

        /**
         * Clone the options.
         *
         * @return the clone.
         */
        public Options copy()
        {
            return new Options()
                .root(root())
                .nativeBufferCacheSize(nativeBufferCacheSize())
                .bufferSize(bufferSize())
                .charset(charset())
                .list(list())
                .write(write());
        }

        /**
         * Turn on directory listing (default: true).
         *
         * @param value the value.
         * @return this options.
         */
        public Options list(boolean value)
        {
            list = value;
            return this;
        }

        /**
         * @see Options#list(boolean)
         *
         * @return the value;
         */
        public boolean list()
        {
            return list;
        }

        /**
         * Turn on support for PUT and DELETE (default: false).
         *
         * @param value the value.
         *
         * @return this options.
         */
        public Options write(boolean value)
        {
            write = value;
            return this;
        }

        /**
         * @see Options#write(boolean)
         *
         * @return the value.
         */
        public boolean write()
        {
            return write;
        }

        /**
         * Set the charset for text/ (default: Charset.defaultCharset().name())
         *
         * @param value the value.
         * @return this options.
         */
        public Options charset(String value)
        {
            charset =  Charset.forName(charset).name();
            return this;
        }

        /**
         * @see Options#charset(java.lang.String)
         *
         * @return the value.
         */
        public String charset()
        {
            return charset;
        }

        /**
         * Set the DOCUMENT_ROOT (default: www)
         *
         * @param value the value (ignored if not a dir).
         *
         * @return this options.
         */
        public Options root(Path value)
        {
            value = value.toAbsolutePath();
            if (Files.isDirectory(value))
            {
                root = value;
            }
            return this;
        }

        /**
         * @see Options#root(java.nio.file.Path)
         *
         * @return the value.
         */
        public Path root()
        {
            return root;
        }

        /**
         * Set the read buffer size (default: 64K).
         *
         * @param value the value (ignored if not >= 1024).
         *
         * @return this options.
         */
        public Options bufferSize(int value)
        {
            if (value >= 1024)
            {
                bufferSize = value;
            }

            return this;
        }

        /**
         * @see Options#bufferSize(int)
         *
         * @return the value.
         */
        public int bufferSize()
        {
            return bufferSize;
        }

        /**
         * Set the native buffer cache size (default: 16M).
         *
         * @param value the value (if < 1024 -> 0)
         *
         * @return
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
         * @see Options#nativeBufferCacheSize(int)
         *
         * @return the value.
         */
        public int nativeBufferCacheSize()
        {
            return nativeBufferCacheSize;
        }
    }
}
