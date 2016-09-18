# ksah - A ligthweight asynchronous HTTP server.

**ksah** is a multithreaded, asynchronous, and none blocking web server writting in Java. 
Its goal is to feature acceptable performance under heavy load while being reasonable 
lightweight and depending on Java8 only. It's intended for embeded (IoT) as well as 
microservice use-cases.

## Status

At the moment, **ksah** is just getting started and will be under development for awhile.

## Features

The server currently:

* responds to HTTP/1.1 GET, HEAD, and OPTIONS (additionally, it can be configured to support some basic PUT and DELETE) 
  requests (all others request methods respond with 405).
* supports basic directory listing.
* supports HTTP/1.1 keep-alive behavior.
* supports 100-continue behavior.
* is pretty configurable and should have reasonable good response times under heavy load (if configured correctly).

## Usage

There are 3 differnt usage scenarios namely, as a standalone program, as a library/toolkit, and as a bundle inside OSGi.

### Commandline

When started from the commandline all that is needed is the *de.kalpatec.ksah-0.1.0-SNAPSHOT.jar* file.

    $ java -jar de.kalpatec.ksah-0.1.0-SNAPSHOT.jar

this will start the server on port 8080 with reasonable defaults. Files will be served from a *www* dir inside
the current directory (if there is any). Notice that PUT and DELETE are **not** enabled in this
case.

#### Commandline options

A lot of things can be configured via the commandline - the full listing looks like this:

    $ java -jar de.kalpatec.ksah-0.1.0-SNAPSHOT.jar help

    ksah ["<option-name>" "<option-value>"]

    Options:
    port "<number>" - The port to bind to (mandatory).
    address "<interface>" - The interface name to bind to (optional, default: "0.0.0.0").
    backlog "<number>" - The backlog size (optional, default: "1024").
    keepalive "<boolean>" - Set tcp keepalive (optional, default: "true").
    nodelay "<boolean>" - Set tcp nodelay (optional, default: "true").
    reuseaddress "<boolean>" - Set tcp reuseaddress (optional, default: "true").
    rcvbuf "<number>" - The size of the receive buffer which is also the max header size (optional, default: "65536").
    sndbuf "<number>" - The size of the send buffer (optional, default: "65536").
    buffercache "<number>" - The total size of _direct_ buffers used for receiving (optional, default: "16777216").
    timeout "<number>:<unit>" - The read/write timeout for a socket (optional, default: "2:SECONDS").
    maxconnnections "<number>" - The max number of concurrent connections (optional, default: "1024").
    maxconnectiontime "<number>" - The max time a connection is keep-alive in milliseconds, 0 for no keep-alive (optional, default: "10000").
    root "<path>" - The location of the web root (optional, default: "./www").
    bufferSize "<number>" - The size of the read file buffer (optional, default: "65536").
    staticbuffercache "<number>" - The total size of _direct_ buffers used for reading files (optional, default: "16777216").
    charset "<encoding>" - The charset send for text/ mime files (optional, default: "<plattform-encoding>").
    list "<boolean>" - Enable directory listings (optional, default: "true").
    write "<boolean>" - Enable writing via PUT and DELETE (optional, default: "false").

#### Basic options

Perhaps the most important options are:

    port "<number>" - The port to bind to (mandatory).

to change the port number,

    root "<path>" - The location of the web root (optional, default: "./www").

to change the directory from which files are served, and

    write "<boolean>" - Enable writing via PUT and DELETE (optional, default: "false").

to enable support for PUT and DELETE.

#### Basic options example

As a simple example consider:

    java -jar de.kalpatec.ksah-0.1.0-SNAPSHOT.jar port 4711 write true root ~/public_html

which would make ksah available on port 4711, with PUT and DELETE enabled, while serving and listing the
user.home/public_html folder.

### Library and toolkit

Using ksah as a library is reasonable straight-forward. It mainly requires to create and configure the HttpServer
and bind it to the StaticHandler that handles the requests. The important interfaces are
in *de.kalpatec.ksah* as well as in *de.kalpatec.ksah.handler*.

#### Library example

Consider this snippet from the test setup (provided under src/test/java):

    HttpServer server =
        HttpServer.create(
            new InetSocketAddress(4711),
            HttpServer.options()
                .timeout(10, TimeUnit.SECONDS),
            StaticHandler.create(
                StaticHandler.options()
                    .root(www.toPath()).list(false).write(true)
            )::handle);

As you can see, we *HttpServer.create(...)* the server with the address and port
we want and configure it via a *HttpServer.options()* object (setting the timeout to 10 seconds).
Additionally, we use the file handler provided via StaticHandler.create(...) which
we pass a *StaticHandler.options()* object to configure the handler (setting the root from
which files are served to a given www dir, disable directory listing, and enable PUT and DELETE support.

Now, all we need to do is to keep a thread alive so that the JVM doesn't exit (the server itself
doesn't have non deamon threads by default). The easiest is to just wait for the server to be closed
like we do in *de.kalpatec.ksah.main.Main*:

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

#### Toolkit

The core receive and send dispatch loop (i.e., the webserver itself) is what it
is right now and can not easily be extended, however, how the request is handled
can be via a custom handler. Instead of the StaticHandler:handle method one
can pass any other handler and for example create a router this way which in turn
could pass requests to more specialised handlers registering for more fine-grained
endpoints.

Additionally, the *de.kalpatec.ksah.handler.impl.StaticHandlerImpl* can be
extended via subclassing (i.e., inheritence). It routes all incomming requests
through protected do{Method} methods (i.e., doGet, doPost, etc.) - hence, in order to
handle a given method differently or implement a not implemented method just
override the given do{Method} method.

### OSGi

The *de.kalpatec.ksah-0.1.0-SNAPSHOT.jar* is a OSGi bundle and
comes with an Activator that will, if the bundle is started, see if at least
the port is set via the System/Bundle properties and in that case start the server
directly. Otherwise, it will wait for a configuration via ConfigAdmin.

In either case, it will publish a ManagedService with a service pid of *de.kalpatec.ksah*
to make the server configurable via ConfigAdmin.

## Limitations

* We don't support any chunked (nor other than identity) encoding (neither sending nor receiving).
* A lot of headers are not understood (like Accept, Content-*)
* Put support is pretty basic.
* Monitoring could be improved (errors and exceptions get logged but there is no information about ongoing requests).
* The automatic test coverage needs to be improved (there was quite some testing but it isn't captured in the integration tests).
* Put should really be writting incomming files into a tmp file first and only *move*
  them to the real location on a successful request (right now, if a request stops in the middle the original
  file is corrupted).
* The caching headers should be set by the StaticHandlerImpl.
* Neither the build nor, running ksah has been tested on Windows (build only MacOSX and running on both Mac and Linux - make sure you
  have UTF-8 configured correctly).
* Javadoc creation is hooked-up to the maven pom but not done automatically (and the javadoc needs improvement anyhow).
* And a lot of other things :-)
