/*
 * Copyright 2013 Proofpoint, Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.proofpoint.reporting.HealthStatusRepresentation.Status;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

@Path("/admin/health")
public class HealthResource
{
    private static final Pattern VALID_REASON_PATTERN = Pattern.compile("^([^\\00-\\x1f]*)");
    private final HealthBeanRegistry healthBeanRegistry;

    @Inject
    HealthResource(HealthBeanRegistry healthBeanRegistry)
    {
        this.healthBeanRegistry = checkNotNull(healthBeanRegistry, "healthBeanRegistry is null");
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public HealthRegistrationsRepresentation getHealthRegistrations()
    {
        ImmutableList.Builder<HealthCheckRegistrationRepresentation> builder = ImmutableList.builder();
        for (String description : healthBeanRegistry.getHealthAttributes().keySet()) {
            builder.add(new HealthCheckRegistrationRepresentation(description));
        }

        return new HealthRegistrationsRepresentation(builder.build());
    }

    @GET
    @Path("check")
    @Produces(MediaType.APPLICATION_JSON)
    public List<HealthStatusRepresentation> checkStatus()
    {
        ImmutableList.Builder<HealthStatusRepresentation> builder = ImmutableList.builder();
        for (Entry<String, HealthBeanAttribute> entry : healthBeanRegistry.getHealthAttributes().entrySet()) {
            String reason;
            Status status = Status.ERROR;

            try {
                reason = entry.getValue().getValue();
            }
            catch (AttributeNotFoundException e) {
                status = Status.UNKNOWN;
                reason = "Health check attribute not found";
            }
            catch (MBeanException | ReflectionException e) {
                status = Status.UNKNOWN;
                reason = e.getCause().getMessage();
            }

            if (reason == null) {
                status = Status.OK;
            }
            else {
                // Strip all but first line
                Matcher matcher = VALID_REASON_PATTERN.matcher(reason);
                checkState(matcher.find());
                reason = matcher.group(1);
            }

            builder.add(new HealthStatusRepresentation(entry.getKey(), status, reason));
        }
        return builder.build();
    }
}
