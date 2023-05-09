/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.discovery.client.announce;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceState;
import com.proofpoint.node.NodeInfo;
import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.UUID;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.proofpoint.discovery.client.ServiceDescriptor.serviceDescriptor;
import static java.util.Objects.requireNonNull;

public class ServiceAnnouncement
{
    private final UUID id = UUID.randomUUID();
    private final String type;
    private final Map<String, String> properties;
    private final String error;

    private ServiceAnnouncement(String type, Map<String, String> properties)
    {
        this.type = requireNonNull(type, "type is null");
        this.properties = ImmutableMap.copyOf(requireNonNull(properties, "properties is null"));
        error = null;
    }

    private ServiceAnnouncement(String error)
    {
        this.type = null;
        this.properties = null;
        this.error = requireNonNull(error, "error is null");
    }

    @JsonProperty
    public UUID getId()
    {
        return id;
    }

    @JsonProperty
    public String getType()
    {
        if (error != null) {
            throw new IllegalStateException(error);
        }
        return type;
    }

    @Nullable
    public String getError()
    {
        return error;
    }

    @JsonProperty
    public Map<String, String> getProperties()
    {
        if (error != null) {
            throw new IllegalStateException(error);
        }
        return properties;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("id", id)
                .add("type", type)
                .add("properties", properties)
                .add("error", error)
                .toString();
    }

    public ServiceDescriptor toServiceDescriptor(NodeInfo nodeInfo)
    {
        if (error != null) {
            throw new IllegalStateException(error);
        }
        return serviceDescriptor(type)
                .setId(id)
                .setNodeInfo(nodeInfo)
                .setLocation(nodeInfo.getLocation())
                .setState(ServiceState.RUNNING)
                .addProperties(properties)
                .build();
    }

    public static ServiceAnnouncementBuilder serviceAnnouncement(String type)
    {
        return new ServiceAnnouncementBuilder(type);
    }

    public static ServiceAnnouncement serviceAnnouncementError(String error)
    {
        return new ServiceAnnouncement(error);
    }

    public static class ServiceAnnouncementBuilder
    {
        private final String type;
        private final ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();

        private ServiceAnnouncementBuilder(String type)
        {
            this.type = type;
        }

        public ServiceAnnouncementBuilder addProperty(String key, String value)
        {
            requireNonNull(key, "key is null");
            requireNonNull(value, "value is null");
            properties.put(key, value);
            return this;
        }

        public ServiceAnnouncementBuilder addProperties(Map<String, String> properties)
        {
            requireNonNull(properties, "properties is null");
            this.properties.putAll(properties);
            return this;
        }

        public ServiceAnnouncement build()
        {
            return new ServiceAnnouncement(type, properties.build());
        }
    }
}

