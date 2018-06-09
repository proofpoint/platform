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
package com.proofpoint.reporting;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.proofpoint.reporting.ReportException.Reason;
import com.proofpoint.reporting.ReportedBeanRegistry.RegistrationInfo;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.Nested;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.Map;

import static com.google.common.collect.Iterables.getOnlyElement;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestReportExporter
{
    private static final Object TESTING_OBJECT = new Object()
    {
        @Gauge
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

    private static final ObjectName TESTING_OBJECT_NAME;

    static {
        ObjectName objectName = null;
        try {
            objectName = ObjectName.getInstance("com.proofpoint.reporting", "name", "TestingObject");
        }
        catch (MalformedObjectNameException ignored) {
        }
        TESTING_OBJECT_NAME = objectName;
    }

    private ReportedBeanRegistry registry;
    @Mock
    private BucketIdProvider bucketIdProvider;
    private ReportExporter reportExporter;

    @BeforeMethod
    public void setup()
    {
        initMocks(this);
        registry = new ReportedBeanRegistry();
        reportExporter = new ReportExporter(registry, bucketIdProvider);
    }

    @Test
    public void testExport()
    {
        reportExporter.export(TESTING_OBJECT, false, "TestingObject", ImmutableMap.of());
        assertExported(false, ImmutableMap.of());
    }

    @Test
    public void testExportPrefix()
    {
        reportExporter.export(TESTING_OBJECT, true, "TestingObject", ImmutableMap.of("foo", "bar"));
        assertExported(true, ImmutableMap.of("foo", "bar"));
    }

    @Test
    public void testExportNoAttributes()
            throws Exception
    {
        reportExporter.export(new Object(), false, "TestingObject", ImmutableMap.of());
        assertEquals(registry.getReportedBeans(), ImmutableList.of());
    }

    @Test
    public void testExportDuplicate()
            throws Exception
    {
        try {
            reportExporter.export(TESTING_OBJECT, false, "TestingObject", ImmutableMap.of());
            reportExporter.export(TESTING_OBJECT, false, "TestingObject", ImmutableMap.of());
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
        reportExporter.export(TESTING_OBJECT, false, "TestingObject", ImmutableMap.of());
        reportExporter.unexportObject(TESTING_OBJECT);
        assertEquals(registry.getReportedBeans(), ImmutableList.of());
    }

    @Test
    public void testUnexportObjectNotRegistered()
            throws Exception
    {
        try {
            reportExporter.unexportObject(TESTING_OBJECT);
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.INSTANCE_NOT_FOUND);
            assertEquals(e.getMessage(), "Testing Object not found");
        }
    }

    @Test
    public void testNotifyBucketIdProvider()
    {
        TestingBucketed bucketed = spy(new TestingBucketed());
        reportExporter.export(bucketed, false, "TestingBucketed", ImmutableMap.of());

        verify(bucketed).setBucketIdProvider(bucketIdProvider);
        verify(bucketed.getInnerBucketed()).setBucketIdProvider(bucketIdProvider);
    }

    @Test
    public void testLegacyExportString()
            throws Exception
    {
        reportExporter.export(TESTING_OBJECT_NAME.getCanonicalName(), TESTING_OBJECT);
        assertExported(false, ImmutableMap.of());
    }

    @Test
    public void testLegacyExportStringNoAttributes()
            throws Exception
    {
        reportExporter.export(TESTING_OBJECT_NAME.getCanonicalName(), new Object());
        assertEquals(registry.getReportedBeans(), ImmutableList.of());
    }

    @Test
    public void testLegacyExportStringMalformedName()
            throws Exception
    {
        try {
            reportExporter.export("TestingObject", TESTING_OBJECT);
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.MALFORMED_OBJECT_NAME);
            assertEquals(e.getMessage(), "Key properties cannot be empty");
        }
    }

    @Test
    public void testLegacyExportStringDuplicate()
            throws Exception
    {
        try {
            reportExporter.export(TESTING_OBJECT_NAME.getCanonicalName(), TESTING_OBJECT);
            reportExporter.export(TESTING_OBJECT_NAME.getCanonicalName(), TESTING_OBJECT);
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.INSTANCE_ALREADY_EXISTS);
            assertEquals(e.getMessage(), "com.proofpoint.reporting:name=TestingObject is already registered");
        }
    }

    @Test
    public void testLegacyExportObjectName()
            throws Exception
    {
        reportExporter.export(TESTING_OBJECT_NAME, TESTING_OBJECT);
        assertExported(false, ImmutableMap.of());
    }

    @Test
    public void testLegacyExportObjectNameNoAttributes()
            throws Exception
    {
        reportExporter.export(TESTING_OBJECT_NAME, new Object());
        assertEquals(registry.getReportedBeans(), ImmutableList.of());
    }

    @Test
    public void testLegacyExportObjectNameDuplicate()
            throws Exception
    {
        try {
            reportExporter.export(TESTING_OBJECT_NAME, TESTING_OBJECT);
            reportExporter.export(TESTING_OBJECT_NAME, TESTING_OBJECT);
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.INSTANCE_ALREADY_EXISTS);
            assertEquals(e.getMessage(), "com.proofpoint.reporting:name=TestingObject is already registered");
        }
    }

    @Test
    public void testLegacyUnexportString()
            throws Exception
    {
        reportExporter.export(TESTING_OBJECT_NAME, TESTING_OBJECT);
        reportExporter.unexport(TESTING_OBJECT_NAME.getCanonicalName());
        assertEquals(registry.getReportedBeans(), ImmutableList.of());
    }

    @Test
    public void testLegacyUnexportStringMalformedName()
            throws Exception
    {
        try {
            reportExporter.unexport("TestingObject");
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.MALFORMED_OBJECT_NAME);
            assertEquals(e.getMessage(), "Key properties cannot be empty");
        }
    }

    @Test
    public void testLegacyUnexportStringNotRegistered()
            throws Exception
    {
        try {
            reportExporter.unexport(TESTING_OBJECT_NAME.getCanonicalName());
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.INSTANCE_NOT_FOUND);
            assertEquals(e.getMessage(), "com.proofpoint.reporting:name=TestingObject not found");
        }
    }

    @Test
    public void testLegacyUnexportObjectName()
            throws Exception
    {
        reportExporter.export(TESTING_OBJECT_NAME, TESTING_OBJECT);
        reportExporter.unexport(TESTING_OBJECT_NAME);
        assertEquals(registry.getReportedBeans(), ImmutableList.of());
    }

    @Test
    public void testLegacyUnexportObjectNameNotRegistered()
            throws Exception
    {
        try {
            reportExporter.unexport(TESTING_OBJECT_NAME);
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.INSTANCE_NOT_FOUND);
            assertEquals(e.getMessage(), "com.proofpoint.reporting:name=TestingObject not found");
        }
    }

    @Test
    public void testLegacyNotifyBucketIdProvider()
    {
        TestingBucketed bucketed = spy(new TestingBucketed());
        reportExporter.export(TESTING_OBJECT_NAME, bucketed);

        verify(bucketed).setBucketIdProvider(bucketIdProvider);
        verify(bucketed.getInnerBucketed()).setBucketIdProvider(bucketIdProvider);
    }

    private void assertExported(boolean applicationPrefix, Map<Object, Object> expectedTags)
    {
        RegistrationInfo registrationInfo = getOnlyElement(registry.getReportedBeans());
        assertEquals(registrationInfo.isApplicationPrefix(), applicationPrefix);
        assertEquals(registrationInfo.getNamePrefix(), "TestingObject");
        assertEquals(registrationInfo.getTags(), expectedTags);
        assertEquals(namesOf(registrationInfo.getReportedBean().getAttributes()), namesOf(ReportedBean.forTarget(TESTING_OBJECT).getAttributes()));
    }

    private static Iterable<String> namesOf(Iterable<ReportedBeanAttribute> attributes)
    {
        return Iterables.transform(attributes, ReportedBeanAttribute::getName);
    }

    private static class TestingBucketed
        extends Bucketed<Object>
    {
        private InnerBucketed innerBucketed = spy(new InnerBucketed());

        @Override
        protected Object createBucket(Object previousBucket)
        {
            return new Object();
        }

        @Nested
        public InnerBucketed getInnerBucketed() {
            return innerBucketed;
        }
    }

    private static class InnerBucketed
        extends Bucketed<Object>
    {
        @Override
        protected Object createBucket(Object previousBucket)
        {
            return new Object();
        }
    }
}
