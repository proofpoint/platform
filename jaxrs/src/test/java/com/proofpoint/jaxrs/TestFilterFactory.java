package com.proofpoint.jaxrs;

import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;

import java.util.ArrayList;
import java.util.List;

public class TestFilterFactory implements ResourceFilterFactory
{
    @Override
    public List<ResourceFilter> create(AbstractMethod am)
    {
        return getPassThroughResourceFilters();
    }

    private static List<ResourceFilter> getPassThroughResourceFilters()
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
                        response.setStatus(503);
                        return response;
                    }
                };
            }
        });
        return resourceFilters;
    }
}
