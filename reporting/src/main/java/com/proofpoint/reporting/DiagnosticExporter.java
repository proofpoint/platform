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

import com.google.common.annotations.Beta;
import com.google.inject.Inject;
import com.proofpoint.reporting.ReportException.Reason;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;

import static java.util.Objects.requireNonNull;

/**
 * Dynamically exports and unexports diagnostic-annotated objects to the diagnostics
 * subsystem.
 */
@Beta
public class DiagnosticExporter
{
    private final DiagnosticBeanRegistry registry;

    @Inject
    DiagnosticExporter(DiagnosticBeanRegistry registry)
    {
        this.registry = requireNonNull(registry, "registry is null");
    }

    /**
     * Export an object to the diagnostics system.
     *
     * @param object The object to export
     * @param namePrefix Name prefix for all metrics reported out of the object
     */
    public void export(Object object, String namePrefix)
    {
        ReportedBean reportedBean = ReportedBean.forTarget(object, Diagnostic.class);
        if (!reportedBean.getAttributes().isEmpty()) {
            try {
                registry.register(object, reportedBean, namePrefix);
            }
            catch (InstanceAlreadyExistsException e) {
                throw new ReportException(Reason.INSTANCE_ALREADY_EXISTS, e.getMessage());
            }
        }
    }

    /**
     * Undo the export of an object to the diagnostics system.
     *
     * @param object The object to unexport
     */
    public void unexportObject(Object object)
    {
        try {
            registry.unregister(object);
        }
        catch (InstanceNotFoundException e) {
            throw new ReportException(Reason.INSTANCE_NOT_FOUND, e.getMessage());
        }
    }
}
