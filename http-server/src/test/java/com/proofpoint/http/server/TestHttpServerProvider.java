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

import com.google.common.io.Files;
import com.google.common.net.InetAddresses;
import com.proofpoint.bootstrap.LifeCycleConfig;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.log.Level;
import com.proofpoint.log.Logging;
import com.proofpoint.node.NodeConfig;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static com.google.common.io.Resources.getResource;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.Request.Builder.preparePut;
import static com.proofpoint.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.log.Logging.resetLogTesters;
import static com.proofpoint.testing.Assertions.assertContains;
import static com.proofpoint.testing.Assertions.assertNotEquals;
import static com.proofpoint.testing.Closeables.closeQuietly;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestHttpServerProvider
{
    private static final String JAVAX_NET_SSL_TRUST_STORE = "javax.net.ssl.trustStore";
    private String originalTrustStore;
    private HttpServer server;
    private File tempDir;
    private NodeInfo nodeInfo;
    private HttpServerConfig config;
    private List<String> infoLogMessages;
    private HttpServerInfo httpServerInfo;
    private LifeCycleManager lifeCycleManager;
    private RequestLog requestLog;

    @BeforeSuite
    public void setupSuite()
    {
        Logging.initialize();
    }

    @BeforeMethod
    public void setup()
            throws Exception
    {
        originalTrustStore = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
        System.setProperty(JAVAX_NET_SSL_TRUST_STORE, getResource("localhost.keystore").getPath());
        tempDir = Files.createTempDir().getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
        config = new HttpServerConfig()
                .setHttpPort(0)
                .setHttpsPort(0)
                .setLogPath(new File(tempDir, "http-request.log").getAbsolutePath());
        nodeInfo = new NodeInfo("test-application", new NodeConfig()
                .setEnvironment("test")
                .setNodeInternalIp(InetAddresses.forString("127.0.0.1"))
                .setNodeBindIp(InetAddresses.forString("127.0.0.1"))
                .setNodeExternalAddress("localhost")
                .setNodeInternalHostname("localhost")
        );
        infoLogMessages = new ArrayList<>();
        Logging.addLogTester("Bootstrap", ((level, message, thrown) -> {
            if (level == Level.INFO) {
                infoLogMessages.add(message);
            }
        }));
        httpServerInfo = new HttpServerInfo(config, nodeInfo);
        lifeCycleManager = new LifeCycleManager(List.of(), null, new LifeCycleConfig());
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
            throws Exception
    {
        closeChannels(httpServerInfo);
        resetLogTesters();
        if (originalTrustStore != null) {
            System.setProperty(JAVAX_NET_SSL_TRUST_STORE, originalTrustStore);
        }
        else {
            System.clearProperty(JAVAX_NET_SSL_TRUST_STORE);
        }

        try {
            if (server != null) {
                server.stop();
            }
        }
        finally {
            deleteRecursively(tempDir.toPath(), ALLOW_INSECURE);
        }
    }

    @Test
    public void testConnectorDefaults()
    {
        assertTrue(config.isHttpEnabled());
        assertNotNull(httpServerInfo.getHttpUri());
        assertNotNull(httpServerInfo.getHttpExternalUri());
        assertNotNull(httpServerInfo.getHttpChannel());
        assertEquals(httpServerInfo.getHttpUri().getScheme(), httpServerInfo.getHttpExternalUri().getScheme());
        assertEquals(httpServerInfo.getHttpUri().getPort(), httpServerInfo.getHttpExternalUri().getPort());
        assertEquals(httpServerInfo.getHttpUri().getScheme(), "http");

        assertFalse(config.isHttpsEnabled());
        assertNull(httpServerInfo.getHttpsUri());
        assertNull(httpServerInfo.getHttpsChannel());

        assertTrue(config.isAdminEnabled());
        assertNotNull(httpServerInfo.getAdminUri());
        assertNotNull(httpServerInfo.getAdminExternalUri());
        assertNotNull(httpServerInfo.getAdminChannel());
        assertEquals(httpServerInfo.getAdminUri().getScheme(), httpServerInfo.getAdminExternalUri().getScheme());
        assertEquals(httpServerInfo.getAdminUri().getPort(), httpServerInfo.getAdminExternalUri().getPort());
        assertEquals(httpServerInfo.getAdminUri().getScheme(), "http");

        assertNotEquals(httpServerInfo.getHttpUri().getPort(), httpServerInfo.getAdminUri().getPort());
    }

    @Test
    public void testHttpDisabled()
    {
        closeChannels(httpServerInfo);

        config.setHttpEnabled(false);
        httpServerInfo = new HttpServerInfo(config, nodeInfo);

        assertNull(httpServerInfo.getHttpUri());
        assertNull(httpServerInfo.getHttpExternalUri());
        assertNull(httpServerInfo.getHttpChannel());

        assertNull(httpServerInfo.getHttpsUri());
        assertNull(httpServerInfo.getHttpsChannel());

        assertNotNull(httpServerInfo.getAdminUri());
        assertNotNull(httpServerInfo.getAdminExternalUri());
        assertNotNull(httpServerInfo.getAdminChannel());
        assertEquals(httpServerInfo.getAdminUri().getScheme(), httpServerInfo.getAdminExternalUri().getScheme());
        assertEquals(httpServerInfo.getAdminUri().getPort(), httpServerInfo.getAdminExternalUri().getPort());
        assertEquals(httpServerInfo.getAdminUri().getScheme(), "http");
    }

    @Test
    public void testAdminDisabled()
    {
        closeChannels(httpServerInfo);

        config.setAdminEnabled(false);
        httpServerInfo = new HttpServerInfo(config, nodeInfo);

        assertNotNull(httpServerInfo.getHttpUri());
        assertNotNull(httpServerInfo.getHttpExternalUri());
        assertNotNull(httpServerInfo.getHttpChannel());
        assertEquals(httpServerInfo.getHttpUri().getScheme(), httpServerInfo.getHttpExternalUri().getScheme());
        assertEquals(httpServerInfo.getHttpUri().getPort(), httpServerInfo.getHttpExternalUri().getPort());
        assertEquals(httpServerInfo.getHttpUri().getScheme(), "http");

        assertNull(httpServerInfo.getHttpsUri());
        assertNull(httpServerInfo.getHttpsChannel());

        assertNull(httpServerInfo.getAdminUri());
        assertNull(httpServerInfo.getAdminExternalUri());
        assertNull(httpServerInfo.getAdminChannel());
    }

    @Test
    public void testHttpsEnabled()
    {
        closeChannels(httpServerInfo);

        config.setHttpsEnabled(true);
        httpServerInfo = new HttpServerInfo(config, nodeInfo);

        assertNotNull(httpServerInfo.getHttpUri());
        assertNotNull(httpServerInfo.getHttpExternalUri());
        assertNotNull(httpServerInfo.getHttpChannel());
        assertEquals(httpServerInfo.getHttpUri().getScheme(), httpServerInfo.getHttpExternalUri().getScheme());
        assertEquals(httpServerInfo.getHttpUri().getPort(), httpServerInfo.getHttpExternalUri().getPort());
        assertEquals(httpServerInfo.getHttpUri().getScheme(), "http");

        assertNotNull(httpServerInfo.getHttpsUri());
        assertNotNull(httpServerInfo.getHttpsChannel());
        assertEquals(httpServerInfo.getHttpsUri().getScheme(), httpServerInfo.getHttpsUri().getScheme());
        assertEquals(httpServerInfo.getHttpsUri().getPort(), httpServerInfo.getHttpsUri().getPort());
        assertEquals(httpServerInfo.getHttpsUri().getScheme(), "https");

        assertNotNull(httpServerInfo.getAdminUri());
        assertNull(httpServerInfo.getAdminExternalUri());
        assertNotNull(httpServerInfo.getAdminChannel());
        assertEquals(httpServerInfo.getAdminUri().getScheme(), httpServerInfo.getAdminUri().getScheme());
        assertEquals(httpServerInfo.getAdminUri().getPort(), httpServerInfo.getAdminUri().getPort());
        assertEquals(httpServerInfo.getAdminUri().getScheme(), "https");

        assertNotEquals(httpServerInfo.getHttpUri().getPort(), httpServerInfo.getHttpsUri().getPort());
        assertNotEquals(httpServerInfo.getHttpUri().getPort(), httpServerInfo.getAdminUri().getPort());
    }

    @Test
    public void testHttp()
            throws Exception
    {
        createServer();
        lifeCycleManager.start();

        try (JettyHttpClient httpClient = new JettyHttpClient(new HttpClientConfig().setHttp2Enabled(false))) {
            StatusResponse response = httpClient.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStatusResponseHandler());

            assertEquals(response.getHeader("X-Protocol"), "HTTP/1.1");
        }
        verify(requestLog).log(any());

        try (JettyHttpClient httpClient = new JettyHttpClient(new HttpClientConfig().setHttp2Enabled(true))) {
            StatusResponse response = httpClient.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(response.getHeader("X-Protocol"), "HTTP/2.0");
        }
    }

    @Test
    public void testHttps()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(getResource("localhost.keystore").toString())
                .setKeystorePassword("changeit");
        httpServerInfo = new HttpServerInfo(config, nodeInfo);

        createServer();
        lifeCycleManager.start();

        HttpClient client = new JettyHttpClient();
        StatusResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpsUri()).build(), createStatusResponseHandler());

        assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testNoRequestLog()
            throws Exception
    {
        config.setLogEnabled(false);
        httpServerInfo = new HttpServerInfo(config, nodeInfo);
        requestLog = null;

        createServer();
        lifeCycleManager.start();

        try (JettyHttpClient httpClient = new JettyHttpClient()) {
            StatusResponse response = httpClient.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
        }
        assertNull(requestLog, "request log");
    }

    @Test
    public void testLogOnExceptionNotReadingBody()
            throws Exception
    {
        createServer(new ErrorServlet());
        lifeCycleManager.start();

        try (JettyHttpClient httpClient = new JettyHttpClient()) {
            StatusResponse response = httpClient.execute(preparePut().setUri(httpServerInfo.getHttpUri()).setBodySource(createStaticBodyGenerator("{}", UTF_8)).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        server.stop();
        verify(requestLog).log(any());
    }

    @Test
    public void testFilter()
            throws Exception
    {
        createServer();
        lifeCycleManager.start();

        try (JettyHttpClient client = new JettyHttpClient()) {
            StatusResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri().resolve("/filter")).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_PAYMENT_REQUIRED);
        }
    }

    @Test
    public void testCompressedRequest()
            throws Exception
    {
        createAndStartServer();

        try (JettyHttpClient httpClient = new JettyHttpClient()) {
            StringResponse response = httpClient.execute(
                    preparePut()
                            .setUri(httpServerInfo.getHttpUri())
                            .setHeader("Content-Encoding", "gzip")
                            .setBodySource(createStaticBodyGenerator(new byte[]{
                                    31, -117, 8, 0, -123, -120, -97, 83, 0, 3, 75, -83,
                                    40, 72, 77, 46, 73, 77, 1, 0, -60, -72, 96, 80, 8, 0, 0, 0
                            }))
                            .build(),
                    createStringResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(response.getBody(), "expected");
        }
    }

    @Test
    public void testAuth()
            throws Exception
    {
        File file = File.createTempFile("auth", ".properties", tempDir);
        Files.asCharSink(file, UTF_8).write("user: password");

        config.setUserAuthFile(file.getAbsolutePath());

        createServer();
        lifeCycleManager.start();

        try (HttpClient client = new JettyHttpClient()) {
            StringResponse response = client.execute(
                    prepareGet()
                            .setUri(httpServerInfo.getHttpUri())
                            .addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString("user:password".getBytes(UTF_8)).trim())
                            .build(),
                    createStringResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(response.getBody(), "user");
        }
    }

    @Test
    public void testShowStackTraceEnabled()
            throws Exception
    {
        config.setShowStackTrace(true);
        createServer(new ErrorServlet());
        lifeCycleManager.start();

        try (HttpClient client = new JettyHttpClient()) {
            StringResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStringResponseHandler());
            assertEquals(response.getStatusCode(), 500);
            assertContains(response.getBody(), "ErrorServlet.java");
        }
    }

    @Test
    public void testShowStackTraceDisabled()
            throws Exception
    {
        createServer(new ErrorServlet());
        lifeCycleManager.start();

        try (HttpClient client = new JettyHttpClient()) {
            StringResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStringResponseHandler());
            assertEquals(response.getStatusCode(), 500);
            assertTrue(!response.getBody().contains("ErrorServlet.java"));
        }
    }

    @Test
    public void testHttpsDaysUntilCertificateExpiration()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(new File(getResource("localhost.keystore").toURI()).getAbsolutePath())
                .setKeystorePassword("changeit");
        createAndStartServer();
        Long daysUntilCertificateExpiration = server.getDaysUntilCertificateExpiration();
        assertNotNull(daysUntilCertificateExpiration);
        assertTrue(daysUntilCertificateExpiration > 1000);
    }

    @Test
    public void testNoHttpsDaysUntilCertificateExpiration()
            throws Exception
    {
        config.setHttpEnabled(true)
                .setHttpsPort(0);
        createAndStartServer();
        assertNull(server.getDaysUntilCertificateExpiration());
    }

    @Test
    public void testCreatesTraceToken()
            throws Exception
    {
        createServer();
        lifeCycleManager.start();

        try (JettyHttpClient client = new JettyHttpClient()) {
            StatusResponse response = client.execute(prepareGet()
                    .setUri(httpServerInfo.getHttpUri())
                    .setHeader("X-Forwarded-For", "10.2.3.4")
                    .build(), createStatusResponseHandler());

            String token = response.getHeader("X-Trace-Token-Was");
            assertEquals(token.length(), 32);
            assertEquals(token.substring(0, 12), "fwAAAQ=AgME=");
        }
    }

    @Test
    public void testSimpleTraceToken()
            throws Exception
    {
        createServer();
        lifeCycleManager.start();

        try (JettyHttpClient client = new JettyHttpClient()) {
            StatusResponse response = client.execute(prepareGet()
                    .setUri(httpServerInfo.getHttpUri())
                    .setHeader("X-Proofpoint-TraceToken", "some-token-value")
                    .build(), createStatusResponseHandler());

            String token = response.getHeader("X-Trace-Token-Was");
            assertEquals(token, "some-token-value");
        }
    }

    @Test
    public void testTraceTokenWithProperties()
            throws Exception
    {
        createServer();
        lifeCycleManager.start();

        try (JettyHttpClient client = new JettyHttpClient()) {
            StatusResponse response = client.execute(prepareGet()
                    .setUri(httpServerInfo.getHttpUri())
                    .setHeader("X-Proofpoint-TraceToken", "{\"id\":\"testBasic\",\"key-b\":\"value-b\",\"key-a\":\"value-a\",\"key-c\":\"value-c\"}")
                    .build(), createStatusResponseHandler());

            String token = response.getHeader("X-Trace-Token-Was");
            assertEquals(token, "{id=testBasic, key-b=value-b, key-a=value-a, key-c=value-c}");
        }
    }

    @Test
    public void testTraceTokenIgnoresLocalProperties()
            throws Exception
    {
        createServer();
        lifeCycleManager.start();

        try (JettyHttpClient client = new JettyHttpClient()) {
            StatusResponse response = client.execute(prepareGet()
                    .setUri(httpServerInfo.getHttpUri())
                    .setHeader("X-Proofpoint-TraceToken", "{\"id\":\"testBasic\",\"key-b\":\"value-b\",\"_local-1\":\"value-1\",\"key-a\":\"value-a\"}")
                    .build(), createStatusResponseHandler());

            String token = response.getHeader("X-Trace-Token-Was");
            assertEquals(token, "{id=testBasic, key-b=value-b, key-a=value-a}");
        }
    }

    @Test
    public void testInvalidTraceToken()
            throws Exception
    {
        createServer();
        lifeCycleManager.start();

        try (JettyHttpClient client = new JettyHttpClient()) {
            StatusResponse response = client.execute(prepareGet()
                    .setUri(httpServerInfo.getHttpUri())
                    .setHeader("X-Forwarded-For", "10.2.3.4")
                    .setHeader("X-Proofpoint-TraceToken", "{\"id\":\"testBasic\"")
                    .build(), createStatusResponseHandler());

            String token = response.getHeader("X-Trace-Token-Was");
            assertEquals(token.length(), 32);
            assertEquals(token.substring(0, 12), "fwAAAQ=AgME=");
        }
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Insufficient configured threads: .*")
    public void testInsufficientThreadsHttp()
            throws Exception
    {
        config.setMaxThreads(1);
        createAndStartServer();
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Insufficient configured threads: .*")
    public void testInsufficientThreadsHttps()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(getResource("localhost.keystore").toString())
                .setKeystorePassword("changeit")
                .setMaxThreads(1);
        createAndStartServer();
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Insufficient configured threads: .*")
    public void testInsufficientThreadsAdmin()
            throws Exception
    {
        config.setAdminMaxThreads(1);
        createAndStartServer();
    }

    @Test
    public void testStopRequestLog()
            throws Exception
    {
        createAndStartServer();
        server.stop();
        verify(requestLog).stop();
    }

    private void createAndStartServer()
            throws Exception
    {
        closeChannels(httpServerInfo);
        httpServerInfo = new HttpServerInfo(config, nodeInfo);
        createServer();
        server.start();
    }

    private void createServer()
    {
        createServer(new DummyServlet());
    }

    private void createServer(HttpServlet servlet)
    {
        HashLoginServiceProvider loginServiceProvider = new HashLoginServiceProvider(config);
        HttpServerProvider serverProvider = new HttpServerProvider(
                httpServerInfo,
                nodeInfo,
                config,
                servlet,
                Set.of(new DummyFilter()),
                Set.of(),
                new DummyServlet(),
                Set.of(),
                new RequestStats(),
                new TestingHttpServer.DetailedRequestStats(),
                new QueryStringFilter(),
                new ClientAddressExtractor(),
                lifeCycleManager)
        {
            @Override
            protected RequestLog createRequestLog(HttpServerConfig config)
            {
                requestLog = mock(RequestLog.class);
                return requestLog;
            }
        };
        serverProvider.setLoginService(loginServiceProvider.get());
        server = serverProvider.get();
    }

    static void closeChannels(HttpServerInfo info)
    {
        closeQuietly(info.getHttpChannel(), info.getHttpsChannel(), info.getAdminChannel());
    }
}
