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
package com.proofpoint.event.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.ListenableFuture;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.UnexpectedResponseException;
import com.proofpoint.http.client.balancing.BalancingHttpClient;
import com.proofpoint.http.client.balancing.BalancingHttpClientConfig;
import com.proofpoint.http.client.balancing.HttpServiceBalancerConfig;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.http.client.balancing.HttpServiceBalancerStats;
import com.proofpoint.http.client.balancing.ServiceUnavailableException;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.reporting.testing.TestingReportCollectionFactory;
import com.proofpoint.units.Duration;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.joda.time.DateTime;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.proofpoint.concurrent.Threads.daemonThreadsNamed;
import static com.proofpoint.event.client.EventTypeMetadata.getValidEventTypeMetaDataSet;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static com.proofpoint.tracetoken.TraceTokenManager.registerRequestToken;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestHttpEventClient
{
    private DummyServlet servlet;
    private HttpEventClient client;
    private Server server;
    private URI baseUri;
    private HttpClient httpClient;
    private HttpServiceBalancerImpl balancer;
    private int responseCode;

    @Test(expectedExceptions = ServiceUnavailableException.class, expectedExceptionsMessageRegExp = ".*has no instances.*")
    public void testFutureFailsWhenServiceUnavailable()
            throws InterruptedException
    {
        client = newEventClient(ImmutableSet.of());

        try {
            client.post(new FixedDummyEventClass("host", new DateTime(), UUID.randomUUID(), 1, "foo")).get();
        }
        catch (ExecutionException e) {
            throw Throwables.propagate(e.getCause());
        }
    }

    @Test
    public void testCallSucceedsWhenServiceUnavailable()
    {
        client = newEventClient(ImmutableSet.of());

        client.post(new FixedDummyEventClass("host", new DateTime(), UUID.randomUUID(), 1, "foo"));

        assertNull(servlet.lastPath);
        assertNull(servlet.lastBody);
    }

    @Test
    public void testServerFails()
            throws InterruptedException
    {
        responseCode = 503;
        client = newEventClient(ImmutableSet.of(baseUri));

        ListenableFuture<Void> future = client.post(TestingUtils.getEvents());

        try {
            future.get();
            fail("expected exception");
        }
        catch (ExecutionException e) {
            assertInstanceOf(e.getCause(), UnexpectedResponseException.class);
        }

        assertEquals(servlet.lastPath, "/v2/event");
    }

    @Test
    public void testReceivesEvent()
            throws ExecutionException, InterruptedException, IOException
    {
        client = newEventClient(ImmutableSet.of(baseUri));

        client.post(TestingUtils.getEvents()).get();

        assertEquals(servlet.lastPath, "/v2/event");
        assertEquals(new ObjectMapper().readValue(servlet.lastBody, Object.class), TestingUtils.getExpectedJson(), "JSON encoding " + servlet.lastBody);
    }

    @Test
    public void loadTest()
            throws ExecutionException, InterruptedException, IOException
    {
        client = newEventClient(ImmutableSet.of(baseUri));

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(client.post(TestingUtils.getEvents()));
        }

        for (Future<Void> future : futures) {
            future.get();
            System.out.println("future " + future);
        }
        assertEquals(servlet.lastPath, "/v2/event");
        assertEquals(new ObjectMapper().readValue(servlet.lastBody, Object.class), TestingUtils.getExpectedJson(), "JSON encoding " + servlet.lastBody);
    }

    @BeforeMethod
    public void setup()
            throws Exception
    {
        balancer = new HttpServiceBalancerImpl(
                "test collector balancer",
                new TestingReportCollectionFactory().createReportCollection(HttpServiceBalancerStats.class),
                new HttpServiceBalancerConfig()
        );

        httpClient = new BalancingHttpClient(balancer,
                new JettyHttpClient(new HttpClientConfig()
                        .setConnectTimeout(new Duration(10, SECONDS))
                        .setMaxRequestsQueuedPerDestination(100)),
                new BalancingHttpClientConfig()
                        .setMinBackoff(new Duration(1, MILLISECONDS))
                        .setMaxBackoff(new Duration(2, MILLISECONDS)),
                newSingleThreadScheduledExecutor(daemonThreadsNamed("event-test-retry")));
        servlet = new DummyServlet();
        server = createServer(servlet);
        server.start();
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }

        httpClient.close();
    }

    private HttpEventClient newEventClient(Set<URI> uris)
    {
        NodeInfo nodeInfo = new NodeInfo("test");
        balancer.updateHttpUris(uris);

        Set<EventTypeMetadata<?>> eventTypes = getValidEventTypeMetaDataSet(FixedDummyEventClass.class);
        JsonEventWriter eventWriter = new JsonEventWriter(nodeInfo, eventTypes);
        registerRequestToken("sample-trace-token");

        return new HttpEventClient(
                eventWriter,
                nodeInfo,
                httpClient
        );
    }

    private Server createServer(final HttpServlet servlet)
            throws Exception
    {
        int port;
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(0));
            port = socket.getLocalPort();
        }
        baseUri = new URI("http", null, "127.0.0.1", port, null, null, null);

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendServerVersion(false);
        httpConfiguration.setSendXPoweredBy(false);

        server = new Server();

        ServerConnector httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration));
        httpConnector.setPort(port);
        httpConnector.setName("http");
        server.addConnector(httpConnector);

        ServletHolder servletHolder = new ServletHolder(servlet);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.addServlet(servletHolder, "/*");
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(context);
        server.setHandler(handlers);
        return server;
    }

    private class DummyServlet
            extends HttpServlet
    {
        private volatile String lastPath;
        private volatile String lastBody;

        private DummyServlet()
        {
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException
        {
            lastPath = request.getPathInfo();
            lastBody = CharStreams.toString(new InputStreamReader(request.getInputStream(), UTF_8));
            if (responseCode != 0) {
                response.sendError(responseCode);
            }
        }
    }
}
