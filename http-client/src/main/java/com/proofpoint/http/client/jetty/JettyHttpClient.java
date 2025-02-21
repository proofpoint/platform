package com.proofpoint.http.client.jetty;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.io.Closeables;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;
import com.proofpoint.http.client.BodySource;
import com.proofpoint.http.client.DynamicBodySource;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.HttpRequestFilter;
import com.proofpoint.http.client.InputStreamBodySource;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.StaticBodyGenerator;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;
import org.eclipse.jetty.client.AbstractConnectionPool;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.Destination;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.InputStreamResponseListener;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Socks4Proxy;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.eclipse.jetty.client.transport.HttpDestination;
import org.eclipse.jetty.client.transport.HttpExchange;
import org.eclipse.jetty.client.transport.HttpRequest;
import org.eclipse.jetty.client.transport.internal.HttpConnectionOverHTTP;
import org.eclipse.jetty.http.HttpCookieStore;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.transport.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.eclipse.jetty.util.thread.Sweeper;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.proofpoint.http.client.jetty.AuthorizationPreservingHttpClient.setPreserveAuthorization;
import static com.proofpoint.http.client.jetty.Stats.stats;
import static java.lang.Math.max;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.eclipse.jetty.client.ConnectionPoolAccessor.getActiveConnections;
import static org.eclipse.jetty.client.ConnectionPoolAccessor.getIdleConnections;

public class JettyHttpClient
        implements com.proofpoint.http.client.HttpClient
{

    private static final Logger log = Logger.get(JettyHttpClient.class);
    private static final String[] ENABLED_PROTOCOLS = {"TLSv1.2", "TLSv1.3"};
    private static final String[] ENABLED_CIPHERS = {
            "TLS_AES_128_GCM_SHA256",
            "TLS_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_EMPTY_RENEGOTIATION_INFO_SCSV",
    };

    private static final String PLATFORM_STATS_KEY = "platform_stats";
    private static final long SWEEP_PERIOD_MILLIS = 5000;

    private static final AtomicLong NAME_COUNTER = new AtomicLong();
    private static final JettyHttpClientOptions DEFAULT_CLIENT_OPTIONS =
        new JettyHttpClientOptions.Builder()
            .setEnableCertificateVerification(true)
            .build();

    private final HttpClient httpClient;
    private final long maxContentLength;
    private final Long requestTimeoutMillis;
    private final long idleTimeoutMillis;
    private final Stats stats;
    private final CachedDistribution queuedRequestsPerDestination;
    private final CachedDistribution activeConnectionsPerDestination;
    private final CachedDistribution idleConnectionsPerDestination;

    private final CachedDistribution currentQueuedTime;
    private final CachedDistribution currentRequestTime;
    private final CachedDistribution currentRequestSendTime;
    private final CachedDistribution currentResponseWaitTime;
    private final CachedDistribution currentResponseProcessTime;

    private final List<HttpRequestFilter> requestFilters;
    private final Exception creationLocation = new Exception();
    private final String name;
    private final AtomicLong lastLoggedJettyState = new AtomicLong();

    public JettyHttpClient()
    {
        this(new HttpClientConfig());
    }

    public JettyHttpClient(HttpClientConfig config)
    {
        this(uniqueName(), config);
    }

    public JettyHttpClient(String name, HttpClientConfig config)
    {
        this(name, config, List.of());
    }

    public JettyHttpClient(
        String name,
        HttpClientConfig config,
        Iterable<? extends HttpRequestFilter> requestFilters)
    {
        this(name, config, DEFAULT_CLIENT_OPTIONS, requestFilters);
    }

    public JettyHttpClient(
            String name,
            HttpClientConfig config,
            JettyHttpClientOptions options,
            Iterable<? extends HttpRequestFilter> requestFilters)
    {
        this.name = requireNonNull(name, "name is null");

        requireNonNull(config, "config is null");
        requireNonNull(requestFilters, "requestFilters is null");

        maxContentLength = config.getMaxContentLength().toBytes();
        Duration requestTimeout = config.getRequestTimeout();
        if (requestTimeout == null) {
            requestTimeoutMillis = null;
        }
        else {
            requestTimeoutMillis = requestTimeout.toMillis();
        }
        idleTimeoutMillis = config.getIdleTimeout().toMillis();

        creationLocation.fillInStackTrace();

        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setEndpointIdentificationAlgorithm("HTTPS");
        sslContextFactory.setExcludeProtocols();
        sslContextFactory.setIncludeProtocols(ENABLED_PROTOCOLS);
        sslContextFactory.setExcludeCipherSuites();
        sslContextFactory.setIncludeCipherSuites(ENABLED_CIPHERS);
        sslContextFactory.setCipherComparator(Ordering.explicit("", ENABLED_CIPHERS));
        if (config.getKeyStorePath() != null) {
            sslContextFactory.setKeyStorePath(config.getKeyStorePath());
            sslContextFactory.setKeyStorePassword(config.getKeyStorePassword());
        }
        if (config.getTrustStorePath() != null) {
            sslContextFactory.setTrustStorePath(config.getTrustStorePath());
            sslContextFactory.setTrustStorePassword(config.getTrustStorePassword());
        }
        if (!options.isEnableCertificateVerification()) {
            sslContextFactory.setTrustAll(true);
        }

        HttpClientTransport transport;
        if (config.isHttp2Enabled()) {
            HTTP2Client client = new HTTP2Client();
            client.setInitialSessionRecvWindow(toIntExact(config.getHttp2InitialSessionReceiveWindowSize().toBytes()));
            client.setInitialStreamRecvWindow(toIntExact(config.getHttp2InitialStreamReceiveWindowSize().toBytes()));
            client.setInputBufferSize(toIntExact(config.getHttp2InputBufferSize().toBytes()));
            client.setSelectors(config.getSelectorCount());
            transport = new HttpClientTransportOverHTTP2(client);
        }
        else {
            ClientConnector clientConnector = new ClientConnector();
            clientConnector.setSelectors(config.getSelectorCount());
            clientConnector.setSslContextFactory(sslContextFactory);
            transport = new HttpClientTransportOverHTTP(clientConnector);
        }

        httpClient = new AuthorizationPreservingHttpClient(transport);

        // request and response buffer size
        httpClient.setRequestBufferSize(toIntExact(config.getRequestBufferSize().toBytes()));
        httpClient.setResponseBufferSize(toIntExact(config.getResponseBufferSize().toBytes()));

        httpClient.setMaxRequestsQueuedPerDestination(config.getMaxRequestsQueuedPerDestination());
        httpClient.setMaxConnectionsPerDestination(config.getMaxConnectionsPerServer());

        // disable cookies
        httpClient.setHttpCookieStore(new HttpCookieStore.Empty());

        // remove default user agent
        httpClient.setUserAgentField(null);

        // timeouts
        httpClient.setIdleTimeout(idleTimeoutMillis);
        httpClient.setConnectTimeout(config.getConnectTimeout().toMillis());
        httpClient.setAddressResolutionTimeout(config.getConnectTimeout().toMillis());

        if (config.getConnectTimeout() != null) {
            long connectTimeout = config.getConnectTimeout().toMillis();
            httpClient.setConnectTimeout(connectTimeout);
            httpClient.setAddressResolutionTimeout(connectTimeout);
        }

        HostAndPort socksProxy = config.getSocksProxy();
        if (socksProxy != null) {
            httpClient.getProxyConfiguration().addProxy(new Socks4Proxy(socksProxy.getHost(), socksProxy.getPortOrDefault(1080)));
        }

        httpClient.setByteBufferPool(new ArrayByteBufferPool());
        QueuedThreadPool executor = createExecutor(name, config.getMinThreads(), config.getMaxThreads());
        stats = stats(executor);
        httpClient.setExecutor(executor);
        httpClient.setScheduler(createScheduler(name, config.getTimeoutConcurrency(), config.getTimeoutThreads()));

        httpClient.setSocketAddressResolver(new JettyAsyncSocketAddressResolver(
                httpClient.getExecutor(),
                httpClient.getScheduler(),
                config.getConnectTimeout().toMillis()));

        httpClient.setSocketAddressResolver(new JettyAsyncSocketAddressResolver(
                httpClient.getExecutor(),
                httpClient.getScheduler(),
                config.getConnectTimeout().toMillis()));

        // Jetty client connections can sometimes get stuck while closing which reduces
        // the available connections.  The Jetty Sweeper periodically scans the active
        // connection pool looking for connections in the closed state, and if a connection
        // is observed in the closed state multiple times, it logs, and destroys the connection.
        httpClient.addBean(new Sweeper(httpClient.getScheduler(), SWEEP_PERIOD_MILLIS), true);

        try {
            this.httpClient.start();

            // remove the GZIP encoding from the client
            // TODO: there should be a better way to to do this
            this.httpClient.getContentDecoderFactories().clear();
        }
        catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }

        this.requestFilters = ImmutableList.copyOf(requestFilters);

        this.activeConnectionsPerDestination = new ConnectionPoolDistribution(httpClient,
                (distribution, connectionPool) -> distribution.add(getActiveConnections(connectionPool).size()));

         this.idleConnectionsPerDestination = new ConnectionPoolDistribution(httpClient,
                 (distribution, connectionPool) -> distribution.add(getIdleConnections(connectionPool).size()));

         this.queuedRequestsPerDestination = new DestinationDistribution(httpClient,
                 (distribution, destination) -> distribution.add(destination.getHttpExchanges().size()));

         this.currentQueuedTime = new RequestDistribution(httpClient, (distribution, listener, now) -> {
             long started = listener.getRequestStarted();
             if (started == 0) {
                 started = now;
             }
             distribution.add(NANOSECONDS.toMillis(started - listener.getCreated()));
         });

         this.currentRequestTime = new RequestDistribution(httpClient, (distribution, listener, now) -> {
             long started = listener.getRequestStarted();
             if (started == 0) {
                 return;
             }
             long finished = listener.getResponseFinished();
             if (finished == 0) {
                 finished = now;
             }
             distribution.add(NANOSECONDS.toMillis(finished - started));
         });

         this.currentRequestSendTime = new RequestDistribution(httpClient, (distribution, listener, now) -> {
             long started = listener.getRequestStarted();
             if (started == 0) {
                 return;
             }
             long requestSent = listener.getRequestFinished();
             if (requestSent == 0) {
                 requestSent = now;
             }
             distribution.add(NANOSECONDS.toMillis(requestSent - started));
         });

         this.currentResponseWaitTime = new RequestDistribution(httpClient, (distribution, listener, now) -> {
             long requestSent = listener.getRequestFinished();
             if (requestSent == 0) {
                 return;
             }
             long responseStarted = listener.getResponseStarted();
             if (responseStarted == 0) {
                 responseStarted = now;
             }
             distribution.add(NANOSECONDS.toMillis(responseStarted - requestSent));
         });

         this.currentResponseProcessTime = new RequestDistribution(httpClient, (distribution, listener, now) -> {
             long responseStarted = listener.getResponseStarted();
             if (responseStarted == 0) {
                 return;
             }
             long finished = listener.getResponseFinished();
             if (finished == 0) {
                 finished = now;
             }
             distribution.add(NANOSECONDS.toMillis(finished - responseStarted));
         });
    }

    // Protect against finalizer attacks, as constructor can throw exception.
    @SuppressWarnings("deprecation")
    @Override
    protected final void finalize()
    {
    }

    private static QueuedThreadPool createExecutor(String name, int minThreads, int maxThreads)
    {
        try {
            QueuedThreadPool pool = new QueuedThreadPool(maxThreads, minThreads, 60000, null);
            pool.setName("http-client-" + name);
            pool.setDaemon(true);
            pool.start();
            pool.setStopTimeout(2000);
            return pool;
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    private static Scheduler createScheduler(String name, int timeoutConcurrency, int timeoutThreads)
    {
        Scheduler scheduler;
        String threadName = "http-client-" + name + "-scheduler";
        if ((timeoutConcurrency == 1) && (timeoutThreads == 1)) {
            scheduler = new ScheduledExecutorScheduler(threadName, true);
        }
        else {
            checkArgument(timeoutConcurrency >= 1, "timeoutConcurrency must be at least one");
            int threads = max(1, timeoutThreads / timeoutConcurrency);
            scheduler = new ConcurrentScheduler(timeoutConcurrency, threads, threadName);
        }

        try {
            scheduler.start();
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }

        return scheduler;
    }

    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        long requestStart = System.nanoTime();
        AtomicLong bytesWritten = new AtomicLong(0);

        // apply filters
        request = applyRequestFilters(request);

        // create jetty request and response listener
        HttpRequest jettyRequest = buildJettyRequest(request, bytesWritten);
        InputStreamResponseListener listener = new InputStreamResponseListener()
        {
            @Override
            public void onContent(Response response, ByteBuffer content)
            {
                // ignore empty blocks
                if (content.remaining() == 0) {
                    return;
                }
                super.onContent(response, content);
            }
        };

        // fire the request
        jettyRequest.send(listener);

        // wait for response to begin
        Response response;
        try {
            response = listener.get(httpClient.getIdleTimeout(), MILLISECONDS);
        }
        catch (InterruptedException e) {
            jettyRequest.abort(e);
            Thread.currentThread().interrupt();
            return responseHandler.handleException(request, e);
        }
        catch (TimeoutException e) {
            jettyRequest.abort(e);
            return responseHandler.handleException(request, e);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RejectedExecutionException || cause instanceof ClosedByInterruptException) {
                maybeLogJettyState();
            }
            if (cause instanceof Exception) {
                return responseHandler.handleException(request, (Exception) cause);
            }
            return responseHandler.handleException(request, new RuntimeException(cause));
        }

        // process response
        long responseStart = System.nanoTime();

        JettyResponse jettyResponse = null;
        T value;
        try {
            InputStream inputStream = listener.getInputStream();
            try {
                jettyResponse = new JettyResponse(response, inputStream);
                value = responseHandler.handle(request, jettyResponse);
            }
            finally {
                Closeables.closeQuietly(inputStream);
            }
        }
        finally {
            recordRequestComplete(stats, request, requestStart, bytesWritten.get(), jettyResponse, responseStart);
        }
        return value;
    }

    void maybeLogJettyState()
    {
        long lastLogged = lastLoggedJettyState.get();
        long time = System.nanoTime();
        if (lastLogged + 60_000_000_000L > time) {
            return;
        }
        if (lastLoggedJettyState.compareAndSet(lastLogged, time)) {
            log.warn("Received RejectedExecutionException. Jetty dump:\n%s", dump());
        }
    }

    @Override
    public <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
    {
        requireNonNull(request, "request is null");
        requireNonNull(responseHandler, "responseHandler is null");
        AtomicLong bytesWritten = new AtomicLong(0);

        request = applyRequestFilters(request);

        HttpRequest jettyRequest = buildJettyRequest(request, bytesWritten);

        JettyResponseFuture<T, E> future = new JettyResponseFuture<>(this, request, jettyRequest, responseHandler, bytesWritten, stats);

        BufferingResponseListener listener = new BufferingResponseListener(future, Ints.saturatedCast(maxContentLength));

        try {
            jettyRequest.send(listener);
        }
        catch (RuntimeException e) {
            if (!(e instanceof RejectedExecutionException)) {
                e = new RejectedExecutionException(e);
            }
            // normally this is a rejected execution exception because the client has been closed
            future.failed(e);
        }
        return future;
    }

    private Request applyRequestFilters(Request request)
    {
        for (HttpRequestFilter requestFilter : requestFilters) {
            request = requestFilter.filterRequest(request);
        }
        return request;
    }

    private HttpRequest buildJettyRequest(Request finalRequest, AtomicLong bytesWritten)
    {
        HttpRequest jettyRequest = (HttpRequest) httpClient.newRequest(finalRequest.getUri());

        JettyRequestListener listener = new JettyRequestListener(finalRequest.getUri());
        jettyRequest.onRequestBegin(request -> listener.onRequestBegin());
        jettyRequest.onRequestSuccess(request -> listener.onRequestEnd());
        jettyRequest.onResponseBegin(response -> listener.onResponseBegin());
        jettyRequest.onComplete(result -> listener.onFinish());
        jettyRequest.attribute(PLATFORM_STATS_KEY, listener);

        jettyRequest.method(finalRequest.getMethod());

        jettyRequest.headers(headers -> finalRequest.getHeaders().forEach(headers::add));

        BodySource bodySource = finalRequest.getBodySource();
        if (bodySource != null) {
            if (bodySource instanceof StaticBodyGenerator) {
                StaticBodyGenerator staticBodyGenerator = (StaticBodyGenerator) bodySource;
                jettyRequest.body(new BytesRequestContent(staticBodyGenerator.getBody()));
                bytesWritten.addAndGet(staticBodyGenerator.getBody().length);
            }
            else if (bodySource instanceof InputStreamBodySource) {
                jettyRequest.body(new InputStreamBodySourceContentProvider((InputStreamBodySource) bodySource, bytesWritten));
            }
            else if (bodySource instanceof DynamicBodySource) {
                jettyRequest.body(new DynamicBodySourceContentProvider((DynamicBodySource) bodySource, bytesWritten));
            }
            else {
                throw new IllegalArgumentException("Request has unsupported BodySource type");
            }
        }
        jettyRequest.followRedirects(finalRequest.isFollowRedirects());

        setPreserveAuthorization(jettyRequest, finalRequest.isPreserveAuthorizationOnRedirect());

        // timeouts
        if (requestTimeoutMillis != null) {
            jettyRequest.timeout(requestTimeoutMillis, MILLISECONDS);
        }
        jettyRequest.idleTimeout(idleTimeoutMillis, MILLISECONDS);

        return jettyRequest;
    }

    public List<HttpRequestFilter> getRequestFilters()
    {
        return requestFilters;
    }

    @Override
    @Managed
    @Flatten
    public RequestStats getStats()
    {
        return stats;
    }

    @Managed
    @Nested
    public CachedDistribution getActiveConnectionsPerDestination()
    {
        return activeConnectionsPerDestination;
    }

    @Managed
    @Nested
    public CachedDistribution getIdleConnectionsPerDestination()
    {
        return idleConnectionsPerDestination;
    }

    @Managed
    @Nested
    public CachedDistribution getQueuedRequestsPerDestination()
    {
        return queuedRequestsPerDestination;
    }

    @Managed
    @Nested
    public CachedDistribution getCurrentQueuedTime()
    {
        return currentQueuedTime;
    }

    @Managed
    @Nested
    public CachedDistribution getCurrentRequestTime()
    {
        return currentRequestTime;
    }

    @Managed
    @Nested
    public CachedDistribution getCurrentRequestSendTime()
    {
        return currentRequestSendTime;
    }

    @Managed
    @Nested
    public CachedDistribution getCurrentResponseWaitTime()
    {
        return currentResponseWaitTime;
    }

    @Managed
    @Nested
    public CachedDistribution getCurrentResponseProcessTime()
    {
        return currentResponseProcessTime;
    }

    @Managed
    public String dump()
    {
        return httpClient.dump();
    }

    @Managed
    public void dumpStdErr()
    {
        httpClient.dumpStdErr();
    }

    @Managed
    public String dumpAllDestinations()
    {
        return String.format("%s\t%s\t%s\t%s\t%s%n", "URI", "queued", "request", "wait", "response") +
                httpClient.getDestinations().stream()
                        .map(JettyHttpClient::dumpDestination)
                        .collect(Collectors.joining("\n"));
    }

    // todo this should be @Managed but operations with parameters are broken in jmx utils https://github.com/martint/jmxutils/issues/27
    @SuppressWarnings("UnusedDeclaration")
    public String dumpDestination(URI uri)
    {
        return httpClient.getDestinations().stream()
                .filter(destination -> Objects.equals(destination.getOrigin().getScheme(), uri.getScheme()))
                .filter(destination -> Objects.equals(destination.getOrigin().getAddress().getHost(), uri.getHost()))
                .filter(destination -> destination.getOrigin().getAddress().getPort() == uri.getPort())
                .findFirst()
                .map(JettyHttpClient::dumpDestination)
                .orElse(null);
    }

    private static String dumpDestination(Destination destination)
    {
        long now = System.nanoTime();
        return getRequestListenersForDestination(destination).stream()
                .map(listener -> dumpRequest(now, listener))
                .sorted()
                .collect(Collectors.joining("\n"));
    }

    static List<JettyRequestListener> getRequestListenersForDestination(Destination destination)
    {
        return getRequestForDestination(destination).stream()
                .map(request -> request.getAttributes().get(PLATFORM_STATS_KEY))
                .map(JettyRequestListener.class::cast)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static List<org.eclipse.jetty.client.Request> getRequestForDestination(Destination destination)
    {
        HttpDestination httpDestination = (HttpDestination) destination;
        Queue<HttpExchange> httpExchanges = httpDestination.getHttpExchanges();

        List<org.eclipse.jetty.client.Request> requests = httpExchanges.stream()
                .map(HttpExchange::getRequest)
                .collect(Collectors.toList());

        getActiveConnections((AbstractConnectionPool) httpDestination.getConnectionPool()).stream()
                .filter(HttpConnectionOverHTTP.class::isInstance)
                .map(HttpConnectionOverHTTP.class::cast)
                .map(connection -> connection.getHttpChannel().getHttpExchange())
                .filter(Objects::nonNull)
                .forEach(exchange -> requests.add(exchange.getRequest()));

        return requests.stream()
                .filter(Objects::nonNull)
                .collect(toImmutableList());
    }

    private static String dumpRequest(long now, JettyRequestListener listener)
    {
        long created = listener.getCreated();
        long requestStarted = listener.getRequestStarted();
        if (requestStarted == 0) {
            requestStarted = now;
        }
        long requestFinished = listener.getRequestFinished();
        if (requestFinished == 0) {
            requestFinished = now;
        }
        long responseStarted = listener.getResponseStarted();
        if (responseStarted == 0) {
            responseStarted = now;
        }
        long finished = listener.getResponseFinished();
        if (finished == 0) {
            finished = now;
        }
        return String.format("%s\t%.1f\t%.1f\t%.1f\t%.1f",
                listener.getUri(),
                nanosToMillis(requestStarted - created),
                nanosToMillis(requestFinished - requestStarted),
                nanosToMillis(responseStarted - requestFinished),
                nanosToMillis(finished - responseStarted));
    }

    private static double nanosToMillis(long nanos)
    {
        return new Duration(nanos, NANOSECONDS).getValue(MILLISECONDS);
    }

    @Override
    public void close()
    {
        // client must be destroyed before the pools or
        // you will create a several second busy wait loop
        closeQuietly(httpClient);
        closeQuietly((LifeCycle) httpClient.getExecutor());
        closeQuietly(httpClient.getScheduler());
    }

    @Override
    public boolean isClosed()
    {
        return !httpClient.isRunning();
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .addValue(name)
                .toString();
    }

    @SuppressWarnings("UnusedDeclaration")
    public StackTraceElement[] getCreationLocation()
    {
        return creationLocation.getStackTrace();
    }

    private static void closeQuietly(LifeCycle service)
    {
        try {
            if (service != null) {
                service.stop();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (Exception ignored) {
        }
    }

    private static String uniqueName()
    {
        return "anonymous" + NAME_COUNTER.incrementAndGet();
    }

    static void recordRequestComplete(RequestStats requestStats, Request request, long requestStart, long bytesWritten, JettyResponse response, long responseStart)
    {
        if (response == null) {
            return;
        }

        Duration responseProcessingTime = Duration.nanosSince(responseStart);
        Duration requestProcessingTime = new Duration(responseStart - requestStart, NANOSECONDS);

        requestStats.record(request.getMethod(),
                response.getStatusCode(),
                bytesWritten,
                response.getBytesRead(),
                requestProcessingTime,
                responseProcessingTime);
    }
}
