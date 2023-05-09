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

import com.google.inject.Injector;
import jakarta.inject.Inject;

import javax.management.InstanceAlreadyExistsException;
import java.util.Set;

class GuiceHealthExporter
{
    private final HealthExporter healthExporter;

    @Inject
    GuiceHealthExporter(Set<HealthMapping> mappings, HealthExporter healthExporter, Injector injector)
            throws InstanceAlreadyExistsException
    {
        this.healthExporter = healthExporter;
        export(mappings, injector);
    }

    private void export(Set<HealthMapping> mappings, Injector injector)
            throws InstanceAlreadyExistsException
    {
        for (HealthMapping mapping : mappings) {
            Object object = injector.getInstance(mapping.getKey());
            healthExporter.export(mapping.getNameSuffix(), object);
        }
    }
}
