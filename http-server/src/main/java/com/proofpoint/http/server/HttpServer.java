/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.http.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.proofpoint.bootstrap.AcceptRequests;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.stats.MaxGauge;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.management.MBeanServer;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ServerSocketChannel;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.toIntExact;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Collections.list;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;

public class HttpServer
{
    private static final String[] ENABLED_PROTOCOLS = {"SSLv2Hello", "TLSv1.1", "TLSv1.2"};
    private static final String[] ENABLED_CIPHERS = {
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_EMPTY_RENEGOTIATION_INFO_SCSV",
    };

    private final Server server;
    private final boolean registerErrorHandler;
    private final RequestStats stats;
    private final MaxGauge busyThreads = new MaxGauge();
    private final ClientAddressExtractor clientAddressExtractor;

    private final Optional<ZonedDateTime> certificateExpiration;

    /**
     * @deprecated Use {@link #HttpServer(HttpServerInfo, NodeInfo, HttpServerConfig, Servlet, Map,
     * Set, Set, Servlet, Map, Set, MBeanServer, LoginService, QueryStringFilter, RequestStats,
     * DetailedRequestStats, RequestLogHandler, ClientAddressExtractor)}.
     */
    @Deprecated
    public HttpServer(HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            Servlet theServlet,
            Map<String, String> parameters,
            Set<Filter> filters,
            Set<HttpResourceBinding> resources,
            @Nullable Servlet theAdminServlet,
            @Nullable Map<String, String> adminParameters,
            @Nullable Set<Filter> adminFilters,
            @Nullable MBeanServer mbeanServer,
            @Nullable LoginService loginService,
            QueryStringFilter queryStringFilter,
            RequestStats stats,
            DetailedRequestStats detailedRequestStats,
            @Nullable RequestLogHandler logHandler)
            throws IOException
    {
        this(httpServerInfo, nodeInfo, config, theServlet, parameters, filters, resources, theAdminServlet,
                adminParameters, adminFilters, mbeanServer, loginService, queryStringFilter, stats,
                detailedRequestStats, logHandler, new ClientAddressExtractor());

    }

    public HttpServer(HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            Servlet theServlet,
            Map<String, String> parameters,
            Set<Filter> filters,
            Set<HttpResourceBinding> resources,
            @Nullable Servlet theAdminServlet,
            @Nullable Map<String, String> adminParameters,
            @Nullable Set<Filter> adminFilters,
            @Nullable MBeanServer mbeanServer,
            @Nullable LoginService loginService,
            QueryStringFilter queryStringFilter,
            RequestStats stats,
            DetailedRequestStats detailedRequestStats,
            @Nullable RequestLogHandler logHandler,
            ClientAddressExtractor clientAddressExtractor)
            throws IOException
    {
        requireNonNull(httpServerInfo, "httpServerInfo is null");
        requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(config, "config is null");
        requireNonNull(theServlet, "theServlet is null");
        requireNonNull(parameters, "parameters is null");
        requireNonNull(filters, "filters is null");
        requireNonNull(resources, "resources is null");
        requireNonNull(queryStringFilter, "queryStringFilter is null");
        this.stats = requireNonNull(stats, "stats is null");
        requireNonNull(detailedRequestStats, "detailedRequestStats is null");
        this.clientAddressExtractor = requireNonNull(clientAddressExtractor, "clientAddressExtractor is null");

        QueuedThreadPool threadPool = new QueuedThreadPool(config.getMaxThreads())
        {
            @Override
            protected void runJob(Runnable job)
            {
                try {
                    busyThreads.add(1);
                    super.runJob(job);
                }
                finally {
                    busyThreads.add(-1);
                }
            }
        };
        threadPool.setMinThreads(config.getMinThreads());
        threadPool.setIdleTimeout(Ints.checkedCast(config.getThreadMaxIdleTime().toMillis()));
        threadPool.setName("http-worker");
        server = new Server(threadPool);
        server.setStopTimeout(config.getStopTimeout().toMillis());
        registerErrorHandler = config.isShowStackTrace();

        if (mbeanServer != null) {
            // export jmx mbeans if a server was provided
            MBeanContainer mbeanContainer = new MBeanContainer(mbeanServer);
            server.addBean(mbeanContainer);
        }

        HttpConfiguration baseHttpConfiguration = new HttpConfiguration();
        baseHttpConfiguration.setSendServerVersion(false);
        baseHttpConfiguration.setSendXPoweredBy(false);
        if (config.getMaxRequestHeaderSize() != null) {
            baseHttpConfiguration.setRequestHeaderSize(toIntExact(config.getMaxRequestHeaderSize().toBytes()));
        }

        // set up HTTP connector
        ServerConnector httpConnector;
        if (config.isHttpEnabled()) {
            HttpConfiguration httpConfiguration = new HttpConfiguration(baseHttpConfiguration);
            // if https is enabled, set the CONFIDENTIAL and INTEGRAL redirection information
            if (config.isHttpsEnabled()) {
                httpConfiguration.setSecureScheme("https");
                httpConfiguration.setSecurePort(httpServerInfo.getHttpsUri().getPort());
            }

            Integer acceptors = config.getHttpAcceptorThreads();
            Integer selectors = config.getHttpSelectorThreads();
            HttpConnectionFactory http1 = new HttpConnectionFactory(httpConfiguration);
            HTTP2CServerConnectionFactory http2c = new HTTP2CServerConnectionFactory(httpConfiguration);
            http2c.setInitialSessionRecvWindow(toIntExact(config.getHttp2InitialSessionReceiveWindowSize().toBytes()));
            http2c.setInitialStreamRecvWindow(toIntExact(config.getHttp2InitialStreamReceiveWindowSize().toBytes()));
            http2c.setMaxConcurrentStreams(config.getHttp2MaxConcurrentStreams());
            http2c.setInputBufferSize(toIntExact(config.getHttp2InputBufferSize().toBytes()));
            httpConnector = createServerConnector(
                    httpServerInfo.getHttpChannel(),
                    server,
                    null,
                    firstNonNull(acceptors, -1),
                    firstNonNull(selectors, -1),
                    http1,
                    http2c);
            httpConnector.setName("http");
            httpConnector.setPort(httpServerInfo.getHttpUri().getPort());
            httpConnector.setIdleTimeout(config.getNetworkMaxIdleTime().toMillis());
            httpConnector.setHost(nodeInfo.getBindIp().getHostAddress());
            httpConnector.setAcceptQueueSize(config.getHttpAcceptQueueSize());

            server.addConnector(httpConnector);
        }

        // set up NIO-based HTTPS connector
        ServerConnector httpsConnector;
        if (config.isHttpsEnabled()) {
            HttpConfiguration httpsConfiguration = new HttpConfiguration(baseHttpConfiguration);
            httpsConfiguration.addCustomizer(new SecureRequestCustomizer());

            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setKeyStorePath(config.getKeystorePath());
            sslContextFactory.setKeyStorePassword(config.getKeystorePassword());
            sslContextFactory.setExcludeProtocols();
            sslContextFactory.setIncludeProtocols(ENABLED_PROTOCOLS);
            sslContextFactory.setExcludeCipherSuites();
            sslContextFactory.setIncludeCipherSuites(ENABLED_CIPHERS);
            sslContextFactory.setCipherComparator(Ordering.explicit("", ENABLED_CIPHERS));
            SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, "http/1.1");

            Integer acceptors = config.getHttpsAcceptorThreads();
            Integer selectors = config.getHttpsSelectorThreads();
            httpsConnector = createServerConnector(
                    httpServerInfo.getHttpsChannel(),
                    server,
                    null,
                    firstNonNull(acceptors, -1),
                    firstNonNull(selectors, -1),
                    sslConnectionFactory,
                    new HttpConnectionFactory(httpsConfiguration));
            httpsConnector.setName("https");
            httpsConnector.setPort(httpServerInfo.getHttpsUri().getPort());
            httpsConnector.setIdleTimeout(config.getNetworkMaxIdleTime().toMillis());
            httpsConnector.setHost(nodeInfo.getBindIp().getHostAddress());
            httpsConnector.setAcceptQueueSize(config.getHttpAcceptQueueSize());


            server.addConnector(httpsConnector);
        }

        // set up NIO-based Admin connector
        ServerConnector adminConnector;
        if (config.isAdminEnabled()) {
            HttpConfiguration adminConfiguration = new HttpConfiguration(baseHttpConfiguration);

            QueuedThreadPool adminThreadPool = new QueuedThreadPool(config.getAdminMaxThreads());
            adminThreadPool.setName("http-admin-worker");
            adminThreadPool.setMinThreads(config.getAdminMinThreads());
            adminThreadPool.setIdleTimeout(Ints.checkedCast(config.getThreadMaxIdleTime().toMillis()));

            if (config.isHttpsEnabled()) {
                adminConfiguration.addCustomizer(new SecureRequestCustomizer());

                SslContextFactory sslContextFactory = new SslContextFactory();
                sslContextFactory.setKeyStorePath(config.getKeystorePath());
                sslContextFactory.setKeyStorePassword(config.getKeystorePassword());
                sslContextFactory.setExcludeProtocols();
                sslContextFactory.setIncludeProtocols(ENABLED_PROTOCOLS);
                sslContextFactory.setExcludeCipherSuites();
                sslContextFactory.setIncludeCipherSuites(ENABLED_CIPHERS);
                sslContextFactory.setCipherComparator(Ordering.explicit("", ENABLED_CIPHERS));
                SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, "http/1.1");
                adminConnector = createServerConnector(
                        httpServerInfo.getAdminChannel(),
                        server,
                        adminThreadPool,
                        0,
                        -1,
                        sslConnectionFactory,
                        new HttpConnectionFactory(adminConfiguration));
            }
            else {
                HttpConnectionFactory http1 = new HttpConnectionFactory(adminConfiguration);
                HTTP2CServerConnectionFactory http2c = new HTTP2CServerConnectionFactory(adminConfiguration);
                http2c.setMaxConcurrentStreams(config.getHttp2MaxConcurrentStreams());
                adminConnector = createServerConnector(
                        httpServerInfo.getAdminChannel(),
                        server,
                        adminThreadPool,
                        -1,
                        -1,
                        http1,
                        http2c);
            }

            adminConnector.setName("admin");
            adminConnector.setPort(httpServerInfo.getAdminUri().getPort());
            adminConnector.setIdleTimeout(config.getNetworkMaxIdleTime().toMillis());
            adminConnector.setHost(nodeInfo.getBindIp().getHostAddress());
            adminConnector.setAcceptQueueSize(config.getHttpAcceptQueueSize());


            server.addConnector(adminConnector);
        }

        /*
         * structure is:
         *
         * server
         *    |--- statistics handler
         *           |--- context handler
         *           |       |--- (no) admin filter
         *           |       |--- timing filter
         *           |       |--- query string filter
         *           |       |--- trace token filter
         *           |       |--- gzip response filter
         *           |       |--- gzip request filter
         *           |       |--- security handler
         *           |       |--- user provided filters
         *           |       |--- the servlet (normally GuiceContainer)
         *           |       |--- resource handlers
         *           |--- log handler
         *    |-- admin context handler
         *           |--- timing filter
         *           |--- query string filter
         *           |--- trace token filter
         *           |--- gzip response filter
         *           |--- gzip request filter
         *           |--- security handler
         *           |--- user provided admin filters
         *           \--- the servlet
         */
        HandlerCollection handlers = new HandlerCollection();

        for (HttpResourceBinding resource : resources) {
            GzipHandler gzipHandler = new GzipHandler();
            gzipHandler.setHandler(new ClassPathResourceHandler(resource.getBaseUri(), resource.getClassPathResourceBase(), resource.getWelcomeFiles()));
            handlers.addHandler(gzipHandler);
        }

        handlers.addHandler(createServletContext(theServlet, parameters, false, filters, queryStringFilter, loginService, nodeInfo, "http", "https"));
        if (logHandler != null) {
            handlers.addHandler(logHandler);
        }

        RequestLogHandler statsRecorder = new RequestLogHandler();
        statsRecorder.setRequestLog(new StatsRecordingHandler(stats, detailedRequestStats));
        handlers.addHandler(statsRecorder);

        // add handlers to Jetty
        StatisticsHandler statsHandler = new StatisticsHandler();
        statsHandler.setHandler(handlers);

        HandlerList rootHandlers = new HandlerList();
        if (theAdminServlet != null && config.isAdminEnabled()) {
            rootHandlers.addHandler(createServletContext(theAdminServlet, adminParameters, true, adminFilters, queryStringFilter, loginService, nodeInfo, "admin"));
        }
        rootHandlers.addHandler(statsHandler);
        server.setHandler(rootHandlers);

        certificateExpiration = loadAllX509Certificates(config).stream()
                .map(X509Certificate::getNotAfter)
                .min(naturalOrder())
                .map(date -> ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
    }

    private ServletContextHandler createServletContext(Servlet theServlet,
            Map<String, String> parameters,
            boolean isAdmin,
            Set<Filter> filters,
            QueryStringFilter queryStringFilter,
            LoginService loginService,
            NodeInfo nodeInfo,
            String... connectorNames)
    {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);

        if (!isAdmin) {
            // Filter out any /admin JAX-RS resources that were implicitly bound.
            // May be removed once we require explicit JAX-RS binding.
            context.addFilter(new FilterHolder(new AdminFilter(false)), "/*", null);
        }
        context.addFilter(new FilterHolder(new TimingFilter()), "/*", null);
        context.addFilter(new FilterHolder(queryStringFilter), "/*", null);
        context.addFilter(new FilterHolder(new TraceTokenFilter(nodeInfo.getInternalIp(), clientAddressExtractor)), "/*", null);

        // -- gzip handler
        context.setGzipHandler(new GzipHandler());

        // -- gzip request filter
        context.addFilter(GZipRequestFilter.class, "/*", null);
        // -- security handler
        if (loginService != null) {
            SecurityHandler securityHandler = createSecurityHandler(loginService);
            context.setSecurityHandler(securityHandler);
        }
        // -- user provided filters
        for (Filter filter : filters) {
            context.addFilter(new FilterHolder(filter), "/*", null);
        }
        // -- the servlet
        ServletHolder servletHolder = new ServletHolder(theServlet);
        servletHolder.setInitParameters(ImmutableMap.copyOf(parameters));
        context.addServlet(servletHolder, "/*");

        // Starting with Jetty 9 there is no way to specify connectors directly, but
        // there is this wonky @ConnectorName virtual hosts automatically added
        String[] virtualHosts = new String[connectorNames.length];
        for (int i = 0; i < connectorNames.length; i++) {
            virtualHosts[i] = "@" + connectorNames[i];
        }
        context.setVirtualHosts(virtualHosts);

        return context;
    }

    private static SecurityHandler createSecurityHandler(LoginService loginService)
    {
        Constraint constraint = new Constraint();
        constraint.setAuthenticate(false);

        ConstraintMapping constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec("/*");

        ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
        securityHandler.setLoginService(loginService);

        // TODO: support for other auth schemes (digest, etc)
        securityHandler.setAuthenticator(new BasicAuthenticator());
        securityHandler.setConstraintMappings(ImmutableList.of(constraintMapping));
        return securityHandler;
    }

    @Managed
    public Long getDaysUntilCertificateExpiration()
    {
        return certificateExpiration.map(date -> ZonedDateTime.now().until(date, DAYS))
                .orElse(null);
    }

    @AcceptRequests
    public void start()
            throws Exception
    {
        server.start();
        // clear the error handler registered by start()
        if (!registerErrorHandler) {
            server.setErrorHandler(null);
        }
        checkState(server.isStarted(), "server is not started");
    }

    @PreDestroy
    public void stop()
            throws Exception
    {
        server.stop();
    }

    @Flatten
    public RequestStats getStats()
    {
        return stats;
    }

    @Nested
    public MaxGauge getBusyThreads()
    {
        return busyThreads;
    }

    private static Set<X509Certificate> loadAllX509Certificates(HttpServerConfig config)
    {
        ImmutableSet.Builder<X509Certificate> certificates = ImmutableSet.builder();
        if (config.isHttpsEnabled()) {
            try (InputStream keystoreInputStream = new FileInputStream(config.getKeystorePath())) {
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                keystore.load(keystoreInputStream, config.getKeystorePassword().toCharArray());

                for (String alias : list(keystore.aliases())) {
                    try {
                        Certificate certificate = keystore.getCertificate(alias);
                        if (certificate instanceof X509Certificate) {
                            certificates.add((X509Certificate) certificate);
                        }
                    }
                    catch (KeyStoreException ignored) {
                    }
                }
            }
            catch (Exception ignored) {
            }
        }
        return certificates.build();
    }

    private static ServerConnector createServerConnector(
            ServerSocketChannel channel,
            Server server,
            Executor executor,
            int acceptors,
            int selectors,
            ConnectionFactory... factories)
            throws IOException
    {
        ServerConnector connector = new ServerConnector(server, executor, null, null, acceptors, selectors, factories);
        connector.open(channel);
        return connector;
    }
}
