/*
 * Copyright 2016 Proofpoint, Inc.
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
package com.proofpoint.reporting;

import com.proofpoint.jaxrs.AccessDoesNotRequireAuthentication;
import com.proofpoint.reporting.HealthBeanAttribute.Type;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import javax.management.MBeanException;
import javax.management.ReflectionException;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static java.util.Objects.requireNonNull;

@Path("/liveness")
@AccessDoesNotRequireAuthentication
public class LivenessResource
{
    private final HealthBeanRegistry healthBeanRegistry;

    @Inject
    public LivenessResource(HealthBeanRegistry healthBeanRegistry)
    {
        this.healthBeanRegistry = requireNonNull(healthBeanRegistry, "healthBeanRegistry is null");
    }

    @GET
    @Produces(TEXT_PLAIN)
    public Response get()
    {
        StringBuilder sb = new StringBuilder();
        healthBeanRegistry.getHealthAttributes().values().stream()
                .filter(healthBeanAttribute -> healthBeanAttribute.getType() == Type.RESTART)
                .forEach(attribute -> {
                    try {
                        String value = attribute.getValue();
                        if (value != null) {
                            sb.append(value).append('\n');
                        }
                    }
                    catch (MBeanException | ReflectionException e) {
                        sb.append(e.toString()).append('\n');
                    }
                });

        String failures = sb.toString();
        if (failures.isEmpty()) {
            return Response.ok("OK").build();
        }
        return Response.serverError().entity(failures).build();
    }
}
