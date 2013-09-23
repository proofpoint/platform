package com.proofpoint.jaxrs.util;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import com.proofpoint.jaxrs.TestResource;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;

import java.util.ArrayList;
import java.util.List;

import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;

public final class HttpTestUtils
{
    private HttpTestUtils ()
    {
    }

    public static TestingHttpServer createServer(final TestResource resource)
    {

        return Guice.createInjector(
                new TestingNodeModule(),
                new JaxrsModule(),
                new JsonModule(),
                new TestingHttpServerModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(TestResource.class).toInstance(resource);
                    }
                }).getInstance(TestingHttpServer.class);
    }

    public static TestingHttpServer createServerWithFilter (final TestResource resource, final ResourceFilterFactory filterFactory)
    {

        Injector injector = Guice.createInjector(
                new TestingNodeModule(),
                new JaxrsModule(),
                new JsonModule(),
                new TestingHttpServerModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(TestResource.class).toInstance(resource);
                        jaxrsBinder(binder)
                                .bindResourceFilterFactory(filterFactory.getClass());
                    }
                });
        return injector.getInstance(TestingHttpServer.class);
    }

    public static List<ResourceFilter> getPassThroughResourceFilters()
    {
        List<ResourceFilter> resourceFilters = new ArrayList<>();
        resourceFilters.add(new ResourceFilter()
        {
            @Override
            public ContainerRequestFilter getRequestFilter()
            {
                return new ContainerRequestFilter()
                {
                    @Override
                    public ContainerRequest filter(ContainerRequest request)
                    {
                        return request;
                    }
                };
            }

            @Override
            public ContainerResponseFilter getResponseFilter()
            {
                return new ContainerResponseFilter()
                {
                    @Override
                    public ContainerResponse filter(ContainerRequest request, ContainerResponse response)
                    {
                        response.setStatus(201);
                        return response;
                    }
                };
            }
        });
        return resourceFilters;
    }
}
