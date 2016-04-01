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

import com.google.common.collect.ImmutableMap;

import javax.management.InstanceAlreadyExistsException;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.collect.Maps.newConcurrentMap;

class HealthBeanRegistry
{
    private final ConcurrentMap<String, HealthBeanAttribute> healthAttributes = newConcurrentMap();

    Map<String, HealthBeanAttribute> getHealthAttributes()
    {
        return ImmutableMap.copyOf(healthAttributes);
    }

    public void register(HealthBeanAttribute healthBeanAttribute, String description)
            throws InstanceAlreadyExistsException
    {
        if (description == null) {
            throw new UnsupportedOperationException("Only explicit description supported at this time");
        }
        if (healthAttributes.putIfAbsent(description, healthBeanAttribute) != null) {
            throw new InstanceAlreadyExistsException(description + " is already registered");
        }
    }

    public void unregister(String description)
    {
        healthAttributes.remove(description);
    }
}
