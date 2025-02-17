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
import com.proofpoint.jaxrs.AccessDoesNotRequireAuthentication;
import com.proofpoint.node.NodeInfo;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import static java.util.Objects.requireNonNull;

@Path("/admin/version")
@AccessDoesNotRequireAuthentication
public class VersionResource
{
    private final VersionInfo versionInfo;

    @Inject
    public VersionResource(NodeInfo nodeInfo)
    {
        this.versionInfo = new VersionInfo(nodeInfo);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public VersionInfo getVersionInfo()
    {
        return versionInfo;
    }

    public record VersionInfo(NodeInfo nodeInfo)
    {
        public VersionInfo
        {
            requireNonNull(nodeInfo, "nodeInfo is null");
        }

        @JsonProperty
        String getApplication()
        {
            return nodeInfo.getApplication();
        }

        @JsonProperty
        String getApplicationVersion()
        {
            String applicationVersion = nodeInfo.getApplicationVersion();
            if (applicationVersion.isEmpty()) {
                return null;
            }
            return applicationVersion;
        }

        @JsonProperty
        String getPlatformVersion()
        {
            String platformVersion = nodeInfo.getPlatformVersion();
            if (platformVersion.isEmpty()) {
                return null;
            }
            return platformVersion;
        }
    }
}
