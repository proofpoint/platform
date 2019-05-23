package com.proofpoint.jaxrs;

import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.TheServlet;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.inject.Inject;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.HttpUriBuilder.uriBuilderFrom;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.adminOnlyJaxrsModule;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.testing.Assertions.assertContains;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestJaxrsModule
{
    private static final String INJECTED_MESSSAGE = "Hello, World!";
    private static final String SECOND_INJECTED_MESSSAGE = "Goodbye, World!";

    private final JettyHttpClient client = new JettyHttpClient();

    private LifeCycleManager lifeCycleManager;
    private TestingHttpServer server;

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
        Closeables.closeQuietly(client);
    }

    @Test
    public void testWadlDisabled()
            throws Exception
    {
        createServer(binder -> jaxrsBinder(binder).bind(TestingResource.class));

        Request request = Request.builder()
                            .setUri(server.getBaseUrl().resolve("/application.wadl"))
                            .setMethod("GET")
                            .build();
        StatusResponse response = client.execute(request, createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode(), "Status code");
    }

    @Test
    public void testOptionsDisabled()
            throws Exception
    {
        createServer(binder -> jaxrsBinder(binder).bind(TestingResource.class));

        Request request = Request.builder()
                            .setUri(server.getBaseUrl().resolve("/"))
                            .setMethod("OPTIONS")
                            .build();
        StatusResponse response = client.execute(request, createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.METHOD_NOT_ALLOWED.getStatusCode(), "Status code");
        assertNull(response.getHeader("Host")); // Pentest "finding"
    }

    @Test
    public void testEnableOptions()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        new JaxrsModule().withOptionsEnabled(),
                        new JsonModule(),
                        new ReportingModule(),
                        new TestingHttpServerModule(),
                        binder -> jaxrsBinder(binder).bind(TestingResource.class))
                .initialize();
        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);

        Request request = Request.builder()
                            .setUri(server.getBaseUrl().resolve("/"))
                            .setMethod("OPTIONS")
                            .build();
        StatusResponse response = client.execute(request, createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode(), "Status code");
        assertNull(response.getHeader("Host")); // Pentest "finding"
    }

    @Test
    public void testInjectableProvider()
            throws Exception
    {
        createServer(binder -> {
            jaxrsBinder(binder).bindInstance(new InjectedResource());
            jaxrsBinder(binder).bindInjectionProvider(InjectedContextObject.class).to(InjectedContextObjectSupplier.class);
        });

        Request request = Request.builder()
                            .setUri(server.getBaseUrl().resolve("/injectedresource"))
                            .setMethod("GET")
                            .build();
        StringResponse response = client.execute(request, createStringResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode(), "Status code");
        assertContains(response.getBody(), INJECTED_MESSSAGE);
    }

    @Test
    public void testTwoInjectableProviders()
            throws Exception
    {
        createServer(binder -> {
            jaxrsBinder(binder).bindInstance(new InjectedResource2());
            jaxrsBinder(binder).bindInjectionProvider(InjectedContextObject.class).to(InjectedContextObjectSupplier.class);
            jaxrsBinder(binder).bindInjectionProvider(SecondInjectedContextObject.class).to(SecondInjectedContextObjectSupplier.class);
        });

        Request request = Request.builder()
                            .setUri(server.getBaseUrl().resolve("/injectedresource2"))
                            .setMethod("GET")
                            .build();
        StringResponse response = client.execute(request, createStringResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode(), "Status code");
        assertContains(response.getBody(), INJECTED_MESSSAGE);
        assertContains(response.getBody(), SECOND_INJECTED_MESSSAGE);
    }

    @Test
    public void testClientInfo()
        throws Exception
    {
        createServer(binder -> jaxrsBinder(binder).bind(ClientInfoResource.class));

        Request request = Request.builder()
                            .setUri(server.getBaseUrl().resolve("/test"))
                            .setMethod("GET")
                            .setHeader("X-FORWARDED-FOR", "1.2.3.4")
                            .build();
        StringResponse response = client.execute(request, createStringResponseHandler());
        assertEquals(response.getBody(), "1.2.3.4", "Response body");

    }

    @Test
    public void testRedirectWithUnquotedSearch()
        throws Exception
    {
        createServer(binder -> jaxrsBinder(binder).bind(RedirectResource.class));

        Request request = Request.builder()
                            .setUri(server.getBaseUrl().resolve("/test"))
                            .setMethod("GET")
                            .build();
        StringResponse response = client.execute(request, createStringResponseHandler());
        assertEquals(response.getHeader("Location"), "https://maps.example.com/maps?q=Dirtt&#43;Environmental&#43;Solutions&#43;Ltd", "Location header");

    }

    @Test
    public void testQueryParamAsFormParamDisabled()
            throws Exception
    {
        createServer(binder -> jaxrsBinder(binder).bind(FormParamResource.class));

        Request request = Request.builder()
                .setUri(uriBuilderFrom(server.getBaseUrl().resolve("/test")).addParameter("testParam", "foo").build())
                .setMethod("POST")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED)
                .build();
        StringResponse response = client.execute(request, createStringResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getBody(), "Empty param");
    }

    @Test
    public void testAdminOnly()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        adminOnlyJaxrsModule(),
                        new JsonModule(),
                        new ReportingModule(),
                        new TestingHttpServerModule(),
                        new Module()
                        {
                            @Override
                            public void configure(Binder binder)
                            {
                                binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class);
                            }

                            @Provides
                            @TheServlet
                            public Map<String, String> createTheServletParams()
                            {
                                return new HashMap<>();
                            }
                        })
                .initialize();
        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
    }

    private void createServer(Module module)
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        explicitJaxrsModule(),
                        new JsonModule(),
                        new ReportingModule(),
                        new TestingHttpServerModule(),
                        module)
                .initialize();
        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
    }

    @Path("/injectedresource")
    public static class InjectedResource
    {
        @GET
        public Response getContextInjectable(@Context InjectedContextObject injectedContextObject)
        {
            return Response.ok(injectedContextObject.getMessage()).build();
        }
    }

    public static class InjectedContextObjectSupplier
        implements Supplier<InjectedContextObject>
    {
        @Override
        public InjectedContextObject get()
        {
            return new InjectedContextObject();
        }
    }

    public static class InjectedContextObject
    {
        @Inject
        private HttpServletRequest request;

        public String getMessage()
        {
            return String.format("%s %s", request.getServletPath(), INJECTED_MESSSAGE);
        }
    }

    @Path("/injectedresource2")
    public static class InjectedResource2
    {
        @GET
        public Response getContextInjectable(@Context InjectedContextObject injectedContextObject, @Context SecondInjectedContextObject secondInjectedContextObject)
        {
            return Response.ok(injectedContextObject.getMessage() + ":" + secondInjectedContextObject.getMessage()).build();
        }
    }

    public static class SecondInjectedContextObjectSupplier
        implements Supplier<SecondInjectedContextObject>
    {
        @Override
        public SecondInjectedContextObject get()
        {
            return new SecondInjectedContextObject();
        }
    }

    public static class SecondInjectedContextObject
    {
        @Inject
        private HttpServletRequest request;

        public String getMessage()
        {
            return String.format("%s %s", request.getServletPath(), SECOND_INJECTED_MESSSAGE);
        }
    }

    private static class DummyServlet
        extends HttpServlet
    {
    }
}
