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
package de.kalpatec.ksah.config;

import de.kalpatec.ksah.HttpServer;
import de.kalpatec.ksah.handler.StaticHandler;
import java.net.InetSocketAddress;
import java.nio.file.FileSystems;
import java.util.Dictionary;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.osgi.framework.BundleContext;

/**
 * Utility class to parse command line and config properties from System/Bundle properties
 * as well as ConfigAdmin.
 *
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
public class ConfigParser
{
    /**
     * @see de.kalpatec.ksah.HttpServer.Options#backlog(int)
     */
    public static final String BACKLOG = "de.kalpatec.ksah.backlog";

    /**
     * @see de.kalpatec.ksah.HttpServer#listen(java.net.InetSocketAddress, java.util.function.Consumer)
     */
    public static final String ADDRESS = "de.kalpatec.ksah.address";

    /**
     * @see de.kalpatec.ksah.HttpServer#listen(java.net.InetSocketAddress, java.util.function.Consumer)
     */
    public static final String PORT = "de.kalpatec.ksah.port";

    /**
     * @see de.kalpatec.ksah.handler.StaticHandler.Options#list(boolean)
     */
    public static final String STATICLIST = "de.kalpatec.ksah.handler.static.list";

    /**
     * @see de.kalpatec.ksah.handler.StaticHandler.Options#write(boolean)
     */
    public static final String STATICWRITE = "de.kalpatec.ksah.handler.static.write";

    /**
     * @see de.kalpatec.ksah.handler.StaticHandler.Options#charset(java.lang.String)
     */
    public static final String STATICCHARSET = "de.kalpatec.ksah.handler.static.charset";

    /**
     * @see de.kalpatec.ksah.handler.StaticHandler.Options#nativeBufferCacheSize(int)
     */
    public static final String STATICBUFFERCACHE = "de.kalpatec.ksah.handler.static.buffercache";

    /**
     * @see de.kalpatec.ksah.handler.StaticHandler.Options#bufferSize(int)
     */
    public static final String STATICBUFFERSIZE = "de.kalpatec.ksah.handler.static.buffersize";

    /**
     * @see de.kalpatec.ksah.handler.StaticHandler.Options#root(java.nio.file.Path)
     */
    public static final String STATICROOT = "de.kalpatec.ksah.handler.static.root";

    /**
     * @see de.kalpatec.ksah.HttpServer.Options#maxConnectionTime(long)
     */
    public static final String MAXCONNECTIONTIME = "de.kalpatec.ksah.maxconnectiontime";

    /**
     * @see de.kalpatec.ksah.HttpServer.Options#maxConnections()
     */
    public static final String MAXCONNNECTIONS = "de.kalpatec.ksah.maxconnnections";

    /**
     * @see de.kalpatec.ksah.HttpServer.Options#timeout(long, java.util.concurrent.TimeUnit)
     */
    public static final String TIMEOUT = "de.kalpatec.ksah.timeout";

    /**
     * @see de.kalpatec.ksah.HttpServer.Options#nativeBufferCacheSize(int)
     */
    public static final String BUFFERCACHE = "de.kalpatec.ksah.buffercache";

    /**
     * @see de.kalpatec.ksah.HttpServer.Options#sndBuf(int)
     */
    public static final String SNDBUF = "de.kalpatec.ksah.sndbuf";

    /**
     * @see de.kalpatec.ksah.HttpServer.Options#rcvBuf(int)
     */
    public static final String RCVBUF = "de.kalpatec.ksah.rcvbuf";

    /**
     * @see de.kalpatec.ksah.HttpServer.Options#reuseAddress(boolean)
     */
    public static final String REUSEADDRESS = "de.kalpatec.ksah.reuseaddress";

    /**
     * @see de.kalpatec.ksah.HttpServer.Options#noDelay(boolean)
     */
    public static final String NODELAY = "de.kalpatec.ksah.nodelay";

    /**
     * @see de.kalpatec.ksah.HttpServer.Options#keepAlive(boolean)
     */
    public static final String KEEPALIVE = "de.kalpatec.ksah.keepalive";

    public static enum Properties
    {
        port(PORT, "\"<number>\" - The port to bind to (mandatory).", null, null),
        address(ADDRESS,"\"<interface>\" - The interface name to bind to (optional, default: \"0.0.0.0\").", null, null),
        backlog(BACKLOG,"\"<number>\" - The backlog size (optional, default: \"1024\").", (value, options) -> options.backlog(Integer.parseInt(value)), null),
        keepalive(KEEPALIVE, "\"<boolean>\" - Set tcp keepalive (optional, default: \"true\").", (value, options) -> options.keepAlive(Boolean.parseBoolean(value)), null),
        nodelay(NODELAY, "\"<boolean>\" - Set tcp nodelay (optional, default: \"true\").", (value, options) -> options.noDelay(Boolean.parseBoolean(value)), null),
        reuseaddress(REUSEADDRESS,"\"<boolean>\" - Set tcp reuseaddress (optional, default: \"true\").", (value, options) -> options.reuseAddress(Boolean.parseBoolean(value)), null),
        rcvbuf(RCVBUF, "\"<number>\" - The size of the receive buffer which is also the max header size (optional, default: \"65536\").", (value, options) -> options.rcvBuf(Integer.parseInt(value)), null),
        sndbuf(SNDBUF, "\"<number>\" - The size of the send buffer (optional, default: \"65536\").", (value, options) -> options.sndBuf(Integer.parseInt(value)), null),
        buffercache(BUFFERCACHE, "\"<number>\" - The total size of _direct_ buffers used for receiving (optional, default: \"16777216\").",(value, options) -> options.nativeBufferCacheSize(Integer.parseInt(value)), null),
        timeout(TIMEOUT, "\"<number>:<unit>\" - The read/write timeout for a socket (optional, default: \"2:SECONDS\").", (value, options) -> options.timeout(Long.parseLong(value.substring(0, value.indexOf(':'))), TimeUnit.valueOf(value.substring(value.indexOf(':' + 1)))), null),
        maxconnnections(MAXCONNNECTIONS,"\"<number>\" - The max number of concurrent connections (optional, default: \"1024\").", (value, options) -> options.maxConnections(Integer.parseInt(value)), null),
        maxconnectiontime(MAXCONNECTIONTIME, "\"<number>\" - The max time a connection is keep-alive in milliseconds, 0 for no keep-alive (optional, default: \"10000\").", (value, options) -> options.maxConnectionTime(Long.parseLong(value)),null),
        root(STATICROOT,"\"<path>\" - The location of the web root (optional, default: \"./www\").", null, (value, options) -> options.root(FileSystems.getDefault().getPath(value))),
        bufferSize(STATICBUFFERSIZE, "\"<number>\" - The size of the read file buffer (optional, default: \"65536\").", null, (value, options) -> options.bufferSize(Integer.parseInt(value))),
        staticbuffercache(STATICBUFFERCACHE,"\"<number>\" - The total size of _direct_ buffers used for reading files (optional, default: \"16777216\").", null, (value, options) -> options.nativeBufferCacheSize(Integer.parseInt(value))),
        charset(STATICCHARSET, "\"<encoding>\" - The charset send for text/ mime files (optional, default: \"<plattform-encoding>\").", null, (value, options) -> options.charset(value)),
        list(STATICLIST, "\"<boolean>\" - Enable directory listings (optional, default: \"true\").", null, (value, options) -> options.list(Boolean.parseBoolean(value))),
        write(STATICWRITE, "\"<boolean>\" - Enable writing via PUT and DELETE (optional, default: \"false\").", null, (value, options) -> options.write(Boolean.parseBoolean(value)))
        ;

        private final String m_system;
        private final String m_desc;
        private final BiConsumer<String, HttpServer.Options> m_httpOptionsHandler;
        private final BiConsumer<String, StaticHandler.Options> m_staticOptionsHandler;

        Properties(String system, String desc, BiConsumer<String, HttpServer.Options> httpHandler, BiConsumer<String, StaticHandler.Options> staticHandler)
        {
            m_system = system;
            m_desc = desc;
            m_httpOptionsHandler = httpHandler;
            m_staticOptionsHandler = staticHandler;
        }

        public String getSystem()
        {
            return m_system;
        }

        public String getDesc()
        {
            return m_desc;
        }

        public boolean isHttp()
        {
            return m_httpOptionsHandler != null;
        }

        public boolean isStatic()
        {
            return m_staticOptionsHandler != null;
        }

        public void apply(String value, HttpServer.Options options)
        {
            m_httpOptionsHandler.accept(value, options);
        }

        public void apply(String value, StaticHandler.Options options)
        {
            m_staticOptionsHandler.accept(value, options);
        }
    }

    public static InetSocketAddress parseProperties(String[] args, Supplier<InetSocketAddress> defaultSupplier)
    {
        return parseProperties(Properties::name, toMap(args)::get, defaultSupplier);
    }

    public static HttpServer.Options parseProperties(String[] args, HttpServer.Options options)
    {
        return parseProperties(Properties::name, toMap(args)::get, options);
    }

    public static StaticHandler.Options parseProperties(String[] args, StaticHandler.Options options)
    {
        return parseProperties(Properties::name, toMap(args)::get, options);
    }

    public static InetSocketAddress parseConfigProperties(Dictionary<String,String> dict, Supplier<InetSocketAddress> defaultSupplier)
    {
        return parseProperties(Properties::getSystem, dict::get, defaultSupplier);
    }

    public static InetSocketAddress parseSystemProperties(Supplier<InetSocketAddress> defaultSupplier)
    {
        return parseProperties(Properties::getSystem, System::getProperty, defaultSupplier);
    }

    public static InetSocketAddress parseBundleProperties(BundleContext context, Supplier<InetSocketAddress> defaultSupplier)
    {
        return parseProperties(Properties::getSystem, context::getProperty, defaultSupplier);
    }

    public static HttpServer.Options parseConfigProperties(Dictionary<String,String> dict, HttpServer.Options options)
    {
        return parseProperties(Properties::getSystem, dict::get, options);
    }

    public static StaticHandler.Options parseConfigProperties(Dictionary<String,String> dict, StaticHandler.Options options)
    {
        return parseProperties(Properties::getSystem, dict::get, options);
    }

    public static HttpServer.Options parseSystemProperties(HttpServer.Options options)
    {
        return parseProperties(Properties::getSystem, System::getProperty, options);
    }

    public static HttpServer.Options parseBundleProperties(BundleContext context, HttpServer.Options options)
    {
        return parseProperties(Properties::getSystem, context::getProperty, options);
    }

    public static StaticHandler.Options parseSystemProperties(StaticHandler.Options options)
    {
        return parseProperties(Properties::getSystem, System::getProperty, options);
    }

    public static StaticHandler.Options parseBundleProperties(BundleContext context, StaticHandler.Options options)
    {
        return parseProperties(Properties::getSystem, context::getProperty, options);
    }

    public static StaticHandler.Options parseConfigProperties(StaticHandler.Options options)
    {
        return parseProperties(Properties::getSystem, System::getProperty, options);
    }

    private static Map<String,String> toMap(String[] args)
    {
        Map<String,String> properties = new HashMap<>();

        if (args.length % 2 == 0)
        {
            for (int i = 0; i < args.length; i+=2)
            {
                properties.put(args[i], args[i+1]);
            }
        }

        return properties;
    }

    private static StaticHandler.Options parseProperties(Function<Properties, String> ref, Function<String,String> lookup, StaticHandler.Options options)
    {
        return parseProperties(ref, lookup, Properties::isStatic, (property, value) -> property.apply(value, options), options);
    }

    private static HttpServer.Options parseProperties(Function<Properties, String> ref, Function<String,String> lookup, HttpServer.Options options)
    {
        return parseProperties(ref, lookup, Properties::isHttp, (property, value) -> property.apply(value, options), options);
    }

    private static InetSocketAddress parseProperties(Function<Properties, String> ref, Function<String,String> lookup, Supplier<InetSocketAddress> defaultSupplier)
    {
        Map<Properties, String> result = new HashMap<>();

        parseProperties(ref, lookup, property -> !property.isStatic() && !property.isHttp(), (property, value) -> result.put(property, value), null);

        if (result.containsKey(Properties.address) && result.containsKey(Properties.port))
        {
            return new InetSocketAddress(result.get(Properties.address), Integer.parseInt(result.get(Properties.port)));
        }
        else if (result.containsKey(Properties.port))
        {
            return new InetSocketAddress(Integer.parseInt(result.get(Properties.port)));
        }
        else
        {
            return defaultSupplier.get();
        }
    }

    private static <T> T parseProperties(Function<Properties, String> ref, Function<String,String> lookup, Predicate<Properties> filter, BiConsumer<Properties, String> handler, T result)
    {
        EnumSet.allOf(Properties.class).stream()
            .filter(filter)
            .forEach(
                property ->
                {
                    String value = lookup.apply(ref.apply(property));

                    if (value != null && !value.isEmpty())
                    {
                        handler.accept(property, value);
                    }
                });

        return result;
    }
}
