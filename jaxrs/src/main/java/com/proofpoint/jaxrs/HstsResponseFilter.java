package com.proofpoint.jaxrs;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class HstsResponseFilter implements ContainerResponseFilter
{
    private static final String HSTS = "Strict-Transport-Security";
    private static final String MAX_AGE = "max-age";
    private static final String INCLUDE_SUB_DOMAINS = "; includeSubDomains";
    private static final String PRELOAD = "; preload";
    private final String headerValue;

    @Inject
    public HstsResponseFilter(JaxrsConfig config)
    {
        requireNonNull(config, "jaxrsConfig is null");
        if (config.getHstsMaxAge() != null) {
            StringBuilder headerValueBuilder = new StringBuilder(String.format("%s=%d", MAX_AGE,
                    config.getHstsMaxAge().roundTo(TimeUnit.SECONDS)));
            if (config.isIncludeSubDomains()) {
                headerValueBuilder.append(INCLUDE_SUB_DOMAINS);
            }
            if (config.isPreload()) {
                headerValueBuilder.append(PRELOAD);
            }
            headerValue = headerValueBuilder.toString();
        }
        else {
            headerValue = null;
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException
    {
        if (headerValue != null && requestContext.getSecurityContext().isSecure()) {
            responseContext.getHeaders().putSingle(HSTS, headerValue);
        }
    }
}
