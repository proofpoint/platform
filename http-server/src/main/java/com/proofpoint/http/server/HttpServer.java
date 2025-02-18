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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.proofpoint.bootstrap.AcceptRequests;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.stats.MaxGauge;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.ee10.servlet.security.ConstraintMapping;
import org.eclipse.jetty.ee10.servlet.security.ConstraintSecurityHandler;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.jmx.MBeanContainer;
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
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.management.MBeanServer;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ServerSocketChannel;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.Math.toIntExact;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Collections.list;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jetty.http.MimeTypes.Type.TEXT_PLAIN;
import static org.eclipse.jetty.security.Constraint.ALLOWED;

public class HttpServer
{
    private static final String[] ENABLED_PROTOCOLS = System.getProperty("java.version").matches("11(\\.0\\.[12])?") ?
            new String[] {"TLSv1.2"} : new String[] {"TLSv1.2", "TLSv1.3"};
    private static final String[] ENABLED_CIPHERS = {
            "TLS_AES_128_GCM_SHA256",
            "TLS_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_EMPTY_RENEGOTIATION_INFO_SCSV",
    };

    private final Server server;
    private final boolean showStackTrace;
    private final RequestStats stats;
    private final MaxGauge busyThreads = new MaxGauge();
    private final RequestLog requestLog;
    private final ClientAddressExtractor clientAddressExtractor;

    private final HttpServerInfo httpServerInfo;
    private final NodeInfo nodeInfo;
    private final HttpServerConfig config;
    private final Optional<ZonedDateTime> certificateExpiration;
    private final HttpServerModuleOptions moduleOptions;

    /**
     * @deprecated Will no longer be public
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
            @Nullable SessionHandler sessionHandler,
            QueryStringFilter queryStringFilter,
            RequestStats stats,
            DetailedRequestStats detailedRequestStats,
            @Nullable RequestLog requestLog,
            ClientAddressExtractor clientAddressExtractor)
    {
        this(httpServerInfo, nodeInfo, config, theServlet, parameters,
                filters, resources, theAdminServlet, adminParameters, adminFilters, mbeanServer,
                loginService, sessionHandler, queryStringFilter, stats, detailedRequestStats,
                requestLog, clientAddressExtractor, new HttpServerModuleOptions());
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
            @Nullable SessionHandler sessionHandler,
            QueryStringFilter queryStringFilter,
            RequestStats stats,
            DetailedRequestStats detailedRequestStats,
            @Nullable RequestLog requestLog,
            ClientAddressExtractor clientAddressExtractor,
            HttpServerModuleOptions moduleOptions)
    {
        this.httpServerInfo = requireNonNull(httpServerInfo, "httpServerInfo is null");
        this.nodeInfo = requireNonNull(nodeInfo, "nodeInfo is null");
        this.config = requireNonNull(config, "config is null");
        requireNonNull(theServlet, "theServlet is null");
        requireNonNull(parameters, "parameters is null");
        requireNonNull(filters, "filters is null");
        requireNonNull(resources, "resources is null");
        requireNonNull(queryStringFilter, "queryStringFilter is null");
        this.stats = requireNonNull(stats, "stats is null");
        requireNonNull(detailedRequestStats, "detailedRequestStats is null");
        this.requestLog = requestLog;
        this.clientAddressExtractor = requireNonNull(clientAddressExtractor, "clientAddressExtractor is null");
        this.moduleOptions = requireNonNull(moduleOptions, "httpServerModuleOptions is null");

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
        threadPool.setIdleTimeout(toIntExact(config.getThreadMaxIdleTime().toMillis()));
        threadPool.setName("http-worker");
        server = new Server(threadPool);
        server.setStopTimeout(config.getStopTimeout().toMillis());
        showStackTrace = config.isShowStackTrace();

        if (mbeanServer != null) {
            // export jmx mbeans if a server was provided
            MBeanContainer mbeanContainer = new MBeanContainer(mbeanServer);
            server.addBean(mbeanContainer);
        }

        // register a channel listener if logging is enabled

        /*
         * structure is:
         *
         * server
         * |- request logging handler (optional)
         *    |--- statistics handler
         *           |--- context handler
         *           |       |--- (no) admin filter
         *           |       |--- timing filter
         *           |       |--- query string filter
         *           |       |--- trace token filter
         *           |       |--- gzip request filter
         *           |       |--- security handler
         *           |       |--- resource filters
         *           |       |--- user provided filters
         *           |       |--- gzip response filter
         *           |       |--- the servlet (normally GuiceContainer)
         *           |       |--- session handler (optional)
         *           |--- log handler
         *    |-- admin context handler
         *           |--- timing filter
         *           |--- query string filter
         *           |--- trace token filter
         *           |--- gzip request filter
         *           |--- security handler
         *           |--- resource filters
         *           |--- user provided admin filters
         *           |--- gzip response filter
         *           \--- the servlet
         */
        // add handlers to Jetty
        StatisticsHandler statsHandler = new StatisticsHandler();
        statsHandler.setHandler(createServletContext(theServlet, resources, parameters, false, filters, queryStringFilter, loginService, nodeInfo, sessionHandler, Set.of("http", "https"), showStackTrace));

        ContextHandlerCollection rootHandlers = new ContextHandlerCollection();
        if (theAdminServlet != null && config.isAdminEnabled()) {
            rootHandlers.addHandler(createServletContext(theAdminServlet, resources, adminParameters, true, adminFilters, queryStringFilter, loginService, nodeInfo, null, Set.of("admin"), showStackTrace));
        }
        rootHandlers.addHandler(statsHandler);
        StatsRecordingHandler statsRecordingHandler = new StatsRecordingHandler(stats, detailedRequestStats);

        if (requestLog != null) {
            RequestLogHandler logHandler = new RequestLogHandler(requestLog, clientAddressExtractor);
            server.setRequestLog(new RequestLogCollection(logHandler, statsRecordingHandler));
            logHandler.setHandler(rootHandlers);
            server.setHandler(logHandler);
        }
        else {
            server.setRequestLog(statsRecordingHandler);
            server.setHandler(rootHandlers);
        }
        ErrorHandler errorHandler = new ErrorHandler();
        errorHandler.setShowMessageInTitle(showStackTrace);
        errorHandler.setShowCauses(showStackTrace);
        errorHandler.setShowStacks(showStackTrace);
        errorHandler.setDefaultResponseMimeType(TEXT_PLAIN.asString());
        server.setErrorHandler(errorHandler);

        certificateExpiration = loadAllX509Certificates(config).stream()
                .map(X509Certificate::getNotAfter)
                .min(naturalOrder())
                .map(date -> ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
    }

    private ServerConnector createHttpsServerConnector(HttpServerConfig config, ServerSocketChannel serverSocketChannel, HttpConfiguration configuration, Executor threadPool, int acceptors, int selectors)
            throws IOException
    {
        checkState(!System.getProperty("java.version").startsWith("1.8."), "Java 8 is no longer supported");

        SecureRequestCustomizer customizer = new SecureRequestCustomizer();
        customizer.setSniHostCheck(false);
        customizer.setSniRequired(false);
        configuration.addCustomizer(customizer);

        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setEndpointIdentificationAlgorithm("HTTPS");
        sslContextFactory.setKeyStorePath(config.getKeystorePath());
        sslContextFactory.setKeyStorePassword(config.getKeystorePassword());
        sslContextFactory.setExcludeProtocols();
        sslContextFactory.setIncludeProtocols(ENABLED_PROTOCOLS);
        sslContextFactory.setExcludeCipherSuites();
        sslContextFactory.setIncludeCipherSuites(ENABLED_CIPHERS);
        sslContextFactory.setCipherComparator(Ordering.explicit("", ENABLED_CIPHERS));
        sslContextFactory.setSslSessionTimeout((int) config.getSslSessionTimeout().getValue(SECONDS));
        sslContextFactory.setSslSessionCacheSize(config.getSslSessionCacheSize());
        sslContextFactory.setSniRequired(false);

        List<ConnectionFactory> connectionFactories = new ArrayList<>();
        connectionFactories.add(new SslConnectionFactory(sslContextFactory, "alpn"));
        connectionFactories.add(new HttpConnectionFactory(configuration));
        connectionFactories.add(new ALPNServerConnectionFactory("h2", "http/1.1"));
        HTTP2ServerConnectionFactory http2 = new HTTP2ServerConnectionFactory(configuration);
        http2.setInitialSessionRecvWindow(toIntExact(config.getHttp2InitialSessionReceiveWindowSize().toBytes()));
        http2.setInitialStreamRecvWindow(toIntExact(config.getHttp2InitialStreamReceiveWindowSize().toBytes()));
        http2.setMaxConcurrentStreams(config.getHttp2MaxConcurrentStreams());
        http2.setInputBufferSize(toIntExact(config.getHttp2InputBufferSize().toBytes()));
        http2.setStreamIdleTimeout(config.getHttp2StreamIdleTimeout().toMillis());
        connectionFactories.add(http2);

        return createServerConnector(
                serverSocketChannel,
                server,
                threadPool,
                acceptors,
                selectors,
                connectionFactories.toArray(new ConnectionFactory[0]));
    }

    private ServletContextHandler createServletContext(Servlet theServlet,
            Set<HttpResourceBinding> resources,
            Map<String, String> parameters,
            boolean isAdmin,
            Set<Filter> filters,
            QueryStringFilter queryStringFilter,
            LoginService loginService,
            NodeInfo nodeInfo,
            SessionHandler sessionHandler,
            Set<String> connectorNames,
            boolean showStackTrace)
    {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        if (moduleOptions.isAllowAmbiguousUris()) {
            ServletHandler servletHandler = new ServletHandler();
            servletHandler.setDecodeAmbiguousURIs(true);
            context.setServletHandler(servletHandler);
        }
        ErrorHandler errorHandler = new ErrorHandler();
        errorHandler.setShowCauses(showStackTrace);
        errorHandler.setShowStacks(showStackTrace);
        errorHandler.setShowMessageInTitle(showStackTrace);
        errorHandler.setDefaultResponseMimeType(TEXT_PLAIN.asString());
        context.setErrorHandler(errorHandler);

        if (!isAdmin) {
            // Filter out any /admin JAX-RS resources that were implicitly bound.
            // May be removed once we require explicit JAX-RS binding.
            context.addFilter(new FilterHolder(new AdminFilter(false)), "/*", null);
        }
        context.addFilter(new FilterHolder(new TimingFilter()), "/*", null);
        context.addFilter(new FilterHolder(queryStringFilter), "/*", null);
        context.addFilter(new FilterHolder(new TraceTokenFilter(nodeInfo.getInternalIp(), clientAddressExtractor)), "/*", null);

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
        // -- static resources
        for (HttpResourceBinding resource : resources) {
            ClassPathResourceFilter servlet = new ClassPathResourceFilter(
                    resource.getBaseUri(),
                    resource.getClassPathResourceBase(),
                    resource.getWelcomeFiles());
            String pathSpec = servlet.getBaseUri() + "/*";
            if (pathSpec.equals("//*")) {
                pathSpec = "/*";
            }
            context.addFilter(new FilterHolder(servlet), pathSpec, null);
        }
        // -- gzip handler
        context.insertHandler(new GzipHandler());

        // -- add SessionHandler
        if (sessionHandler != null) {
            context.setSessionHandler(sessionHandler);
        }

        // -- the servlet
        ServletHolder servletHolder = new ServletHolder(theServlet);
        servletHolder.setInitParameters(ImmutableMap.copyOf(parameters));
        context.addServlet(servletHolder, "/*");

        // Starting with Jetty 9 there is no way to specify connectors directly, but
        // there is this wonky @ConnectorName virtual hosts automatically added
        List<String> virtualHosts = connectorNames.stream()
                .map(connectorName -> "@" + connectorName)
                .collect(toImmutableList());

        context.setVirtualHosts(virtualHosts);

        return context;
    }

    private static SecurityHandler createSecurityHandler(LoginService loginService)
    {
        ConstraintMapping constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(ALLOWED);
        constraintMapping.setPathSpec("/*");

        ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
        securityHandler.setLoginService(loginService);

        // TODO: support for other auth schemes (digest, etc)
        securityHandler.setAuthenticator(new BasicAuthenticator());
        securityHandler.setConstraintMappings(List.of(constraintMapping));
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
        HttpConfiguration baseHttpConfiguration = new HttpConfiguration();
        baseHttpConfiguration.setSendServerVersion(false);
        baseHttpConfiguration.setSendXPoweredBy(false);
        if (config.getMaxRequestHeaderSize() != null) {
            baseHttpConfiguration.setRequestHeaderSize(toIntExact(config.getMaxRequestHeaderSize().toBytes()));
        }
        if (moduleOptions.isAllowAmbiguousUris()) {
            baseHttpConfiguration.setUriCompliance(UriCompliance.from(Set.of(
                    UriCompliance.Violation.AMBIGUOUS_EMPTY_SEGMENT,
                    UriCompliance.Violation.AMBIGUOUS_PATH_ENCODING,
                    UriCompliance.Violation.AMBIGUOUS_PATH_PARAMETER,
                    UriCompliance.Violation.AMBIGUOUS_PATH_SEGMENT,
                    UriCompliance.Violation.AMBIGUOUS_PATH_SEPARATOR)));
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
            http2c.setStreamIdleTimeout(config.getHttp2StreamIdleTimeout().toMillis());
            httpConnector = createServerConnector(
                    httpServerInfo.getHttpChannel(),
                    server,
                    null,
                    requireNonNullElse(acceptors, -1),
                    requireNonNullElse(selectors, -1),
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

            Integer acceptors = config.getHttpsAcceptorThreads();
            Integer selectors = config.getHttpsSelectorThreads();
            httpsConnector = createHttpsServerConnector(
                    config,
                    httpServerInfo.getHttpsChannel(),
                    httpsConfiguration,
                    null,
                    requireNonNullElse(acceptors, -1),
                    requireNonNullElse(selectors, -1));
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
            adminThreadPool.setIdleTimeout(toIntExact(config.getThreadMaxIdleTime().toMillis()));

            if (config.isHttpsEnabled()) {
                adminConnector = createHttpsServerConnector(
                        config,
                        httpServerInfo.getAdminChannel(),
                        adminConfiguration,
                        adminThreadPool,
                        0,
                        -1);
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

        server.start();
        checkState(server.isStarted(), "server is not started");
    }

    @PreDestroy
    public void stop()
            throws Exception
    {
        server.stop();
        if (requestLog != null) {
            requestLog.stop();
        }
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
            catch (IOException | CertificateException | NoSuchAlgorithmException | KeyStoreException ignored) {
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
