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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.proofpoint.reporting.DiagnosticBeanRegistry.RegistrationInfo;
import com.proofpoint.reporting.ReportException.Reason;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.google.common.collect.Iterables.getOnlyElement;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestDiagnosticExporter
{
    private static final Object TESTING_OBJECT = new Object()
    {
        @Diagnostic
        public int getMetric()
        {
            return 1;
        }

        @Override
        public String toString()
        {
            return "Testing Object";
        }
    };

    private DiagnosticBeanRegistry registry;
    private DiagnosticExporter diagnosticExporter;

    @BeforeMethod
    public void setup()
    {
        registry = new DiagnosticBeanRegistry();
        diagnosticExporter = new DiagnosticExporter(registry);
    }

    @Test
    public void testExport()
    {
        diagnosticExporter.export(TESTING_OBJECT, "TestingObject");
        assertExported();
    }

    @Test
    public void testExportPrefix()
    {
        diagnosticExporter.export(TESTING_OBJECT, "TestingObject");
        assertExported();
    }

    @Test
    public void testExportNoAttributes()
            throws Exception
    {
        diagnosticExporter.export(new Object(), "TestingObject");
        assertEquals(registry.getDiagnosticBeans(), ImmutableList.of());
    }

    @Test
    public void testExportDuplicate()
            throws Exception
    {
        try {
            diagnosticExporter.export(TESTING_OBJECT, "TestingObject");
            diagnosticExporter.export(TESTING_OBJECT, "TestingObject");
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.INSTANCE_ALREADY_EXISTS);
            assertEquals(e.getMessage(), "Testing Object is already registered");
        }
    }

    @Test
    public void testUnexportObject()
            throws Exception
    {
        diagnosticExporter.export(TESTING_OBJECT, "TestingObject");
        diagnosticExporter.unexportObject(TESTING_OBJECT);
        assertEquals(registry.getDiagnosticBeans(), ImmutableList.of());
    }

    @Test
    public void testUnexportObjectNotRegistered()
            throws Exception
    {
        try {
            diagnosticExporter.unexportObject(TESTING_OBJECT);
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.INSTANCE_NOT_FOUND);
            assertEquals(e.getMessage(), "Testing Object not found");
        }
    }

    private void assertExported()
    {
        RegistrationInfo registrationInfo = getOnlyElement(registry.getDiagnosticBeans());
        assertEquals(registrationInfo.getNamePrefix(), "TestingObject");
        assertEquals(namesOf(registrationInfo.getReportedBean().getAttributes()), namesOf(ReportedBean.forTarget(TESTING_OBJECT, Diagnostic.class).getAttributes()));
    }

    private static Iterable<String> namesOf(Iterable<ReportedBeanAttribute> attributes)
    {
        return Iterables.transform(attributes, ReportedBeanAttribute::getName);
    }
}
