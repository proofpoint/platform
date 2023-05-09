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
import com.google.auto.value.AutoValue;
import com.proofpoint.jaxrs.AccessDoesNotRequireAuthentication;
import com.proofpoint.node.NodeInfo;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/admin/version")
@AccessDoesNotRequireAuthentication
public class VersionResource
{
    private final VersionInfo versionInfo;

    @Inject
    public VersionResource(NodeInfo nodeInfo)
    {
        this.versionInfo = new AutoValue_VersionResource_VersionInfo(nodeInfo);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public VersionInfo getVersionInfo()
    {
        return versionInfo;
    }

    @AutoValue
    public abstract static class VersionInfo
    {
        abstract NodeInfo getNodeInfo();

        @JsonProperty
        String getApplication()
        {
            return getNodeInfo().getApplication();
        }

        @JsonProperty
        String getApplicationVersion()
        {
            String applicationVersion = getNodeInfo().getApplicationVersion();
            if (applicationVersion.isEmpty()) {
                return null;
            }
            return applicationVersion;
        }

        @JsonProperty
        String getPlatformVersion()
        {
            String platformVersion = getNodeInfo().getPlatformVersion();
            if (platformVersion.isEmpty()) {
                return null;
            }
            return platformVersion;
        }
    }
}
