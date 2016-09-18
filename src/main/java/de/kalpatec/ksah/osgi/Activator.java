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
package de.kalpatec.ksah.osgi;

import de.kalpatec.ksah.HttpServer;
import de.kalpatec.ksah.config.ConfigParser;
import de.kalpatec.ksah.handler.StaticHandler;
import java.net.InetSocketAddress;
import java.util.Dictionary;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

/**
 * Start a ksah instance inside OSGi.
 *
 * if the properties are set via the System/Bundle properties the server starts
 * directly. Otherwise, it needs to wait for a configuration via ConfigAdmin.
 *
 * In either case, it will publish a ManagedService to make the server configurable
 * via ConfigAdmin.
 *
* @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
public class Activator implements BundleActivator
{
    private final Object m_lock = new Object();
    private volatile HttpServer m_server;

    @Override
    public void start(BundleContext context) throws Exception
    {
        InetSocketAddress address = ConfigParser.parseBundleProperties(context, () -> null);

        if (address != null)
        {
            m_server =
                HttpServer.create(address,
                    ConfigParser.parseBundleProperties(context, HttpServer.options()),
                    StaticHandler.create(ConfigParser.parseBundleProperties(context, StaticHandler.options())
                    )::handle);
        }

        Dictionary props = new Properties();
        props.put(Constants.SERVICE_PID, "de.kalpatec.ksah");

        context.registerService(ManagedService.class.getName(), new ManagedService()
        {
            @Override
            public void updated(Dictionary dctnr) throws ConfigurationException
            {
                synchronized (m_lock)
                {
                    try
                    {
                        if (m_server != null && !m_server
                            .close()
                            .awaitClose(2, TimeUnit.SECONDS))
                        {
                            Logger.getLogger(Activator.class.getName()).log(Level.WARNING, "Timeout while stopping server");
                        }
                    }
                    catch (InterruptedException ex)
                    {
                        Logger.getLogger(Activator.class.getName()).log(Level.WARNING, "Interrupted", ex);
                    }
                    m_server = null;

                    if (dctnr != null)
                    {
                        InetSocketAddress address = ConfigParser.parseConfigProperties(dctnr, () -> null);
                        if (address != null)
                        {
                            try
                            {
                                m_server =
                                    HttpServer.create(address,
                                        ConfigParser.parseConfigProperties(dctnr, HttpServer.options()),
                                        StaticHandler.create(ConfigParser.parseConfigProperties(dctnr, StaticHandler.options())
                                        )::handle);
                            }
                            catch (Exception ex)
                            {
                                throw new RuntimeException(ex);
                            }
                        }
                    }
                }
            }
        }, props);
    }

    @Override
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public void stop(BundleContext context) throws Exception
    {
        synchronized (m_lock)
        {
            if (m_server != null && !m_server
                .close()
                .awaitClose(2, TimeUnit.SECONDS))
            {
                Logger.getLogger(Activator.class.getName()).log(Level.WARNING, "Timeout while stopping server");
            }
            m_server = null;
        }
    }
}
