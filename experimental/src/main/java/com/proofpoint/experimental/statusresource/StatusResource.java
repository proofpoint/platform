package com.proofpoint.experimental.statusresource;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceSelector;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Path("/v1/status")
public class StatusResource
{
    private final Set<ServiceSelector> dependencies;
    private final AsyncHttpClient httpClient;

    @Inject
    public StatusResource(Set<ServiceSelector> dependencies, AsyncHttpClient httpClient)
    {
        if (dependencies != null) {
            this.dependencies = ImmutableSet.copyOf(dependencies);
        }
        else {
            this.dependencies = ImmutableSet.of();
        }

        this.httpClient = httpClient;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus()
    {
        boolean ready = true;
        ImmutableMap.Builder<String, Status> resultBuilder = ImmutableMap.builder();
        for (ServiceSelector dependency : dependencies)
        {
            String name = String.format("%s (%s)", dependency.getType(), dependency.getPool());
            List<ServiceDescriptor> services = dependency.selectAllServices();
            if (services.isEmpty()) {
                resultBuilder.put(name, new Status(false, "No discoverable service"));
                ready = false;
                continue;
            }

            Status status = checkDependency(services.iterator().next());
            resultBuilder.put(name, status);
            if (!status.isReachable()) {
                ready = false;
            }
        }

        return Response.ok(ImmutableMap.of("ready", ready, "dependencies", resultBuilder.build())).build();
    }

    private Status checkDependency(ServiceDescriptor service)
    {
        Preconditions.checkNotNull(service, "service cannot be null");
        if (service.getProperties().containsKey("http") || service.getProperties().containsKey("https")) {
            return checkHttpDependency(service);
        }

        return new Status(true, "Don't know how to validate this dependency; assume it's okay");
    }

    private Status checkHttpDependency(ServiceDescriptor service)
    {

        String uriString = service.getProperties().get("http");
        if (uriString == null) {
            uriString = service.getProperties().get("https");
        }

        if (uriString == null) {
            throw new IllegalArgumentException("Service cannot be contacted by HTTP");
        }

        try {
            httpClient.prepareGet(uriString).execute().get(1, TimeUnit.SECONDS);
        }
        catch (TimeoutException e) {
            return new Status(false, "Timed out after 1 second");
        }
        catch (Exception e) {
            return new Status(false, String.format("Failed with exception '%s'", e.getMessage()));
        }

        return new Status(true, "Reached HTTP ok");
    }


    public static class Status
    {
        private final boolean reachable;
        private final String details;

        @JsonCreator
        public Status(boolean reachable, String details)
        {
            this.reachable = reachable;
            this.details = details;
        }

        @JsonProperty
        public boolean isReachable()
        {
            return reachable;
        }

        @JsonProperty
        public String getDetails()
        {
            return details;
        }
    }
}
