package com.proofpoint.swagger;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;


@Provider
public class ResponseCorsFilter implements ContainerResponseFilter
{
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
    {
        responseContext.getHeaders().add("Access-Control-Allow-Origin", requestContext.getHeaderString("Origin"));
    }
}
