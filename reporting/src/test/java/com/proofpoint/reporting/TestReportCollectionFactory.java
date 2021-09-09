/*
 * Copyright 2014 Proofpoint, Inc.
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
import com.proofpoint.testing.TestingTicker;
import jakarta.validation.constraints.NotNull;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.ObjectNameBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;
import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class TestReportCollectionFactory
{
    @Mock
    private ReportExporter reportExporter;
    private ReportCollectionFactory reportCollectionFactory;

    @Captor
    private ArgumentCaptor<Map<String, String>> tagCaptor;

    @BeforeMethod
    public void setup()
    {
        initMocks(this);
        reportCollectionFactory = new ReportCollectionFactory(reportExporter, new TestingTicker());
    }

    @Test
    public void testKeyedDistribution()
    {
        KeyedDistribution keyedDistribution = reportCollectionFactory.createReportCollection(KeyedDistribution.class);
        SomeObject someObject = keyedDistribution.add("value", false);

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SomeObject> reportCaptor = ArgumentCaptor.forClass(SomeObject.class);

        verify(reportExporter).export(reportCaptor.capture(), eq(false), stringCaptor.capture(), tagCaptor.capture());
        assertEquals(stringCaptor.getValue(), "KeyedDistribution.Add");
        assertEquals(tagCaptor.getValue(), ImmutableMap.of("foo", "value", "bar", "false"));
        assertSame(reportCaptor.getValue(), someObject);
    }

    @Test
    public void testNullValue()
    {
        KeyedDistribution keyedDistribution = reportCollectionFactory.createReportCollection(KeyedDistribution.class);
        SomeObject someObject = keyedDistribution.add(null, false);

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SomeObject> reportCaptor = ArgumentCaptor.forClass(SomeObject.class);

        verify(reportExporter).export(reportCaptor.capture(), eq(false), stringCaptor.capture(), tagCaptor.capture());
        assertEquals(stringCaptor.getValue(), "KeyedDistribution.Add");
        assertEquals(tagCaptor.getValue(), ImmutableMap.of("bar", "false"));
        assertSame(reportCaptor.getValue(), someObject);
    }

    @Test
    public void testOptionalValue()
    {
        OptionalKeyedDistribution optionalKeyedDistribution = reportCollectionFactory.createReportCollection(OptionalKeyedDistribution.class);
        SomeObject someObject = optionalKeyedDistribution.add(Optional.empty(), Optional.of(false));

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SomeObject> reportCaptor = ArgumentCaptor.forClass(SomeObject.class);

        verify(reportExporter).export(reportCaptor.capture(), eq(false), stringCaptor.capture(), tagCaptor.capture());
        assertEquals(stringCaptor.getValue(), "OptionalKeyedDistribution.Add");
        assertEquals(tagCaptor.getValue(), ImmutableMap.of("bar", "false"));
        assertSame(reportCaptor.getValue(), someObject);
    }

    private interface KeyedDistribution
    {
        SomeObject add(@Key("foo") String key, @NotNull @Key("bar") boolean bool);
    }

    private interface OptionalKeyedDistribution
    {
        SomeObject add(@Key("foo") Optional<String> key, @Key("bar") Optional<Boolean> bool);
    }

    @Test
    public void testPrefixedCollection()
    {
        KeyedDistribution keyedDistribution = reportCollectionFactory.createReportCollection(
                KeyedDistribution.class,
                true,
                "Prefix",
                ImmutableMap.of("a", "fooval", "b", "with\"quote", "c", "with,comma", "d", "with\\backslash")
        );
        SomeObject someObject = keyedDistribution.add("value", false);

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SomeObject> reportCaptor = ArgumentCaptor.forClass(SomeObject.class);

        verify(reportExporter).export(reportCaptor.capture(), eq(true), stringCaptor.capture(), tagCaptor.capture());
        assertEquals(stringCaptor.getValue(), "Prefix.Add");
        assertEquals(tagCaptor.getValue(), ImmutableMap.builder()
                .put("a", "fooval")
                .put("b", "with\"quote")
                .put("c", "with,comma")
                .put("d", "with\\backslash")
                .put("foo", "value")
                .put("bar", "false")
                .build());
        assertSame(reportCaptor.getValue(), someObject);
    }

    @Test
    public void testPrefixAbsentCollection()
    {
        KeyedDistribution keyedDistribution = reportCollectionFactory.createReportCollection(
                KeyedDistribution.class,
                false,
                null,
                ImmutableMap.of("a", "fooval", "b", "with\"quote", "c", "with,comma", "d", "with\\backslash")
        );
        SomeObject someObject = keyedDistribution.add("value", false);

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SomeObject> reportCaptor = ArgumentCaptor.forClass(SomeObject.class);

        verify(reportExporter).export(reportCaptor.capture(), eq(false), stringCaptor.capture(), tagCaptor.capture());
        assertEquals(stringCaptor.getValue(), "Add");
        assertEquals(tagCaptor.getValue(), ImmutableMap.builder()
                .put("a", "fooval")
                .put("b", "with\"quote")
                .put("c", "with,comma")
                .put("d", "with\\backslash")
                .put("foo", "value")
                .put("bar", "false")
                .build());
        assertSame(reportCaptor.getValue(), someObject);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*KeyedDistribution\\.add\\(java\\.lang\\.String, boolean\\) @Key\\(\"foo\"\\) duplicates tag on entire report collection.*")
    public void testPrefixedCollectionWithConflictingTags()
    {
        reportCollectionFactory.createReportCollection(
                KeyedDistribution.class,
                true,
                "Prefix",
                ImmutableMap.of("foo", "fooval")
        );
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testLegacyNamedCollection()
    {
        String name = new ObjectNameBuilder("com.example")
                .withProperty("a", "fooval")
                .withProperty("b", "with\"quote")
                .withProperty("c", "with,comma")
                .withProperty("d", "with\\backslash")
                .build();
        KeyedDistribution keyedDistribution = reportCollectionFactory.createReportCollection(KeyedDistribution.class, name);
        SomeObject someObject = keyedDistribution.add("value", false);

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SomeObject> reportCaptor = ArgumentCaptor.forClass(SomeObject.class);

        verify(reportExporter).export(reportCaptor.capture(), eq(false), stringCaptor.capture(), tagCaptor.capture());
        assertEquals(stringCaptor.getValue(), "Add");
        assertEquals(tagCaptor.getValue(), ImmutableMap.builder()
                .put("a", "fooval")
                .put("b", "with\"quote")
                .put("c", "with,comma")
                .put("d", "with\\backslash")
                .put("foo", "value")
                .put("bar", "false")
                .build());
        assertSame(reportCaptor.getValue(), someObject);
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "com\\.proofpoint\\.reporting\\.TestReportCollectionFactory\\$MissingParameterName\\.add\\(java\\.lang\\.String, boolean\\) parameter 2 has no @com.proofpoint.reporting.Key annotation")
    public void testNoParameterNames()
    {
        reportCollectionFactory.createReportCollection(MissingParameterName.class);
    }

    private interface MissingParameterName
    {
        SomeObject add(@Key("foo") String key, boolean bool);
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "com\\.proofpoint\\.reporting\\.TestReportCollectionFactory\\$NotConstructable\\.add\\(java\\.lang\\.String, boolean\\) return type ConstructorNeedsArgument has no public no-arg constructor")
    public void testReturnTypeNotConstructable()
    {
        reportCollectionFactory.createReportCollection(NotConstructable.class);
    }

    private interface NotConstructable
    {
        ConstructorNeedsArgument add(@Key("foo") String key, @Key("bar") boolean bool);
    }

    @Test
    public void testNoParameters()
    {
        TrackInstantiation.reset();
        NoParameters noParameters = reportCollectionFactory.createReportCollection(NoParameters.class);

        TrackInstantiation.assertInstantiated();

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TrackInstantiation> reportCaptor = ArgumentCaptor.forClass(TrackInstantiation.class);

        verify(reportExporter).export(reportCaptor.capture(), eq(false), stringCaptor.capture(), tagCaptor.capture());
        assertEquals(stringCaptor.getValue(), "NoParameters.Add");
        assertEquals(tagCaptor.getValue(), ImmutableMap.of());
        assertNotNull(reportCaptor.getValue());

        assertNotNull(noParameters.add());
    }

    @Test
    public void testPrefixedNoParameters()
    {
        TrackInstantiation.reset();
        NoParameters noParameters = reportCollectionFactory.createReportCollection(
                NoParameters.class,
                true,
                "Prefix",
                ImmutableMap.of("a", "fooval", "b", "with\"quote", "c", "with,comma", "d", "with\\backslash")
        );

        TrackInstantiation.assertInstantiated();

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TrackInstantiation> reportCaptor = ArgumentCaptor.forClass(TrackInstantiation.class);

        verify(reportExporter).export(reportCaptor.capture(), eq(true), stringCaptor.capture(), tagCaptor.capture());
        assertEquals(stringCaptor.getValue(), "Prefix.Add");
        assertEquals(tagCaptor.getValue(), ImmutableMap.builder()
                .put("a", "fooval")
                .put("b", "with\"quote")
                .put("c", "with,comma")
                .put("d", "with\\backslash")
                .build());
        assertNotNull(reportCaptor.getValue());

        assertNotNull(noParameters.add());
    }

    @Test
    public void testPrefixAbsentNoParameters()
    {
        TrackInstantiation.reset();
        NoParameters noParameters = reportCollectionFactory.createReportCollection(
                NoParameters.class,
                false,
                null,
                ImmutableMap.of("a", "fooval", "b", "with\"quote", "c", "with,comma", "d", "with\\backslash")
        );

        TrackInstantiation.assertInstantiated();

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TrackInstantiation> reportCaptor = ArgumentCaptor.forClass(TrackInstantiation.class);

        verify(reportExporter).export(reportCaptor.capture(), eq(false), stringCaptor.capture(), tagCaptor.capture());
        assertEquals(stringCaptor.getValue(), "Add");
        assertEquals(tagCaptor.getValue(), ImmutableMap.builder()
                .put("a", "fooval")
                .put("b", "with\"quote")
                .put("c", "with,comma")
                .put("d", "with\\backslash")
                .build());
        assertNotNull(reportCaptor.getValue());

        assertNotNull(noParameters.add());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testLegacyNamedNoParameters()
    {
        TrackInstantiation.reset();
        String name = new ObjectNameBuilder("com.example")
                .withProperty("a", "fooval")
                .withProperty("b", "with\"quote")
                .withProperty("c", "with,comma")
                .withProperty("d", "with\\backslash")
                .build();
        NoParameters noParameters = reportCollectionFactory.createReportCollection(NoParameters.class, name);

        TrackInstantiation.assertInstantiated();

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TrackInstantiation> reportCaptor = ArgumentCaptor.forClass(TrackInstantiation.class);

        verify(reportExporter).export(reportCaptor.capture(), eq(false), stringCaptor.capture(), tagCaptor.capture());
        assertEquals(stringCaptor.getValue(), "Add");
        assertEquals(tagCaptor.getValue(), ImmutableMap.builder()
                .put("a", "fooval")
                .put("b", "with\"quote")
                .put("c", "with,comma")
                .put("d", "with\\backslash")
                .build());
        assertNotNull(reportCaptor.getValue());

        assertNotNull(noParameters.add());
    }

    @Test
    public void testEquivalence()
    {
        equivalenceTester()
                .addEquivalentGroup(reportCollectionFactory.createReportCollection(KeyedDistribution.class))
                .addEquivalentGroup(reportCollectionFactory.createReportCollection(KeyedDistribution.class))
                .check();
    }

    private interface NoParameters
    {
        TrackInstantiation add();
    }

    private static class TrackInstantiation
    {
        private static final AtomicBoolean isInstantiated = new AtomicBoolean();

        public TrackInstantiation()
        {
            checkState(!isInstantiated.get());
            isInstantiated.set(true);
        }

        public static void reset()
        {
            isInstantiated.set(false);
        }

        public static void assertInstantiated()
        {
            assertTrue(isInstantiated.get());
        }
    }

    public static class SomeObject
    {
    }

    public static class ConstructorNeedsArgument
    {
        public ConstructorNeedsArgument(int something)
        {
        }
    }
}
