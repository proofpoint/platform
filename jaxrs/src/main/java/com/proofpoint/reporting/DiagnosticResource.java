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

import com.google.common.collect.ImmutableMap;
import com.proofpoint.jaxrs.AccessDoesNotRequireAuthentication;
import com.proofpoint.reporting.DiagnosticBeanRegistry.RegistrationInfo;

import javax.inject.Inject;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/admin/diagnostic")
@AccessDoesNotRequireAuthentication
public class DiagnosticResource
{
    private final DiagnosticBeanRegistry diagnosticBeanRegistry;

    @Inject
    public DiagnosticResource(DiagnosticBeanRegistry diagnosticBeanRegistry)
    {
        this.diagnosticBeanRegistry = requireNonNull(diagnosticBeanRegistry, "diagnosticBeanRegistry is null");
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Map<String, Object> get()
    {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        for (RegistrationInfo registrationInfo : diagnosticBeanRegistry.getDiagnosticBeans()) {
            for (ReportedBeanAttribute attribute : registrationInfo.getReportedBean().getAttributes()) {
                Object value = null;

                try {
                    value = attribute.getValue(null);
                }
                catch (AttributeNotFoundException | MBeanException | ReflectionException ignored) {
                }

                if (value != null) {
                    String name = registrationInfo.getNamePrefix() +
                            '.' +
                            attribute.getName();
                    builder.put(name, value);
                }
            }
        }
        return builder.build();
    }
}
