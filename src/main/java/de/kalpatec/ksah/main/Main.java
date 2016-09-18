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
package de.kalpatec.ksah.main;

import de.kalpatec.ksah.HttpServer;
import de.kalpatec.ksah.config.ConfigParser;
import de.kalpatec.ksah.handler.StaticHandler;
import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * A launcher for ksah.
 *
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
public class Main
{
    /**
     * We'll read system properties first via the ConfigParser, next we read
     * commandline params via the ConfigParser.
     *
     * then we start ksah and wait for it to stop.
     *
     * @see ConfigParser
     * @param args
     * @throws Exception
     */
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public static void main(String[] args) throws Exception
    {
        if (args.length % 2 != 0)
        {
            System.out.println("\nksah [\"<option-name>\" \"<option-value>\"]\n");
            System.out.println("Options: ");

            EnumSet.allOf(ConfigParser.Properties.class).stream().sorted()
                .forEach(property -> System.out.println(property.name() + " " + property.getDesc()));

            System.out.println("\n");
            return;
        }

        InetSocketAddress address =
            ConfigParser.parseProperties(args,
                () -> ConfigParser.parseSystemProperties(
                    ()-> new InetSocketAddress(8080)));

        HttpServer server =
            HttpServer.create(address,
                    ConfigParser.parseProperties(args, ConfigParser.parseSystemProperties(HttpServer.options())),
                    StaticHandler.create(ConfigParser.parseProperties(args, ConfigParser.parseSystemProperties(StaticHandler.options()))
                    )::handle
                );

        System.out.println("ksah running on: " + address);

        server.awaitClose(Integer.MAX_VALUE, TimeUnit.DAYS);

        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            @SuppressWarnings("UseOfSystemOutOrSystemErr")
            public void run()
            {
                server.close();
                try
                {
                    if (!server.awaitClose(2, TimeUnit.SECONDS))
                    {
                        System.err.println("Unable to shutdown cleanly");
                    }
                }
                catch (InterruptedException ex)
                {
                    ex.printStackTrace(System.err);
                }
            }
        });
    }
}
