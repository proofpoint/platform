package com.proofpoint.jaxrs;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;
import com.proofpoint.http.server.TheAdminServlet;
import com.proofpoint.http.server.TheServlet;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import com.sun.jersey.spi.container.ResourceFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;

public class JaxrsTheServletProvider implements Provider<Map<String, String>>
{
    public static final String JERSEY_CONTAINER_REQUEST_FILTERS = "com.sun.jersey.spi.container.ContainerRequestFilters";
    public static final String JERSEY_RESOURCE_FILTERS = "com.sun.jersey.spi.container.ResourceFilters";
    private final Map<String, String> servletInitParameters;
    private Set<ResourceFilterFactory> resourceFilterFactorySet;

    public JaxrsTheServletProvider()
    {
        servletInitParameters = Maps.newHashMap();
        servletInitParameters.put(JERSEY_CONTAINER_REQUEST_FILTERS, OverrideMethodFilter.class.getName());
    }

    @Inject(optional = true)
    public void setResourceFilterFactories(Set<ResourceFilterFactory> resourceFilterFactorySet)
    {
        this.resourceFilterFactorySet = ImmutableSet.copyOf(resourceFilterFactorySet);
    }

    @Override
    public Map<String, String> get()
    {
        if ((resourceFilterFactorySet != null) && !resourceFilterFactorySet.isEmpty()) {
            servletInitParameters.put(JERSEY_RESOURCE_FILTERS, join (resourceFilterFactorySet));
        }
        return ImmutableMap.<String, String>builder().putAll(servletInitParameters).build();
    }

    private String join(Set<ResourceFilterFactory> resourceFilterFactorySet)
    {
        StringBuilder stringBuilder = new StringBuilder();
        boolean first = true;
        if ((resourceFilterFactorySet != null) && (!resourceFilterFactorySet.isEmpty())) {
            for (ResourceFilterFactory factory : resourceFilterFactorySet) {
                if (first) {
                    first = false;
                }
                else {
                    stringBuilder.append(",");
                }
                stringBuilder.append(factory.getClass().getName());
            }
        }
        return stringBuilder.toString();
    }
}
