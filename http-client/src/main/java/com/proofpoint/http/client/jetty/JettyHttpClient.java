package com.proofpoint.http.client.jetty;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Ordering;
import com.google.common.io.Closeables;
import com.google.common.io.CountingInputStream;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.AbstractFuture;
import com.proofpoint.http.client.BodySource;
import com.proofpoint.http.client.DynamicBodySource;
import com.proofpoint.http.client.DynamicBodySource.Writer;
import com.proofpoint.http.client.GatheringByteArrayInputStream;
import com.proofpoint.http.client.HeaderName;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.HttpRequestFilter;
import com.proofpoint.http.client.InputStreamBodySource;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.ResponseTooLargeException;
import com.proofpoint.http.client.StaticBodyGenerator;
import com.proofpoint.log.Logger;
import com.proofpoint.stats.Distribution;
import com.proofpoint.tracetoken.TraceToken;
import com.proofpoint.tracetoken.TraceTokenScope;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.Duration;
import org.eclipse.jetty.client.AbstractConnectionPool;
import org.eclipse.jetty.client.DuplexConnectionPool;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.HttpDestination;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.PoolingHttpDestination;
import org.eclipse.jetty.client.Socks4Proxy;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Destination;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Response.Listener;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.client.http.HttpConnectionOverHTTP;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Sweeper;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerTraceToken;
import static com.proofpoint.units.DataSize.Unit.KILOBYTE;
import static com.proofpoint.units.DataSize.Unit.MEGABYTE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class JettyHttpClient
        implements com.proofpoint.http.client.HttpClient
{
    static {
        JettyLogging.setup();
    }

    private static final Logger log = Logger.get(JettyHttpClient.class);
    private static final String[] ENABLED_PROTOCOLS = {"TLSv1.1", "TLSv1.2"};
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

    private static final AtomicLong nameCounter = new AtomicLong();
    private static final String PLATFORM_STATS_KEY = "platform_stats";
    private static final long SWEEP_PERIOD_MILLIS = 5000;
    private static final int CLIENT_TRANSPORT_SELECTORS = 2;

    private final JettyIoPool anonymousPool;
    private final HttpClient httpClient;
    private final long maxContentLength;
    private final Long requestTimeoutMillis;
    private final long idleTimeoutMillis;
    private final RequestStats stats = new RequestStats();
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
        this(new HttpClientConfig(), ImmutableList.of());
    }

    public JettyHttpClient(HttpClientConfig config)
    {
        this(config, ImmutableList.of());
    }

    public JettyHttpClient(HttpClientConfig config, Iterable<? extends HttpRequestFilter> requestFilters)
    {
        this(config, Optional.empty(), requestFilters);
    }

    public JettyHttpClient(HttpClientConfig config, JettyIoPool jettyIoPool, Iterable<? extends HttpRequestFilter> requestFilters)
    {
        this(config, Optional.of(jettyIoPool), requestFilters);
    }

    private JettyHttpClient(HttpClientConfig config, Optional<JettyIoPool> jettyIoPool, Iterable<? extends HttpRequestFilter> requestFilters)
    {
        requireNonNull(config, "config is null");
        requireNonNull(jettyIoPool, "jettyIoPool is null");
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

        SslContextFactory sslContextFactory = new SslContextFactory();
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

        HttpClientTransport transport;
        if (config.isHttp2Enabled()) {
            HTTP2Client client = new HTTP2Client();
            client.setInitialSessionRecvWindow(toIntExact(config.getHttp2InitialSessionReceiveWindowSize().toBytes()));
            client.setInitialStreamRecvWindow(toIntExact(config.getHttp2InitialStreamReceiveWindowSize().toBytes()));
            client.setInputBufferSize(toIntExact(config.getHttp2InputBufferSize().toBytes()));
            client.setSelectors(CLIENT_TRANSPORT_SELECTORS);
            transport = new HttpClientTransportOverHTTP2(client);
        }
        else {
            transport = new HttpClientTransportOverHTTP(CLIENT_TRANSPORT_SELECTORS);
        }

        httpClient = new HttpClient(transport, sslContextFactory);

        httpClient.setMaxRequestsQueuedPerDestination(config.getMaxRequestsQueuedPerDestination());
        httpClient.setMaxConnectionsPerDestination(config.getMaxConnectionsPerServer());

        // disable cookies
        httpClient.setCookieStore(new HttpCookieStore.Empty());

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
            httpClient.getProxyConfiguration().getProxies().add(new Socks4Proxy(socksProxy.getHost(), socksProxy.getPortOrDefault(1080)));
        }

        JettyIoPool pool;
        if (jettyIoPool.isPresent()) {
            pool = jettyIoPool.get();
            anonymousPool = null;
        }
        else {
            pool = new JettyIoPool("anonymous" + nameCounter.incrementAndGet(), new JettyIoPoolConfig());
            anonymousPool = pool;
        }

        name = pool.getName();
        this.httpClient.setExecutor(pool.getExecutor());
        this.httpClient.setByteBufferPool(pool.getByteBufferPool());
        this.httpClient.setScheduler(pool.getScheduler());

        // Jetty client connections can sometimes get stuck while closing which reduces
        // the available connections.  The Jetty Sweeper periodically scans the active
        // connection pool looking for connections in the closed state, and if a connection
        // is observed in the closed state multiple times, it logs, and destroys the connection.
        httpClient.addBean(new Sweeper(pool.getScheduler(), SWEEP_PERIOD_MILLIS), true);

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
                 (distribution, connectionPool) -> distribution.add(connectionPool.getActiveConnections().size()));

         this.idleConnectionsPerDestination = new ConnectionPoolDistribution(httpClient,
                 (distribution, connectionPool) -> distribution.add(connectionPool.getIdleConnections().size()));

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
            else if ((cause instanceof NoClassDefFoundError) && cause.getMessage().endsWith("ALPNClientConnection")) {
                return responseHandler.handleException(request, new RuntimeException("HTTPS cannot be used when HTTP/2 is enabled", cause));
            }
            else {
                return responseHandler.handleException(request, new RuntimeException(cause));
            }
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

    private void maybeLogJettyState()
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

        JettyResponseFuture<T, E> future = new JettyResponseFuture<>(request, jettyRequest, responseHandler, bytesWritten, stats);

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

        // jetty client always adds the user agent header
        // todo should there be a default?
        jettyRequest.getHeaders().remove(HttpHeader.USER_AGENT);

        jettyRequest.method(finalRequest.getMethod());

        for (Entry<String, String> entry : finalRequest.getHeaders().entries()) {
            jettyRequest.header(entry.getKey(), entry.getValue());
        }

        BodySource bodySource = finalRequest.getBodySource();
        if (bodySource != null) {
            if (bodySource instanceof StaticBodyGenerator) {
                StaticBodyGenerator staticBodyGenerator = (StaticBodyGenerator) bodySource;
                jettyRequest.content(new BytesContentProvider(staticBodyGenerator.getBody()));
                bytesWritten.addAndGet(staticBodyGenerator.getBody().length);
            }
            else if (bodySource instanceof InputStreamBodySource) {
                jettyRequest.content(new InputStreamBodySourceContentProvider((InputStreamBodySource) bodySource, bytesWritten));
            }
            else if (bodySource instanceof DynamicBodySource) {
                jettyRequest.content(new DynamicBodySourceContentProvider((DynamicBodySource) bodySource, bytesWritten));
            }
            else {
                throw new IllegalArgumentException("Request has unsupported BodySource type");
            }
        }
        jettyRequest.followRedirects(finalRequest.isFollowRedirects());

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
        Destination destination = httpClient.getDestination(uri.getScheme(), uri.getHost(), uri.getPort());
        if (destination == null) {
            return null;
        }

        return dumpDestination(destination);
    }

    private static String dumpDestination(Destination destination)
    {
        long now = System.nanoTime();
        return getRequestListenersForDestination(destination).stream()
                .map(request -> dumpRequest(now, request))
                .sorted()
                .collect(Collectors.joining("\n"));
    }

    private static List<JettyRequestListener> getRequestListenersForDestination(Destination destination)
    {
        return getRequestForDestination(destination).stream()
                .map(request -> request.getAttributes().get(PLATFORM_STATS_KEY))
                .map(JettyRequestListener.class::cast)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static List<org.eclipse.jetty.client.api.Request> getRequestForDestination(Destination destination)
    {
        PoolingHttpDestination poolingHttpDestination = (PoolingHttpDestination) destination;
        Queue<HttpExchange> httpExchanges = poolingHttpDestination.getHttpExchanges();

        List<org.eclipse.jetty.client.api.Request> requests = httpExchanges.stream()
                .map(HttpExchange::getRequest)
                .collect(Collectors.toList());

        ((DuplexConnectionPool) poolingHttpDestination.getConnectionPool()).getActiveConnections().stream()
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
        try {
            httpClient.stop();
        }
        catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        catch (Exception ignored) {
        }
        if (anonymousPool != null) {
            anonymousPool.close();
        }
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

    private static class JettyResponse
            implements com.proofpoint.http.client.Response
    {
        private final Response response;
        private final CountingInputStream inputStream;
        private final ListMultimap<HeaderName, String> headers;

        JettyResponse(Response response, InputStream inputStream)
        {
            this.response = response;
            this.inputStream = new CountingInputStream(inputStream);
            this.headers = toHeadersMap(response.getHeaders());
        }

        @Override
        public int getStatusCode()
        {
            return response.getStatus();
        }

        @Override
        public String getStatusMessage()
        {
            return response.getReason();
        }

        @Override
        public ListMultimap<HeaderName, String> getHeaders()
        {
            return headers;
        }

        @Override
        public long getBytesRead()
        {
            return inputStream.getCount();
        }

        @Override
        public InputStream getInputStream()
        {
            return inputStream;
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("statusCode", getStatusCode())
                    .add("statusMessage", getStatusMessage())
                    .add("headers", getHeaders())
                    .toString();
        }

        private static ListMultimap<HeaderName, String> toHeadersMap(HttpFields headers)
        {
            ImmutableListMultimap.Builder<HeaderName, String> builder = ImmutableListMultimap.builder();
            for (String name : headers.getFieldNamesCollection()) {
                for (String value : headers.getValuesList(name)) {
                    builder.put(HeaderName.of(name), value);
                }
            }
            return builder.build();
        }
    }

    enum JettyAsyncHttpState
    {
        WAITING_FOR_CONNECTION,
        SENDING_REQUEST,
        WAITING_FOR_RESPONSE,
        PROCESSING_RESPONSE,
        DONE,
        FAILED,
        CANCELED
    }

    private class JettyResponseFuture<T, E extends Exception>
            extends AbstractFuture<T>
            implements HttpResponseFuture<T>
    {
        private final long requestStart = System.nanoTime();
        private final AtomicReference<JettyAsyncHttpState> state = new AtomicReference<>(JettyAsyncHttpState.WAITING_FOR_CONNECTION);
        private final Request request;
        private final org.eclipse.jetty.client.api.Request jettyRequest;
        private final ResponseHandler<T, E> responseHandler;
        private final AtomicLong bytesWritten;
        private final RequestStats stats;
        private final TraceToken traceToken;

        JettyResponseFuture(Request request, org.eclipse.jetty.client.api.Request jettyRequest, ResponseHandler<T, E> responseHandler, AtomicLong bytesWritten, RequestStats stats)
        {
            this.request = request;
            this.jettyRequest = jettyRequest;
            this.responseHandler = responseHandler;
            this.bytesWritten = bytesWritten;
            this.stats = stats;
            traceToken = getCurrentTraceToken();
        }

        @Override
        public String getState()
        {
            return state.get().toString();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            try {
                state.set(JettyAsyncHttpState.CANCELED);
                jettyRequest.abort(new CancellationException());
                return super.cancel(mayInterruptIfRunning);
            }
            catch (Throwable e) {
                try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
                    setException(e);
                }
                return true;
            }
        }

        protected void completed(Response response, InputStream content)
        {
            if (state.get() == JettyAsyncHttpState.CANCELED) {
                return;
            }

            try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
                T value;
                try {
                    value = processResponse(response, content);
                }
                catch (Throwable e) {
                    // this will be an instance of E from the response handler or an Error
                    storeException(e);
                    return;
                }
                state.set(JettyAsyncHttpState.DONE);
                set(value);
            }
        }

        private T processResponse(Response response, InputStream content)
                throws E
        {
            // this time will not include the data fetching portion of the response,
            // since the response is fully cached in memory at this point
            long responseStart = System.nanoTime();

            state.set(JettyAsyncHttpState.PROCESSING_RESPONSE);
            JettyResponse jettyResponse = null;
            T value;
            try {
                jettyResponse = new JettyResponse(response, content);
                value = responseHandler.handle(request, jettyResponse);
            }
            finally {
                recordRequestComplete(stats, request, requestStart, bytesWritten.get(), jettyResponse, responseStart);
            }
            return value;
        }

        protected void failed(Throwable throwable)
        {
            if (state.get() == JettyAsyncHttpState.CANCELED) {
                return;
            }
            try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
                // give handler a chance to rewrite the exception or return a value instead
                if (throwable instanceof Exception) {
                    try {
                        if (throwable instanceof RejectedExecutionException) {
                            maybeLogJettyState();
                        }
                        T value = responseHandler.handleException(request, (Exception) throwable);
                        // handler returned a value, store it in the future
                        state.set(JettyAsyncHttpState.DONE);
                        set(value);
                        return;
                    }
                    catch (Throwable newThrowable) {
                        throwable = newThrowable;
                    }
                }

                // at this point "throwable" will either be an instance of E
                // from the response handler or not an instance of Exception
                storeException(throwable);
            }
        }

        private void storeException(Throwable throwable)
        {
            if (throwable instanceof CancellationException) {
                state.set(JettyAsyncHttpState.CANCELED);
            }
            else {
                state.set(JettyAsyncHttpState.FAILED);
            }
            if (throwable == null) {
                throwable = new Throwable("Throwable is null");
                log.error(throwable, "Something is broken");
            }

            setException(throwable);
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("requestStart", requestStart)
                    .add("state", state)
                    .add("request", request)
                    .toString();
        }
    }

    private static void recordRequestComplete(RequestStats requestStats, Request request, long requestStart, long bytesWritten, JettyResponse response, long responseStart)
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

    private static class InputStreamBodySourceContentProvider extends InputStreamContentProvider
    {
        private final long length;

        public InputStreamBodySourceContentProvider(InputStreamBodySource inputStreamBodySource, AtomicLong bytesWritten)
        {
            super(new BodySourceInputStream(inputStreamBodySource, bytesWritten), inputStreamBodySource.getBufferSize(), false);
            length = inputStreamBodySource.getLength();
        }

        @Override
        public long getLength()
        {
            return length;
        }
    }

    private static class BodySourceInputStream extends InputStream
    {
        private final InputStream delegate;
        private final AtomicLong bytesWritten;

        BodySourceInputStream(InputStreamBodySource bodySource, AtomicLong bytesWritten)
        {
            delegate = bodySource.getInputStream();
            this.bytesWritten = bytesWritten;
        }

        @Override
        public int read()
                throws IOException
        {
            // We guarantee we don't call the int read() method of the delegate.
            throw new UnsupportedOperationException();
        }

        @Override
        public int read(byte[] b)
                throws IOException
        {
            int read = delegate.read(b);
            if (read > 0) {
                bytesWritten.addAndGet(read);
            }
            return read;
        }

        @Override
        public int read(byte[] b, int off, int len)
                throws IOException
        {
            int read = delegate.read(b, off, len);
            if (read > 0) {
                bytesWritten.addAndGet(read);
            }
            return read;
        }

        @Override
        public long skip(long n)
                throws IOException
        {
            return delegate.skip(n);
        }

        @Override
        public int available()
                throws IOException
        {
            return delegate.available();
        }

        @Override
        public void close()
        {
            // We guarantee we don't call this
            throw new UnsupportedOperationException();
        }

        @Override
        public void mark(int readlimit)
        {
            // We guarantee we don't call this
            throw new UnsupportedOperationException();
        }

        @Override
        public void reset()
                throws IOException
        {
            // We guarantee we don't call this
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markSupported()
        {
            return false;
        }
    }

    private static class DynamicBodySourceContentProvider
            implements ContentProvider
    {
        private static final ByteBuffer INITIAL = ByteBuffer.allocate(0);
        private static final ByteBuffer DONE = ByteBuffer.allocate(0);

        private final DynamicBodySource dynamicBodySource;
        private final AtomicLong bytesWritten;
        private final TraceToken traceToken;

        DynamicBodySourceContentProvider(DynamicBodySource dynamicBodySource, AtomicLong bytesWritten)
        {
            this.dynamicBodySource = dynamicBodySource;
            this.bytesWritten = bytesWritten;
            traceToken = getCurrentTraceToken();
        }

        @Override
        public long getLength()
        {
            return dynamicBodySource.getLength();
        }

        @Override
        public Iterator<ByteBuffer> iterator()
        {
            final Queue<ByteBuffer> chunks = new BlockingArrayQueue<>(4, 64);

            Writer writer;
            try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
                writer = dynamicBodySource.start(new DynamicBodySourceOutputStream(chunks));
            }
            catch (Exception e) {
                throwIfUnchecked(e);
                throw new RuntimeException(e);
            }

            return new DynamicBodySourceIterator(chunks, writer, bytesWritten, traceToken);
        }

        private static class DynamicBodySourceOutputStream
                extends OutputStream
        {
            private static int BUFFER_SIZE = 4096;
            private ByteBuffer lastChunk = INITIAL;
            private final Queue<ByteBuffer> chunks;

            private DynamicBodySourceOutputStream(Queue<ByteBuffer> chunks)
            {
                this.chunks = chunks;
            }

            @Override
            public void write(int b)
            {
                if (!chunks.isEmpty() && lastChunk.hasRemaining()) {
                    lastChunk.put((byte)b);
                }
                else {
                    lastChunk = ByteBuffer.allocate(BUFFER_SIZE);
                    lastChunk.put((byte)b);
                    chunks.add(lastChunk);
                }
            }

            @Override
            public void write(byte[] b, int off, int len)
            {
                if (!chunks.isEmpty() && lastChunk.hasRemaining()) {
                    int toCopy = min(len, lastChunk.remaining());
                    lastChunk.put(b, off, toCopy);
                    if (toCopy == len) {
                        return;
                    }
                    off += toCopy;
                    len -= toCopy;
                }

                lastChunk = ByteBuffer.allocate(max(BUFFER_SIZE, len));
                lastChunk.put(b, off, len);
                chunks.add(lastChunk);

            }

            @Override
            public void close()
            {
                lastChunk = DONE;
                chunks.add(DONE);
            }
        }

        private static class DynamicBodySourceIterator extends AbstractIterator<ByteBuffer>
                implements Closeable
        {
            private final Queue<ByteBuffer> chunks;
            private final Writer writer;
            private final AtomicLong bytesWritten;
            private final TraceToken traceToken;

            @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
            DynamicBodySourceIterator(Queue<ByteBuffer> chunks, Writer writer, AtomicLong bytesWritten, TraceToken traceToken)
            {
                this.chunks = chunks;
                this.writer = writer;
                this.bytesWritten = bytesWritten;
                this.traceToken = traceToken;
            }

            @Override
            @SuppressWarnings("ReferenceEquality") // Reference equality to DONE is intentional
            protected ByteBuffer computeNext()
            {
                ByteBuffer chunk = chunks.poll();
                if (chunk == null) {
                    try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
                        while (chunk == null) {
                            try {
                                writer.write();
                            }
                            catch (Exception e) {
                                throwIfUnchecked(e);
                                throw new RuntimeException(e);
                            }
                            chunk = chunks.poll();
                        }
                    }
                }

                if (chunk == DONE) {
                    return endOfData();
                }
                bytesWritten.addAndGet(chunk.position());
                ((Buffer)chunk).flip();
                return chunk;
            }

            @Override
            public void close()
            {
                if (writer instanceof AutoCloseable) {
                    try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
                        ((AutoCloseable)writer).close();
                    }
                    catch (Exception e) {
                        throwIfUnchecked(e);
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @ThreadSafe
    private static class BufferingResponseListener
            extends Listener.Adapter
    {
        private static final long BUFFER_MAX_BYTES = new DataSize(1, MEGABYTE).toBytes();
        private static final long BUFFER_MIN_BYTES = new DataSize(1, KILOBYTE).toBytes();
        private final JettyResponseFuture<?, ?> future;
        private final int maxLength;

        @GuardedBy("this")
        private byte[] currentBuffer = new byte[0];
        @GuardedBy("this")
        private int currentBufferPosition;
        @GuardedBy("this")
        private List<byte[]> buffers = new ArrayList<>();
        @GuardedBy("this")
        private long size;

        BufferingResponseListener(JettyResponseFuture<?, ?> future, int maxLength)
        {
            this.future = requireNonNull(future, "future is null");
            checkArgument(maxLength > 0, "maxLength must be greater than zero");
            this.maxLength = maxLength;
        }

        @Override
        public synchronized void onHeaders(Response response)
        {
            long length = response.getHeaders().getLongField(HttpHeader.CONTENT_LENGTH.asString());
            if (length > maxLength) {
                response.abort(new ResponseTooLargeException());
            }
        }

        @Override
        public synchronized void onContent(Response response, ByteBuffer content)
        {
            int length = content.remaining();
            size += length;
            if (size > maxLength) {
                response.abort(new ResponseTooLargeException());
                return;
            }

            while (length > 0) {
                if (currentBufferPosition >= currentBuffer.length) {
                    allocateCurrentBuffer();
                }
                int readLength = min(length, currentBuffer.length - currentBufferPosition);
                content.get(currentBuffer, currentBufferPosition, readLength);
                length -= readLength;
                currentBufferPosition += readLength;
            }
        }

        @Override
        public synchronized void onComplete(Result result)
        {
            Throwable throwable = result.getFailure();
            if (throwable != null) {
                future.failed(throwable);
            }
            else {
                currentBuffer = new byte[0];
                currentBufferPosition = 0;
                future.completed(result.getResponse(), new GatheringByteArrayInputStream(buffers, size));
                buffers = new ArrayList<>();
                size = 0;
            }
        }

        private synchronized void allocateCurrentBuffer()
        {
            checkState(currentBufferPosition >= currentBuffer.length, "there is still remaining space in currentBuffer");

            currentBuffer = new byte[(int) min(BUFFER_MAX_BYTES, max(2 * currentBuffer.length, BUFFER_MIN_BYTES))];
            buffers.add(currentBuffer);
            currentBufferPosition = 0;
        }
    }

    /*
     * This class is needed because jmxutils only fetches a nested instance object once and holds on to it forever.
     * todo remove this when https://github.com/martint/jmxutils/issues/26 is implemented
     */
    @ThreadSafe
    public static class CachedDistribution
    {
        private final Supplier<Distribution> distributionSupplier;

        @GuardedBy("this")
        private Distribution distribution;
        @GuardedBy("this")
        private long lastUpdate = System.nanoTime();

        public CachedDistribution(Supplier<Distribution> distributionSupplier)
        {
            this.distributionSupplier = distributionSupplier;
        }

        public synchronized Distribution getDistribution()
        {
            // refresh stats only once a second
            if (NANOSECONDS.toMillis(System.nanoTime() - lastUpdate) > 1000) {
                this.distribution = distributionSupplier.get();
                this.lastUpdate = System.nanoTime();
            }
            return distribution;
        }

        @Managed
        public double getMaxError()
        {
            return getDistribution().getMaxError();
        }

        @Managed
        public double getCount()
        {
            return getDistribution().getCount();
        }

        @Managed
        public double getTotal()
        {
            return getDistribution().getTotal();
        }

        @Managed
        public long getP01()
        {
            return getDistribution().getP01();
        }

        @Managed
        public long getP05()
        {
            return getDistribution().getP05();
        }

        @Managed
        public long getP10()
        {
            return getDistribution().getP10();
        }

        @Managed
        public long getP25()
        {
            return getDistribution().getP25();
        }

        @Managed
        public long getP50()
        {
            return getDistribution().getP50();
        }

        @Managed
        public long getP75()
        {
            return getDistribution().getP75();
        }

        @Managed
        public long getP90()
        {
            return getDistribution().getP90();
        }

        @Managed
        public long getP95()
        {
            return getDistribution().getP95();
        }

        @Managed
        public long getP99()
        {
            return getDistribution().getP99();
        }

        @Managed
        public long getMin()
        {
            return getDistribution().getMin();
        }

        @Managed
        public long getMax()
        {
            return getDistribution().getMax();
        }

        @Managed
        public Map<Double, Long> getPercentiles()
        {
            return getDistribution().getPercentiles();
        }
    }

    private static class JettyRequestListener
    {
        enum State
        {
            CREATED, SENDING_REQUEST, AWAITING_RESPONSE, READING_RESPONSE, FINISHED
        }

        private final AtomicReference<State> state = new AtomicReference<>(State.CREATED);

        private final URI uri;
        private final long created = System.nanoTime();
        private final AtomicLong requestStarted = new AtomicLong();
        private final AtomicLong requestFinished = new AtomicLong();
        private final AtomicLong responseStarted = new AtomicLong();
        private final AtomicLong responseFinished = new AtomicLong();

        JettyRequestListener(URI uri)
        {
            this.uri = uri;
        }

        public URI getUri()
        {
            return uri;
        }

        public State getState()
        {
            return state.get();
        }

        public long getCreated()
        {
            return created;
        }

        public long getRequestStarted()
        {
            return requestStarted.get();
        }

        public long getRequestFinished()
        {
            return requestFinished.get();
        }

        public long getResponseStarted()
        {
            return responseStarted.get();
        }

        public long getResponseFinished()
        {
            return responseFinished.get();
        }

        public void onRequestBegin()
        {
            changeState(State.SENDING_REQUEST);

            long now = System.nanoTime();
            requestStarted.compareAndSet(0, now);
        }

        public void onRequestEnd()
        {
            changeState(State.AWAITING_RESPONSE);

            long now = System.nanoTime();
            requestStarted.compareAndSet(0, now);
            requestFinished.compareAndSet(0, now);
        }

        private void onResponseBegin()
        {
            changeState(State.READING_RESPONSE);

            long now = System.nanoTime();
            requestStarted.compareAndSet(0, now);
            requestFinished.compareAndSet(0, now);
            responseStarted.compareAndSet(0, now);
        }

        private void onFinish()
        {
            changeState(State.FINISHED);

            long now = System.nanoTime();
            requestStarted.compareAndSet(0, now);
            requestFinished.compareAndSet(0, now);
            responseStarted.compareAndSet(0, now);
            responseFinished.compareAndSet(0, now);
        }

        private synchronized void changeState(State newState)
        {
            if (state.get().ordinal() < newState.ordinal()) {
                state.set(newState);
            }
        }
    }

    private static class ConnectionPoolDistribution
            extends CachedDistribution
    {
        interface Processor
        {
            void process(Distribution distribution, DuplexConnectionPool pool);
        }

        ConnectionPoolDistribution(HttpClient httpClient, Processor processor)
        {
            super(() -> {
                Distribution distribution = new Distribution();
                httpClient.getDestinations().stream()
                        .filter(PoolingHttpDestination.class::isInstance)
                        .map(PoolingHttpDestination.class::cast)
                        .map(PoolingHttpDestination::getConnectionPool)
                        .filter(Objects::nonNull)
                        .map(DuplexConnectionPool.class::cast)
                        .forEach(pool -> processor.process(distribution, pool));
                return distribution;
            });
        }
    }

    private static class DestinationDistribution
            extends CachedDistribution
    {
        interface Processor
        {
            void process(Distribution distribution, PoolingHttpDestination destination);
        }

        DestinationDistribution(HttpClient httpClient, Processor processor)
        {
            super(() -> {
                Distribution distribution = new Distribution();
                httpClient.getDestinations().stream()
                        .filter(PoolingHttpDestination.class::isInstance)
                        .map(PoolingHttpDestination.class::cast)
                        .forEach(destination -> processor.process(distribution, destination));
                return distribution;
            });
        }
    }

    private static class RequestDistribution
            extends CachedDistribution
    {
        interface Processor
        {
            void process(Distribution distribution, JettyRequestListener listener, long now);
        }

        RequestDistribution(HttpClient httpClient, Processor processor)
        {
            super(() -> {
                long now = System.nanoTime();
                Distribution distribution = new Distribution();
                httpClient.getDestinations().stream()
                        .filter(PoolingHttpDestination.class::isInstance)
                        .map(PoolingHttpDestination.class::cast)
                        .map(JettyHttpClient::getRequestListenersForDestination)
                        .flatMap(List::stream)
                        .forEach(listener -> processor.process(distribution, listener, now));
                return distribution;
            });
        }
    }
}
