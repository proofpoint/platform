package com.proofpoint.discovery.client;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public class ServiceDescriptorListRepresentation
{
    private final String environment;
    private final List<ServiceDescriptorRepresentation> serviceDescriptorRepresentations;

    @JsonCreator
    public ServiceDescriptorListRepresentation(
            @JsonProperty("environment") String environment,
            @JsonProperty("services") List<ServiceDescriptorRepresentation> serviceDescriptorRepresentations)
    {
        this.environment = environment;
        this.serviceDescriptorRepresentations = serviceDescriptorRepresentations;
    }

    @JsonProperty
    @NotNull(message = "Invalid ServiceDescriptorList: environment is null")
    @Size(min=1, message = "Invalid ServiceDescriptorList: environment is empty")
    public String getEnvironment()
    {
        return environment;
    }

    @JsonProperty("services")
    @NotNull(message = "Invalid ServiceDescriptorList: serviceDescriptors is null")
    public List<ServiceDescriptorRepresentation> getServiceDescriptorRepresentations()
    {
        return serviceDescriptorRepresentations;
    }

    public List<ServiceDescriptor> getServiceDescriptors()
    {
        return ImmutableList.copyOf(Iterables.transform(serviceDescriptorRepresentations, toServiceDescriptor));
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ServiceDescriptorListRepresentation that = (ServiceDescriptorListRepresentation) o;

        if (!Objects.equal(environment, that.environment)) {
            return false;
        }
        if (!Objects.equal(serviceDescriptorRepresentations, that.serviceDescriptorRepresentations)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(environment, serviceDescriptorRepresentations);
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("ServiceDescriptorListRepresentation");
        sb.append("{environment='").append(environment).append('\'');
        sb.append(", serviceDescriptors=").append(serviceDescriptorRepresentations);
        sb.append('}');
        return sb.toString();
    }

    private static Function<ServiceDescriptorRepresentation, ServiceDescriptor> toServiceDescriptor = new Function<ServiceDescriptorRepresentation, ServiceDescriptor>() {
        @Override
        public ServiceDescriptor apply(ServiceDescriptorRepresentation serviceDescriptorRepresentation) {
            return ServiceDescriptor.from(serviceDescriptorRepresentation);
        }
    };
}
