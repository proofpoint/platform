package com.proofpoint.http.client;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.proofpoint.http.client.DynamicBodySource.Writer;
import com.proofpoint.http.client.HttpClient.HttpResponseFuture;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.log.Logging;
import com.proofpoint.testing.Assertions;
import com.proofpoint.testing.Closeables;
import com.proofpoint.tracetoken.TraceToken;
import com.proofpoint.units.Duration;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.base.Throwables.propagate;
import static com.google.common.base.Throwables.propagateIfPossible;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.net.HttpHeaders.ACCEPT_ENCODING;
import static com.proofpoint.concurrent.Threads.threadsNamed;
import static com.proofpoint.http.client.HttpUriBuilder.uriBuilderFrom;
import static com.proofpoint.http.client.Request.Builder.prepareDelete;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.Request.Builder.preparePost;
import static com.proofpoint.http.client.Request.Builder.preparePut;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.testing.Assertions.assertGreaterThan;
import static com.proofpoint.testing.Assertions.assertLessThan;
import static com.proofpoint.testing.Closeables.closeQuietly;
import static com.proofpoint.tracetoken.TraceTokenManager.createAndRegisterNewRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static com.proofpoint.units.Duration.nanosSince;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public abstract class AbstractHttpClientTest
{
    protected EchoServlet servlet;
    protected Server server;
    protected URI baseURI;
    private String scheme = "http";
    private String host = "127.0.0.1";
    private String keystore = null;
    protected RequestStats stats;
    private SslContextFactory sslContextFactory;

    protected AbstractHttpClientTest()
    {
    }

    protected AbstractHttpClientTest(String host, String keystore)
    {
        scheme = "https";
        this.host = host;
        this.keystore = keystore;
    }

    protected abstract HttpClientConfig createClientConfig();

    private void executeExceptionRequest(HttpClientConfig config, Request request)
            throws Exception
    {
        try {
            executeRequest(config, request, new CaptureExceptionResponseHandler());
            fail("expected exception");
        }
        catch (CapturedException e) {
            propagateIfPossible(e.getCause(), Exception.class);
            throw new RuntimeException(e.getCause());
        }
    }

    public abstract <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
            throws Exception;

    public final <T, E extends Exception> T executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        try (ClientTester clientTester = clientTester(config)) {
            return clientTester.executeRequest(request, responseHandler);
        }
    }

    public abstract ClientTester clientTester(HttpClientConfig config);

    public interface ClientTester
            extends Closeable
    {
        <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
                throws Exception;
    }

    @BeforeSuite
    public void setupSuite()
    {
        Logging.initialize();
    }

    @BeforeMethod
    public void abstractSetup()
            throws Exception
    {
        servlet = new EchoServlet();

        Server server = new Server();

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendServerVersion(false);
        httpConfiguration.setSendXPoweredBy(false);

        ServerConnector connector;
        if (keystore != null) {
            httpConfiguration.addCustomizer(new SecureRequestCustomizer());

            sslContextFactory = new SslContextFactory(keystore);
            sslContextFactory.setKeyStorePassword("changeit");
            SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, "http/1.1");

            connector = new ServerConnector(server, sslConnectionFactory, new HttpConnectionFactory(httpConfiguration));
        }
        else {
            HttpConnectionFactory http1 = new HttpConnectionFactory(httpConfiguration);
            HTTP2CServerConnectionFactory http2c = new HTTP2CServerConnectionFactory(httpConfiguration);

            connector = new ServerConnector(server, http1, http2c);
        }

        connector.setIdleTimeout(30000);
        connector.setName(scheme);

        server.addConnector(connector);

        ServletHolder servletHolder = new ServletHolder(servlet);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.addServlet(servletHolder, "/*");
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(context);
        server.setHandler(handlers);

        this.server = server;
        server.start();

        baseURI = new URI(scheme, null, host, connector.getLocalPort(), null, null, null);
    }

    @AfterMethod(alwaysRun = true)
    public void abstractTeardown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }
        Logging.resetLogTesters();
    }

    @Test(enabled = false, description = "This takes over a minute to run")
    public void test100kGets()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere?query");
        Request request = prepareGet()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        for (int i = 0; i < 100_000; i++) {
            try {
                int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
                assertEquals(statusCode, 200);
            }
            catch (Exception e) {
                throw new Exception("Error on request " + i, e);
            }
        }
    }

    // Disabled because it is too flaky
    @Test(timeOut = 4000, enabled = false)
    public void testConnectTimeout()
            throws Exception
    {
        try (BackloggedServer server = new BackloggedServer()) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, MILLISECONDS));
            config.setIdleTimeout(new Duration(2, SECONDS));

            Request request = prepareGet()
                    .setUri(new URI(scheme, null, host, server.getPort(), "/", null, null))
                    .build();

            long start = System.nanoTime();
            try {
                executeRequest(config, request, new CaptureExceptionResponseHandler());
                fail("expected exception");
            }
            catch (CapturedException e) {
                Throwable t = e.getCause();
                if (!isConnectTimeout(t)) {
                    fail(format("unexpected exception: [%s]", getStackTraceAsString(t)));
                }
                assertLessThan(nanosSince(start), new Duration(300, MILLISECONDS));
            }
        }
    }

    @Test(expectedExceptions = {ConnectException.class, SocketTimeoutException.class})
    public void testConnectionRefused()
            throws Exception
    {
        int port = findUnusedPort();

        HttpClientConfig config = createClientConfig();
        config.setConnectTimeout(new Duration(5, MILLISECONDS));

        Request request = prepareGet()
                .setUri(new URI(scheme, null, host, port, "/", null, null))
                .build();

        executeExceptionRequest(config, request);
    }

    @Test
    public void testConnectionRefusedWithDefaultingResponseExceptionHandler()
            throws Exception
    {
        int port = findUnusedPort();

        HttpClientConfig config = createClientConfig();
        config.setConnectTimeout(new Duration(5, MILLISECONDS));

        Request request = prepareGet()
                .setUri(new URI(scheme, null, host, port, "/", null, null))
                .build();

        Object expected = new Object();
        assertEquals(executeRequest(config, request, new DefaultOnExceptionResponseHandler(expected)), expected);
    }


    @Test(expectedExceptions = {UnknownHostException.class, UnresolvedAddressException.class}, timeOut = 10000)
    public void testUnresolvableHost()
            throws Exception
    {
        String invalidHost = "nonexistent.invalid";
        assertUnknownHost(invalidHost);

        HttpClientConfig config = createClientConfig();
        config.setConnectTimeout(new Duration(5, SECONDS));

        Request request = prepareGet()
                .setUri(URI.create("http://" + invalidHost))
                .build();

        executeExceptionRequest(config, request);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadPort()
            throws Exception
    {
        HttpClientConfig config = createClientConfig();
        config.setConnectTimeout(new Duration(5, MILLISECONDS));

        Request request = prepareGet()
                .setUri(new URI(scheme, null, host, 70_000, "/", null, null))
                .build();

        executeExceptionRequest(config, request);
    }

    @Test
    public void testDeleteMethod()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = prepareDelete()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "DELETE");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
    }

    @Test
    public void testErrorResponseBody()
            throws Exception
    {
        servlet.setResponseStatusCode(500);
        servlet.setResponseBody("body text");

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String body = executeRequest(request, createStringResponseHandler()).getBody();
        assertEquals(body, "body text");
    }

    @Test
    public void testGetMethod()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere?query");
        Request request = prepareGet()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "GET");
        if (servlet.getRequestUri().toString().endsWith("=")) {
            // todo jetty client rewrites the uri string for some reason
            assertEquals(servlet.getRequestUri(), new URI(uri + "="));
        }
        else {
            assertEquals(servlet.getRequestUri(), uri);
        }
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
    }

    @Test
    public void testResponseHeadersCaseInsensitive()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = prepareGet()
                .setUri(uri)
                .build();

        Response response = executeRequest(request, new PassThroughResponseHandler());

        assertNotNull(response.getHeader("date"));
        assertNotNull(response.getHeader("DATE"));

        assertEquals(response.getHeaders("date").size(), 1);
        assertEquals(response.getHeaders("DATE").size(), 1);
    }

    @Test
    public void testKeepAlive()
            throws Exception
    {
        URI uri = URI.create(baseURI.toASCIIString() + "/?remotePort=");
        Request request = prepareGet()
                .setUri(uri)
                .build();

        StatusResponse response1 = executeRequest(request, createStatusResponseHandler());
        Thread.sleep(1000);
        StatusResponse response2 = executeRequest(request, createStatusResponseHandler());
        Thread.sleep(1000);
        StatusResponse response3 = executeRequest(request, createStatusResponseHandler());

        assertNotNull(response1.getHeader("remotePort"));
        assertNotNull(response2.getHeader("remotePort"));
        assertNotNull(response3.getHeader("remotePort"));

        int port1 = Integer.parseInt(response1.getHeader("remotePort"));
        int port2 = Integer.parseInt(response2.getHeader("remotePort"));
        int port3 = Integer.parseInt(response3.getHeader("remotePort"));

        assertEquals(port2, port1);
        assertEquals(port3, port1);
        Assertions.assertBetweenInclusive(port1, 1024, 65535);
    }

    @Test
    public void testPostMethod()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = preparePost()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "POST");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
    }

    @Test
    public void testPutMethod()
            throws Exception
    {
        servlet.setResponseBody("response");
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = preparePut()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        int statusCode = executeRequest(request, createStringResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "PUT");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
        assertEquals(stats.getWrittenBytes().getAllTime().getTotal(), 0.0);
    }

    @Test
    public void testPutMethodWithStaticBodyGenerator()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        byte[] body = {1, 2, 5};
        Request request = preparePut()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .setBodyGenerator(StaticBodyGenerator.createStaticBodyGenerator(body))
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "PUT");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
        assertEquals(servlet.getRequestBytes(), body);
        assertEquals(stats.getWrittenBytes().getAllTime().getTotal(), 3.0);
    }

    @Test
    public void testPutMethodWithInputStreamBodySource()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = preparePut()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .setBodySource(new InputStreamBodySource(new InputStream()
                {
                    AtomicInteger invocation = new AtomicInteger(0);

                    @Override
                    public int read()
                            throws IOException
                    {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public int read(byte[] b)
                            throws IOException
                    {
                        switch (invocation.getAndIncrement()) {
                            case 0:
                                assertEquals(b.length, 8123);
                                b[0] = 1;
                                return 1;

                            case 1:
                                b[0] = 2;
                                b[1] = 5;
                                return 2;

                            case 2:
                                return -1;

                            default:
                                fail("unexpected invocation of write()");
                                return -1;
                        }
                    }
                }, 8123))
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "PUT");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
        assertEquals(servlet.getRequestBytes(), new byte[]{1, 2, 5});
        assertEquals(stats.getWrittenBytes().getAllTime().getTotal(), 3.0);
    }

    @Test
    public void testPutMethodWithInputStreamBodySourceContentLength()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = preparePut()
                .setUri(uri)
                .setBodySource(new InputStreamBodySource(new InputStream()
                {
                    AtomicInteger invocation = new AtomicInteger(0);

                    @Override
                    public int read()
                            throws IOException
                    {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public int read(byte[] b)
                            throws IOException
                    {
                        switch (invocation.getAndIncrement()) {
                            case 0:
                                assertEquals(b.length, 8123);
                                b[0] = 1;
                                return 1;

                            case 1:
                                b[0] = 2;
                                b[1] = 3;
                                return 2;

                            case 2:
                                return -1;

                            default:
                                fail("unexpected invocation of write()");
                                return -1;
                        }
                    }
                }, 8123) {
                    @Override
                    public long getLength()
                    {
                        return 3;
                    }
                })
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "PUT");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("Content-Length"), ImmutableList.of("3"));
        assertEquals(servlet.getRequestBytes(), new byte[]{1, 2, 3});
        assertEquals(stats.getWrittenBytes().getAllTime().getTotal(), 3.0);
    }

    @Test
    public void testPutMethodWithDynamicBodySource()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = preparePut()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .setBodySource((DynamicBodySource) out -> new Writer()
                {
                    AtomicInteger invocation = new AtomicInteger(0);

                    @Override
                    public void write()
                            throws Exception
                    {
                        switch (invocation.getAndIncrement()) {
                            case 0:
                                out.write(1);
                                break;

                            case 1:
                                byte[] bytes = {2, 5};
                                out.write(bytes);
                                bytes[0] = 9;
                                out.close();
                                break;

                            default:
                                fail("unexpected invocation of write()");
                        }
                    }
                })
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "PUT");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
        assertEquals(servlet.getRequestBytes(), new byte[]{1, 2, 5});
        assertEquals(stats.getWrittenBytes().getAllTime().getTotal(), 3.0);
    }

    @Test
    public void testPutMethodWithDynamicBodySourceEdgeCases()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        final AtomicBoolean closed = new AtomicBoolean(false);
        Request request = preparePut()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .setBodySource((DynamicBodySource) out -> {
                    out.write(1);
                    return new EdgeCaseTestWriter(out, closed);
                })
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "PUT");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
        assertEquals(servlet.getRequestBytes(), createByteArray(1, 15008));
        assertTrue(closed.get(), "Writer was closed");
        assertEquals(stats.getWrittenBytes().getAllTime().getTotal(), 15008.0);
    }

    private static class EdgeCaseTestWriter
            implements Writer, AutoCloseable
    {
        AtomicInteger invocation = new AtomicInteger(0);
        private final OutputStream out;
        private final AtomicBoolean closed;

        EdgeCaseTestWriter(OutputStream out, AtomicBoolean closed)
        {
            this.out = out;
            this.closed = closed;
        }

        @Override
        public void write()
                throws Exception
        {
            switch (invocation.getAndIncrement()) {
                case 0:
                    break;

                case 1:
                    byte[] bytes = {2, 3};
                    out.write(bytes);
                    bytes[0] = 9;
                    break;

                case 2:
                    out.write(createByteArray(4, 2));
                    out.write(6);
                    out.write(createByteArray(7, 2));
                    out.write(createByteArray(9, 5000));
                    byte[] lastArray = createByteArray(5009, 10_000);
                    out.write(lastArray);
                    lastArray[0] = 0;
                    lastArray[1] = 0;
                    break;

                case 3:
                    out.close();
                    break;

                default:
                    fail("unexpected invocation of write()");
            }
        }

        @Override
        public void close()
        {
            closed.set(true);
        }
    }

    private static byte[] createByteArray(int firstValue, int length)
    {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte)firstValue++;
        }
        return bytes;
    }

    @Test
    public void testPutMethodWithDynamicBodySourceContentLength()
            throws Exception
    {
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = preparePut()
                .setUri(uri)
                .setBodySource(new DynamicBodySource()
                {
                    @Override
                    public long getLength()
                    {
                        return 3;
                    }

                    @Override
                    public Writer start(OutputStream out)
                            throws Exception
                    {
                        return new Writer()
                        {
                            AtomicInteger invocation = new AtomicInteger(0);

                            @Override
                            public void write()
                                    throws Exception
                            {
                                switch (invocation.getAndIncrement()) {
                                    case 0:
                                        out.write(1);
                                        break;

                                    case 1:
                                        byte[] bytes = {2, 5};
                                        out.write(bytes);
                                        out.close();
                                        break;

                                    default:
                                        fail("unexpected invocation of write()");
                                }
                            }
                        };
                    }
                })
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "PUT");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("Content-Length"), ImmutableList.of("3"));
        assertEquals(servlet.getRequestBytes(), new byte[]{1, 2, 5});
        assertEquals(stats.getWrittenBytes().getAllTime().getTotal(), 3.0);
    }

    @Test
    public void testDynamicBodySourceSeesTraceToken()
            throws Exception
    {
        createAndRegisterNewRequestToken("somekey", "somevalue");
        TraceToken token = getCurrentTraceToken();
        AtomicReference<TraceToken> writeToken = new AtomicReference<>();
        CountDownLatch closeLatch = new CountDownLatch(1);
        AtomicReference<TraceToken> closeToken = new AtomicReference<>();
        URI uri = baseURI.resolve("/road/to/nowhere");
        Request request = preparePut()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .setBodySource((DynamicBodySource) out -> {
                    assertEquals(getCurrentTraceToken(), token);
                    return new TraceTokenTestWriter(out, writeToken, closeLatch, closeToken);
                })
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 200);
        assertEquals(servlet.getRequestMethod(), "PUT");
        assertEquals(servlet.getRequestUri(), uri);
        assertEquals(servlet.getRequestHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(servlet.getRequestHeaders("dupe"), ImmutableList.of("first", "second"));
        assertEquals(servlet.getRequestHeaders("x-custom-filter"), ImmutableList.of("custom value"));
        assertEquals(servlet.getRequestBytes(), new byte[] {});
        assertEquals(writeToken.get(), token);
        assertTrue(closeLatch.await(1, SECONDS));
        assertEquals(closeToken.get(), token);
    }

    private static class TraceTokenTestWriter
            implements Writer, AutoCloseable
    {
        private final OutputStream out;
        private final AtomicReference<TraceToken> writeToken;
        private final CountDownLatch closeLatch;
        private final AtomicReference<TraceToken> closeToken;

        TraceTokenTestWriter(OutputStream out, AtomicReference<TraceToken> writeToken, CountDownLatch closeLatch, AtomicReference<TraceToken> closeToken)
        {
            this.out = out;
            this.writeToken = writeToken;
            this.closeLatch = closeLatch;
            this.closeToken = closeToken;
        }

        @Override
        public void write()
                throws Exception
        {
            writeToken.set(getCurrentTraceToken());
            out.close();
        }

        @Override
        public void close()
        {
            closeToken.set(getCurrentTraceToken());
            closeLatch.countDown();
        }
    }

    @Test
    public void testResponseHandlerExceptionSeesTraceToken()
            throws Exception
    {
        createAndRegisterNewRequestToken("somekey", "somevalue");
        TraceToken token = getCurrentTraceToken();
        int port = findUnusedPort();

        HttpClientConfig config = createClientConfig();
        config.setConnectTimeout(new Duration(5, MILLISECONDS));

        Request request = prepareGet()
                .setUri(new URI(scheme, null, host, port, "/", null, null))
                .build();

        executeRequest(request, new ResponseHandler<Void, RuntimeException>()
        {
            @Override
            public Void handleException(Request request, Exception exception)
                    throws RuntimeException
            {
                assertEquals(getCurrentTraceToken(), token);
                return null;
            }

            @Override
            public Void handle(Request request, Response response)
                    throws RuntimeException
            {
                fail("unexpected response");
                return null;
            }
        });
    }

    @Test
    public void testResponseHandlerResponseSeesTraceToken()
            throws Exception
    {
        createAndRegisterNewRequestToken("somekey", "somevalue");
        TraceToken token = getCurrentTraceToken();
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        executeRequest(request, new ResponseHandler<Void, RuntimeException>()
        {
            @Override
            public Void handleException(Request request, Exception exception)
                    throws RuntimeException
            {
                fail("unexpected request exception", exception);
                return null;
            }

            @Override
            public Void handle(Request request, Response response)
                    throws RuntimeException
            {
                assertEquals(getCurrentTraceToken(), token);
                return null;
            }
        });
    }

    @Test
    public void testNoFollowRedirect()
            throws Exception
    {
        servlet.setResponseStatusCode(302);
        servlet.setResponseBody("body text");
        servlet.addResponseHeader("Location", "http://127.0.0.1:1");

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 302);
    }

    @Test
    public void testFollowRedirect()
            throws Exception
    {
        EchoServlet destinationServlet = new EchoServlet();

        int port;
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(0));
            port = socket.getLocalPort();
        }

        Server server = new Server();

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendServerVersion(false);
        httpConfiguration.setSendXPoweredBy(false);

        HttpConnectionFactory http1 = new HttpConnectionFactory(httpConfiguration);
        HTTP2CServerConnectionFactory http2c = new HTTP2CServerConnectionFactory(httpConfiguration);
        ServerConnector connector = new ServerConnector(server, null, null, null, -1, -1, http1, http2c);

        connector.setIdleTimeout(30000);
        connector.setName("http");
        connector.setPort(port);

        server.addConnector(connector);

        ServletHolder servletHolder = new ServletHolder(destinationServlet);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.addServlet(servletHolder, "/redirect");
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(context);
        server.setHandler(handlers);

        try {
            server.start();

            servlet.setResponseStatusCode(302);
            servlet.setResponseBody("body text");
            servlet.addResponseHeader("Location", format("http://127.0.0.1:%d/redirect", port));

            Request request = prepareGet()
                    .setUri(baseURI)
                    .setFollowRedirects(true)
                    .build();

            int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
            assertEquals(statusCode, 200);
        }
        finally {
            server.stop();
        }
    }

    @Test(expectedExceptions = {SocketTimeoutException.class, TimeoutException.class, ClosedChannelException.class})
    public void testReadTimeout()
            throws Exception
    {
        HttpClientConfig config = createClientConfig()
                .setIdleTimeout(new Duration(500, MILLISECONDS));

        URI uri = URI.create(baseURI.toASCIIString() + "/?sleep=1000");
        Request request = prepareGet()
                .setUri(uri)
                .build();

        executeRequest(config, request, new ExceptionResponseHandler());
    }

    @Test
    public void testResponseBody()
            throws Exception
    {
        servlet.setResponseBody("body text");

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String body = executeRequest(request, createStringResponseHandler()).getBody();
        assertEquals(body, "body text");
    }

    @Test
    public void testResponseBodyEmpty()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String body = executeRequest(request, createStringResponseHandler()).getBody();
        assertEquals(body, "");
    }

    @Test
    public void testResponseHeader()
            throws Exception
    {
        servlet.addResponseHeader("foo", "bar");
        servlet.addResponseHeader("dupe", "first");
        servlet.addResponseHeader("dupe", "second");

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        StatusResponse response = executeRequest(request, createStatusResponseHandler());

        assertEquals(response.getHeaders("foo"), ImmutableList.of("bar"));
        assertEquals(response.getHeaders("dupe"), ImmutableList.of("first", "second"));
    }

    @Test
    public void testResponseStatusCode()
            throws Exception
    {
        servlet.setResponseStatusCode(543);
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        int statusCode = executeRequest(request, createStatusResponseHandler()).getStatusCode();
        assertEquals(statusCode, 543);
    }

    @Test
    public void testResponseStatusMessage()
            throws Exception
    {
        servlet.setResponseStatusMessage("message");

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String statusMessage = executeRequest(request, createStatusResponseHandler()).getStatusMessage();

        assertEquals(statusMessage, "message");
    }

    @Test(expectedExceptions = UnexpectedResponseException.class)
    public void testThrowsUnexpectedResponseException()
            throws Exception
    {
        servlet.setResponseStatusCode(543);
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        executeRequest(request, new UnexpectedResponseStatusCodeHandler(200));
    }

    @Test
    public void testCompressionIsDisabled()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        String body = executeRequest(request, createStringResponseHandler()).getBody();
        assertEquals(body, "");
        Assert.assertFalse(servlet.getRequestHeaders().containsKey(HeaderName.of(ACCEPT_ENCODING)));
    }

    private ExecutorService executor;

    @BeforeClass
    public final void setUp()
            throws Exception
    {
        executor = Executors.newCachedThreadPool(threadsNamed("test-%s"));
    }

    @AfterClass
    public final void tearDown()
            throws Exception
    {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test(expectedExceptions = {IOException.class, TimeoutException.class})
    public void testConnectNoRead()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 0, null, false)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(10, MILLISECONDS));

            executeRequest(fakeServer, config);
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void testConnectNoReadClose()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 0, null, true)) {

            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(5, SECONDS));

            executeRequest(fakeServer, config);
        }
    }


    @Test(expectedExceptions = {IOException.class, TimeoutException.class})
    public void testConnectReadIncomplete()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 10, null, false)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(10, MILLISECONDS));

            executeRequest(fakeServer, config);
        }
    }


    @Test(expectedExceptions = {IOException.class, TimeoutException.class})
    public void testConnectReadIncompleteClose()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 10, null, true)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(500, MILLISECONDS));
            config.setIdleTimeout(new Duration(500, MILLISECONDS));

            executeRequest(fakeServer, config);
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void testConnectReadRequestClose()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, Long.MAX_VALUE, null, true)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(5, SECONDS));

            executeRequest(fakeServer, config);
        }
    }

    @Test(expectedExceptions = Exception.class)
    public void testConnectReadRequestWriteJunkHangup()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 10, "THIS\nIS\nJUNK\n\n".getBytes(), false)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(5, SECONDS));

            executeRequest(fakeServer, config);
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void testDynamicBodySourceConnectWriteRequestClose()
            throws Exception
    {
        try (FakeServer fakeServer = new FakeServer(scheme, host, 1024, null, true)) {
            HttpClientConfig config = createClientConfig();
            config.setConnectTimeout(new Duration(5, SECONDS));
            config.setIdleTimeout(new Duration(5, SECONDS));

            // kick the fake server
            executor.execute(fakeServer);

            // timing based check to assure we don't hang
            long start = System.nanoTime();
            final AtomicInteger invocation = new AtomicInteger(0);
            try {
                Request request = preparePut()
                        .setUri(fakeServer.getUri())
                        .setBodySource((DynamicBodySource) out -> () -> {
                            if (invocation.getAndIncrement() < 100) {
                                out.write(new byte[1024]);
                            }
                            else {
                                out.close();
                            }
                        })
                        .build();
                executeRequest(config, request, new ExceptionResponseHandler());
            }
            finally {
                assertGreaterThan(invocation.get(), 0);
                assertLessThan(nanosSince(start), new Duration(1, SECONDS), "Expected request to finish quickly");
            }
        }
    }

    @Test(expectedExceptions = CustomError.class)
    public void testHandlesUndeclaredThrowable()
            throws Exception
    {
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        executeRequest(request, new ThrowErrorResponseHandler());
    }

    @Test
    public void testResponseHandlerThrowsBeforeReadingBody()
            throws Exception
    {
        try (LargeResponseServer largeResponseServer = new LargeResponseServer(scheme, host)) {
            RuntimeException expectedException = new RuntimeException();

            // kick the fake server
            executor.execute(largeResponseServer);

            Request request = prepareGet()
                    .setUri(largeResponseServer.getUri())
                    .build();

            try {
                executeRequest(request, new ResponseHandler<Void, RuntimeException>()
                {
                    @Override
                    public Void handleException(Request request, Exception exception)
                    {
                        if (exception instanceof ResponseTooLargeException) {
                            throw expectedException;
                        }
                        fail("Unexpected request failure", exception);
                        return null;
                    }

                    @Override
                    public Void handle(Request request, Response response)
                    {
                        throw expectedException;
                    }
                });
                fail("Expected to get an exception");
            }
            catch (RuntimeException e) {
                assertEquals(e, expectedException);
            }

            largeResponseServer.assertCompleted();
        }
    }

    @Test
    public void testAllConnectionsUsed()
            throws Exception
    {
        final Request request = prepareGet()
                .setUri(uriBuilderFrom(baseURI).addParameter("latch").build())
                .build();
        final ClientTester clientTester = clientTester(createClientConfig()
                .setMaxConnectionsPerServer(2)
                .setMaxRequestsQueuedPerDestination(0));
        final AtomicBoolean sawJettyDump = new AtomicBoolean();

        ExecutorService executor = newFixedThreadPool(3, threadsNamed("test-use-connections"));
        final CountDownLatch countDownLatch = new CountDownLatch(2);

        // The logging test is unfortunately specific to the Jetty implementation
        Logging.addLogTester(JettyHttpClient.class, (level, message, thrown) -> {
            if (message.matches("(?s).*Jetty dump:.*")) {
                sawJettyDump.set(true);
            }
        });

        for (int i = 0; i < 2; ++i) {
            executor.submit(() -> {
                clientTester.executeRequest(request, createStatusResponseHandler());
                countDownLatch.countDown();
                return null;
            });
        }

        Thread.sleep(100);
        try {
            clientTester.executeRequest(request, createStatusResponseHandler());
            fail("expected RejectedExecutionException");
        }
        catch (RejectedExecutionException ignored) {
        }

        assertTrue(sawJettyDump.get(), "Saw dump in log");

        servlet.countDown();
        countDownLatch.await();
        Thread.sleep(100);
        clientTester.executeRequest(request, createStatusResponseHandler());
    }

    private void executeRequest(FakeServer fakeServer, HttpClientConfig config)
            throws Exception
    {
        // kick the fake server
        executor.execute(fakeServer);

        // timing based check to assure we don't hang
        long start = System.nanoTime();
        try {
            Request request = prepareGet()
                    .setUri(fakeServer.getUri())
                    .build();
            executeRequest(config, request, new ExceptionResponseHandler());
        }
        finally {
            assertLessThan(nanosSince(start), new Duration(1, SECONDS), "Expected request to finish quickly");
        }
    }

    private static class FakeServer
            implements Closeable, Runnable
    {
        private final ServerSocket serverSocket;
        private final long readBytes;
        private final byte[] writeBuffer;
        private final boolean closeConnectionImmediately;
        private final AtomicReference<Socket> connectionSocket = new AtomicReference<>();
        private final String scheme;
        private final String host;

        private FakeServer(String scheme, String host, long readBytes, byte[] writeBuffer, boolean closeConnectionImmediately)
                throws Exception
        {
            this.scheme = scheme;
            this.host = host;
            this.writeBuffer = writeBuffer;
            this.readBytes = readBytes;
            this.serverSocket = new ServerSocket(0);
            this.closeConnectionImmediately = closeConnectionImmediately;
        }

        public URI getUri()
        {
            try {
                return new URI(scheme, null, host, serverSocket.getLocalPort(), "/", null, null);
            }
            catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void run()
        {
            try {
                Socket connectionSocket = serverSocket.accept();
                this.connectionSocket.set(connectionSocket);
                if (readBytes > 0) {
                    connectionSocket.setSoTimeout(500);
                    long bytesRead = 0;
                    try {
                        InputStream inputStream = connectionSocket.getInputStream();
                        while (bytesRead < readBytes) {
                            inputStream.read();
                            bytesRead++;
                        }
                    }
                    catch (SocketTimeoutException ignored) {
                    }
                }
                if (writeBuffer != null) {
                    connectionSocket.getOutputStream().write(writeBuffer);
                }
                // todo sleep here maybe
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            finally {
                if (closeConnectionImmediately) {
                    closeQuietly(connectionSocket.get());
                }
            }
        }

        @Override
        public void close()
                throws IOException
        {
            closeQuietly(connectionSocket.get());
            serverSocket.close();
        }
    }

    private class LargeResponseServer
            implements Closeable, Runnable
    {
        private final ServerSocket serverSocket;
        private final AtomicReference<Socket> connectionSocket = new AtomicReference<>();
        private final String scheme;
        private final String host;
        private final CountDownLatch completed = new CountDownLatch(1);

        private LargeResponseServer(String scheme, String host)
                throws Exception
        {
            this.scheme = scheme;
            this.host = host;
            if (sslContextFactory != null) {
                this.serverSocket = sslContextFactory.newSslServerSocket(null, 0, 5);
            }
            else {
                this.serverSocket = new ServerSocket(0);
            }
        }

        public URI getUri()
        {
            try {
                return new URI(scheme, null, host, serverSocket.getLocalPort(), "/", null, null);
            }
            catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }

        public void assertCompleted() {
            try {
                assertTrue(completed.await(10, SECONDS), "LargeResponseServer completed");
            }
            catch (InterruptedException e) {
                throw propagate(e);
            }
        }

        @Override
        public void run()
        {
            try {
                Socket connectionSocket = serverSocket.accept();
                this.connectionSocket.set(connectionSocket);

                BufferedReader reader = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream(), UTF_8));
                String line;
                do {
                    line = reader.readLine();
                } while (!line.isEmpty());

                OutputStreamWriter writer = new OutputStreamWriter(connectionSocket.getOutputStream(), UTF_8);
                writer.write("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "Content-Length: 100000000\r\n" +
                        "\r\n" +
                        Strings.repeat("x", 100000000) +
                        "\r\n");
                writer.flush();
            }
            catch (IOException ignored) {
            }
            finally {
                completed.countDown();
            }
        }

        @Override
        public void close()
                throws IOException
        {
            closeQuietly(connectionSocket.get());
            serverSocket.close();
        }
    }

    public static class ExceptionResponseHandler
            implements ResponseHandler<Void, Exception>
    {
        @Override
        public Void handleException(Request request, Exception exception)
                throws Exception
        {
            throw exception;
        }

        @Override
        public Void handle(Request request, Response response)
                throws Exception
        {
            throw new UnsupportedOperationException();
        }
    }

    private static class PassThroughResponseHandler
            implements ResponseHandler<Response, RuntimeException>
    {
        @Override
        public Response handleException(Request request, Exception exception)
        {
            throw ResponseHandlerUtils.propagate(request, exception);
        }

        @Override
        public Response handle(Request request, Response response)
        {
            return response;
        }
    }

    private static class UnexpectedResponseStatusCodeHandler
            implements ResponseHandler<Integer, RuntimeException>
    {
        private final int expectedStatusCode;

        UnexpectedResponseStatusCodeHandler(int expectedStatusCode)
        {
            this.expectedStatusCode = expectedStatusCode;
        }

        @Override
        public Integer handleException(Request request, Exception exception)
        {
            throw ResponseHandlerUtils.propagate(request, exception);
        }

        @Override
        public Integer handle(Request request, Response response)
                throws RuntimeException
        {
            if (response.getStatusCode() != expectedStatusCode) {
                throw new UnexpectedResponseException(request, response);
            }
            return response.getStatusCode();
        }
    }

    public static class CaptureExceptionResponseHandler
            implements ResponseHandler<String, CapturedException>
    {
        @Override
        public String handleException(Request request, Exception exception)
                throws CapturedException
        {
            throw new CapturedException(exception);
        }

        @Override
        public String handle(Request request, Response response)
                throws CapturedException
        {
            throw new UnsupportedOperationException();
        }

    }

    public static class ThrowErrorResponseHandler
            implements ResponseHandler<String, Exception>
    {
        @Override
        public String handleException(Request request, Exception exception)
        {
            throw new UnsupportedOperationException("not yet implemented", exception);
        }

        @Override
        public String handle(Request request, Response response)
        {
            throw new CustomError();
        }
    }

    private static class CustomError
            extends Error {
    }

    protected static class CapturedException
            extends Exception
    {
        public CapturedException(Exception exception)
        {
            super(exception);
        }
    }

    private static class DefaultOnExceptionResponseHandler
            implements ResponseHandler<Object, RuntimeException>
    {

        private final Object defaultObject;

        public DefaultOnExceptionResponseHandler(Object defaultObject)
        {
            this.defaultObject = defaultObject;
        }

        @Override
        public Object handleException(Request request, Exception exception)
                throws RuntimeException
        {
            return defaultObject;
        }

        @Override
        public Object handle(Request request, Response response)
                throws RuntimeException
        {
            throw new UnsupportedOperationException();
        }
    }

    protected static int findUnusedPort()
            throws IOException
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @SuppressWarnings("SocketOpenedButNotSafelyClosed")
    private static class BackloggedServer
            implements Closeable
    {
        private final List<Socket> clientSockets = new ArrayList<>();
        private final ServerSocket serverSocket;
        private final SocketAddress localSocketAddress;

        private BackloggedServer()
                throws IOException
        {
            this.serverSocket = new ServerSocket(0, 1);
            localSocketAddress = serverSocket.getLocalSocketAddress();

            // some systems like Linux have a large minimum backlog
            int i = 0;
            while (i <= 40) {
                if (!connect()) {
                    return;
                }
                i++;
            }
            throw new SkipException(format("socket backlog is too large (%s connections accepted)", i));
        }

        @Override
        public void close()
        {
            clientSockets.forEach(Closeables::closeQuietly);
            closeQuietly(serverSocket);
        }

        private int getPort()
        {
            return serverSocket.getLocalPort();
        }

        private boolean connect()
                throws IOException
        {
            Socket socket = new Socket();
            clientSockets.add(socket);

            try {
                socket.connect(localSocketAddress, 5);
                return true;
            }
            catch (IOException e) {
                if (isConnectTimeout(e)) {
                    return false;
                }
                throw e;
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void assertUnknownHost(String host)
    {
        try {
            InetAddress.getByName(host);
            fail("Expected UnknownHostException for host " + host);
        }
        catch (UnknownHostException e) {
            // expected
        }
    }

    private static boolean isConnectTimeout(Throwable t)
    {
        // Linux refuses connections immediately rather than queuing them
        return (t instanceof SocketTimeoutException) || (t instanceof SocketException);
    }

    public static <T, E extends Exception> T executeAsync(JettyHttpClient client, Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        HttpResponseFuture<T> future = null;
        try {
            future = client.executeAsync(request, responseHandler);
        }
        catch (Exception e) {
            fail("Unexpected exception", e);
        }

        try {
            return future.get();
        }
        catch (InterruptedException e) {
            currentThread().interrupt();
            throw new RuntimeException(e);
        }
        catch (ExecutionException e) {
            throwIfUnchecked(e.getCause());

            if (e.getCause() instanceof Exception) {
                // the HTTP client and ResponseHandler interface enforces this
                throw (E) e.getCause();
            }

            // e.getCause() is some direct subclass of throwable
            throw new RuntimeException(e.getCause());
        }
    }
}
