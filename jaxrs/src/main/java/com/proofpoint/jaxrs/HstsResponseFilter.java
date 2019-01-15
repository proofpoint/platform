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
    private final JaxrsConfig config;

    @Inject
    public HstsResponseFilter(JaxrsConfig config)
    {
        this.config = requireNonNull(config, "jaxrsConfig is null");
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException
    {
        if (config.getHstsMaxAge() != null && requestContext.getSecurityContext().isSecure()) {
            StringBuilder headerValue = new StringBuilder(String.format("%s=%d", MAX_AGE,
                    config.getHstsMaxAge().roundTo(TimeUnit.SECONDS)));
            if (config.isIncludeSubDomains()) {
                headerValue.append(INCLUDE_SUB_DOMAINS);
            }
            if (config.isPreload()) {
                headerValue.append(PRELOAD);
            }
            responseContext.getHeaders().putSingle(HSTS, headerValue);
        }
    }
}
