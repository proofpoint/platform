package com.proofpoint.discovery.client;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public class ServiceDescriptorRepresentation
{
    private final UUID id;
    private final String nodeId;
    private final String type;
    private final String pool;
    private final String location;
    private final ServiceState state;
    private final Map<String, String> properties;

    public static ServiceDescriptorRepresentation from(ServiceDescriptor serviceDescriptor)
    {
        return new ServiceDescriptorRepresentation(serviceDescriptor.getId(),
                serviceDescriptor.getNodeId(),
                serviceDescriptor.getType(),
                serviceDescriptor.getPool(),
                serviceDescriptor.getLocation(),
                serviceDescriptor.getState(),
                serviceDescriptor.getProperties());
    }

    @JsonCreator
    public ServiceDescriptorRepresentation(
            @JsonProperty("id") UUID id,
            @JsonProperty("nodeId") String nodeId,
            @JsonProperty("type") String type,
            @JsonProperty("pool") String pool,
            @JsonProperty("location") String location,
            @JsonProperty("state") ServiceState state,
            @JsonProperty("properties") Map<String, String> properties)
    {
        this.id = id;
        this.nodeId = nodeId;
        this.type = type;
        this.pool = pool;
        this.location = location;
        this.state = state;
        this.properties = (properties != null) ? ImmutableMap.copyOf(properties) : null;
    }

    @JsonProperty
    @NotNull(message = "Invalid ServiceDescriptor: id is null")
    public UUID getId()
    {
        return id;
    }

    @JsonProperty
    @NotNull(message = "Invalid ServiceDescriptor: nodeId is null")
    @Size(min=1, message = "Invalid ServiceDescriptor: nodeId is empty")
    public String getNodeId()
    {
        return nodeId;
    }

    @JsonProperty
    @NotNull(message = "Invalid ServiceDescriptor: type is null")
    @Size(min=1, message = "Invalid ServiceDescriptor: type is empty")
    public String getType()
    {
        return type;
    }

    @JsonProperty
    @NotNull(message = "Invalid ServiceDescriptor: pool is null")
    @Size(min=1, message = "Invalid ServiceDescriptor: pool is empty")
    public String getPool()
    {
        return pool;
    }

    @JsonProperty
    @NotNull(message = "Invalid ServiceDescriptor: location is null")
    @Size(min=1, message = "Invalid ServiceDescriptor: location is empty")
    public String getLocation()
    {
        return location;
    }

    @JsonProperty
    @NotNull(message = "Invalid ServiceDescriptor: state is null")
    public ServiceState getState()
    {
        return state;
    }

    @JsonProperty
    @NotNull(message = "Invalid ServiceDescriptor: properties is null")
    public Map<String, String> getProperties()
    {
        return properties;
    }

    public ServiceDescriptor toServiceDescriptor()
    {
        return new ServiceDescriptor(id, nodeId, type, pool, location, state, properties);
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

        ServiceDescriptorRepresentation that = (ServiceDescriptorRepresentation) o;

        if (!Objects.equal(id, that.id)) {
            return false;
        }
        if (!Objects.equal(nodeId, that.nodeId)) {
            return false;
        }
        if (!Objects.equal(type, that.type)) {
            return false;
        }
        if (!Objects.equal(pool, that.pool)) {
            return false;
        }
        if (!Objects.equal(location, that.location)) {
            return false;
        }
        if (!Objects.equal(state, that.state)) {
            return false;
        }
        if (!Objects.equal(properties, that.properties)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(id, nodeId, type, pool, location, state, properties);
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("ServiceDescriptorRepresentation");
        sb.append("{id=").append(id);
        sb.append(", nodeId=").append(nodeId);
        sb.append(", type='").append(type).append('\'');
        sb.append(", pool='").append(pool).append('\'');
        sb.append(", location='").append(location).append('\'');
        sb.append(", state='").append(state).append('\'');
        sb.append(", properties=").append(properties);
        sb.append('}');
        return sb.toString();
    }
}
