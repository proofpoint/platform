/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.jmx;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationInspector;
import com.proofpoint.configuration.ConfigurationInspector.ConfigAttribute;
import com.proofpoint.configuration.ConfigurationInspector.ConfigRecord;
import com.proofpoint.jaxrs.AccessDoesNotRequireAuthentication;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

import static java.util.Objects.requireNonNull;

@Path("/admin/configuration")
@AccessDoesNotRequireAuthentication
public class ConfigurationResource
{
    private final ConfigurationFactory configurationFactory;

    @Inject
    public ConfigurationResource(ConfigurationFactory configurationFactory)
    {
        this.configurationFactory = requireNonNull(configurationFactory, "configurationFactory is null");
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, ConfigurationEntry> getConfiguration()
    {
        ConfigurationInspector configurationInspector = new ConfigurationInspector();
        ImmutableMap.Builder<String, ConfigurationEntry> builder = ImmutableMap.builder();

        for (ConfigRecord<?> record : configurationInspector.inspect(configurationFactory)) {
            for (ConfigAttribute attribute : record.getAttributes()) {
                builder.put(attribute.getPropertyName(),
                        new ConfigurationEntry(attribute));
            }
        }
        return builder.build();
    }

    public record ConfigurationEntry(ConfigAttribute configAttribute)
    {
        public ConfigurationEntry
        {
            requireNonNull(configAttribute, "configAttribute is null");
        }

        @JsonProperty
        String getDefaultValue()
        {
            return configAttribute.getDefaultValue();
        }

        @JsonProperty
        String getCurrentValue()
        {
            return configAttribute.getCurrentValue();
        }

        @JsonProperty
        String getDescription()
        {
            String description = configAttribute.getDescription();
            if (description.isEmpty()) {
                return null;
            }
            return description;
        }
    }
}
