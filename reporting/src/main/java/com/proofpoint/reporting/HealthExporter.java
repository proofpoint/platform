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

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import javax.management.InstanceAlreadyExistsException;

import static java.util.Objects.requireNonNull;

public class HealthExporter
{
    private final HealthBeanRegistry registry;

    @Inject
    HealthExporter(HealthBeanRegistry registry)
    {
        this.registry = requireNonNull(registry, "registry is null");
    }

    public void export(@Nullable String instanceName, Object object)
            throws InstanceAlreadyExistsException
    {
        HealthBean healthBean = HealthBean.forTarget(object);
        for (HealthBeanAttribute attribute : healthBean.attributes()) {
            StringBuilder sb = new StringBuilder(attribute.getDescription());
            if (instanceName != null) {
                sb.append(" (").append(instanceName).append(")");
            }
            registry.register(attribute, sb.toString());
        }
    }
}
