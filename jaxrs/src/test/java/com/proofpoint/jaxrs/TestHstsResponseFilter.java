package com.proofpoint.jaxrs;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.InetAddresses;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.ClientAddressExtractor;
import com.proofpoint.http.server.HttpServer;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.http.server.HttpServerModuleOptions;
import com.proofpoint.http.server.InternalNetworkConfig;
import com.proofpoint.http.server.LocalAnnouncementHttpServerInfo;
import com.proofpoint.http.server.QueryStringFilter;
import com.proofpoint.http.server.TheServlet;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.NodeConfig;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import jakarta.servlet.Filter;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static com.google.common.io.Resources.getResource;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.HttpUriBuilder.uriBuilderFrom;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestHstsResponseFilter
{
    private static final String JAVAX_NET_SSL_TRUST_STORE = "javax.net.ssl.trustStore";
    private static JettyHttpClient client;

    private LifeCycleManager lifeCycleManager;
    private TestingHttpServer server;
    private String originalTrustStore;

    @BeforeClass
    public void setupClass()
    {
        originalTrustStore = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
        System.setProperty(JAVAX_NET_SSL_TRUST_STORE, getResource("localhost.keystore").getPath());
        client = new JettyHttpClient();
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
            throws Exception
    {
        if (lifeCycleManager != null) {
            lifeCycleManager.stop();
            lifeCycleManager = null;
        }
    }

    @AfterClass(alwaysRun = true)
    public void teardownClass()
    {
        if (originalTrustStore != null) {
            System.setProperty(JAVAX_NET_SSL_TRUST_STORE, originalTrustStore);
        }
        else {
            System.clearProperty(JAVAX_NET_SSL_TRUST_STORE);
        }
        Closeables.closeQuietly(client);
    }

    @Test
    public void testHstsNotReturnedWithHttp()
            throws Exception
    {
        createServer(ImmutableMap.of());

        Request request = Request.builder()
                .setUri(uriBuilderFrom(server.getBaseUrl().resolve("/test/hsts")).build())
                .setMethod("GET")
                .build();
        StatusResponse response = client.execute(request, createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertNull(response.getHeader("Strict-Transport-Security"));
    }

    @Test
    public void testHstsWithConfig()
            throws Exception
    {
        createServer(ImmutableMap.of("jaxrs.hsts.max-age", "600s",
                "jaxrs.hsts.include-sub-domains", "true",
                "jaxrs.hsts.preload", "true"));
        Request request = Request.builder()
                .setUri(uriBuilderFrom(server.getHttpServerInfo().getHttpsUri().resolve("/test/hsts")).build())
                .setMethod("GET")
                .build();
        StatusResponse response = client.execute(request, createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String hstsHeader = response.getHeader("Strict-Transport-Security");
        assertEquals(hstsHeader, "max-age=600; includeSubDomains; preload");
    }

    @Test
    public void testHstsWithoutOptionalConfig()
            throws Exception
    {
        createServer(ImmutableMap.of("jaxrs.hsts.max-age", "31536000s"));
        Request request = Request.builder()
                .setUri(uriBuilderFrom(server.getHttpServerInfo().getHttpsUri().resolve("/test/hsts")).build())
                .setMethod("GET")
                .build();
        StatusResponse response = client.execute(request, createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String hstsHeader = response.getHeader("Strict-Transport-Security");
        assertEquals(hstsHeader, "max-age=31536000");
    }

    @Test
    public void testHstsDisabledIfMaxAgeNotSet()
            throws Exception
    {
        createServer(ImmutableMap.of());
        Request request = Request.builder()
                .setUri(uriBuilderFrom(server.getBaseUrl().resolve("/test/hsts")).build())
                .setMethod("GET")
                .build();
        StatusResponse response = client.execute(request, createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertNull(response.getHeader("Strict-Transport-Security"));
    }

    private void createServer(Map<String, String> config)
            throws Exception
    {
        NodeInfo nodeInfo = new NodeInfo("test-application", new NodeConfig()
                .setEnvironment("test")
                .setNodeInternalIp(InetAddresses.forString("127.0.0.1"))
                .setNodeBindIp(InetAddresses.forString("127.0.0.1"))
                .setNodeExternalAddress("localhost")
                .setNodeInternalHostname("localhost"));

        Injector injector = bootstrapTest()
                .withModules(
                        binder -> binder.bind(NodeInfo.class).toInstance(nodeInfo),
                        explicitJaxrsModule(),
                        new JsonModule(),
                        new ReportingModule(),
                        new SslTestingHttpServerModule(),
                        binder -> jaxrsBinder(binder).bind(HstsResource.class))
                .setRequiredConfigurationProperties(config)
                .initialize();
        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
    }

    private class SslTestingHttpServerModule implements Module
    {
        @Override
        public void configure(Binder binder)
        {
            binder.disableCircularProxies();

            HttpServerConfig config = new HttpServerConfig()
                    .setMinThreads(1)
                    .setMaxThreads(20)
                    .setShowStackTrace(true)
                    .setHttpsEnabled(true)
                    .setHttpEnabled(true)
                    .setHttpsPort(0)
                    .setHttpPort(0)
                    .setKeystorePath(getResource("localhost.keystore").toString())
                    .setKeystorePassword("changeit");

            binder.bind(HttpServerModuleOptions.class).in(Scopes.SINGLETON);
            binder.bind(HttpServerConfig.class).toInstance(config);
            binder.bind(InternalNetworkConfig.class).toInstance(new InternalNetworkConfig());
            binder.bind(HttpServerInfo.class).in(Scopes.SINGLETON);
            binder.bind(TestingHttpServer.class).in(Scopes.SINGLETON);
            binder.bind(HttpServer.class).to(Key.get(TestingHttpServer.class));
            binder.bind(QueryStringFilter.class).in(Scopes.SINGLETON);
            binder.bind(ClientAddressExtractor.class).in(Scopes.SINGLETON);
            newSetBinder(binder, Filter.class, TheServlet.class);
            newSetBinder(binder, HttpResourceBinding.class, TheServlet.class);
            binder.bind(AnnouncementHttpServerInfo.class).to(LocalAnnouncementHttpServerInfo.class);
        }
    }

    @Path("/test")
    public static class HstsResource
    {
        @GET
        @Path("/hsts")
        public Response hsts()
        {
            return Response.ok().build();
        }
    }
}
