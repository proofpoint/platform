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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.proofpoint.reporting.ReportedBeanRegistry.RegistrationInfo;
import org.testng.annotations.Test;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.reporting.ReportBinder.reportBinder;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestReportBinder
{
    public static final TestingAnnotation TESTING_ANNOTATION = new TestingAnnotation()
    {
        @Override
        public Class<? extends Annotation> annotationType()
        {
            return TestingAnnotation.class;
        }
    };

    private final ObjectName testingClassName;

    public TestReportBinder()
            throws MalformedObjectNameException
    {
        testingClassName = ObjectName.getInstance("com.example:type=TestingClass,name=TestingAnnotation");

        Guice.createInjector(new TestingModule());
    }

    @Test
    public void testGauge() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class);
                });
        assertReportRegistration(injector, "GaugeClass", ImmutableMap.of(), Optional.of(ImmutableSet.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithAnnotationClass() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).annotatedWith(TestingAnnotation.class);
                });
        assertReportRegistration(injector, "GaugeClass.TestingAnnotation", ImmutableMap.of(), Optional.of(ImmutableSet.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithNameAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).annotatedWith(Names.named("TestingAnnotation"));
                });
        assertReportRegistration(injector, "GaugeClass.TestingAnnotation", ImmutableMap.of(), Optional.of(ImmutableSet.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).annotatedWith(TESTING_ANNOTATION);
                });
        assertReportRegistration(injector, "GaugeClass.TestingAnnotation", ImmutableMap.of(), Optional.of(ImmutableSet.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithLegacyName() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).as(testingClassName.getCanonicalName());
                });
        assertReportRegistration(injector, "TestingClass.TestingAnnotation", ImmutableMap.of(), Optional.of(ImmutableSet.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeKey() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(com.google.inject.Key.get(GaugeClass.class));
                });
        assertReportRegistration(injector, "GaugeClass", ImmutableMap.of(), Optional.of(ImmutableSet.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeKeyWithAnnotationClass() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(com.google.inject.Key.get(GaugeClass.class, TestingAnnotation.class));
                });
        assertReportRegistration(injector, "GaugeClass.TestingAnnotation", ImmutableMap.of(), Optional.of(ImmutableSet.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeKeyWithNameAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(com.google.inject.Key.get(GaugeClass.class, Names.named("TestingAnnotation")));
                });
        assertReportRegistration(injector, "GaugeClass.TestingAnnotation", ImmutableMap.of(), Optional.of(ImmutableSet.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeKeyWithAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(com.google.inject.Key.get(GaugeClass.class, TESTING_ANNOTATION));
                });
        assertReportRegistration(injector, "GaugeClass.TestingAnnotation", ImmutableMap.of(), Optional.of(ImmutableSet.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeKeyWithLegacyName() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(com.google.inject.Key.get(GaugeClass.class)).as(testingClassName.getCanonicalName());
                });
        assertReportRegistration(injector, "TestingClass.TestingAnnotation", ImmutableMap.of(), Optional.of(ImmutableSet.of("Gauge", "Reported")));
    }

    @Test
    public void testReportedOnly() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(ReportedClass.class);
                });
        assertReportRegistration(injector, "ReportedClass", ImmutableMap.of(), Optional.of(ImmutableSet.of("Reported")));
    }

    @Test
    public void testManagedOnly() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(ManagedClass.class);
                });
        assertNoReportRegistration(injector);
    }

    @Test
    public void testNested() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(NestedClass.class);
                });
        injector.getInstance(NestedClass.class);
        assertReportRegistration(injector, "NestedClass", ImmutableMap.of(), Optional.of(ImmutableSet.of("Nested.Gauge", "Nested.Reported")));
    }

    @Test
    public void testFlatten() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(FlattenClass.class);
                });
        assertReportRegistration(injector, "FlattenClass", ImmutableMap.of(), Optional.of(ImmutableSet.of("Gauge", "Reported")));
    }

    @Test
    public void testBucketed() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(BucketedClass.class);
                });
        BucketedClass.assertProviderSupplied(injector.getInstance(BucketedClass.class));
        assertReportRegistration(injector, "BucketedClass", ImmutableMap.of(), Optional.of(ImmutableSet.of("Gauge", "Reported")));
    }

    @Test
    public void testNestedBucketed() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(NestedBucketedClass.class);
                });
        BucketedClass.assertProviderSupplied(injector.getInstance(NestedBucketedClass.class));
        BucketedClass.assertProviderSupplied(injector.getInstance(NestedBucketedClass.class).getNested());
        assertReportRegistration(injector, "NestedBucketedClass", ImmutableMap.of(), Optional.of(ImmutableSet.of(
                "Gauge", "Reported",
                "Nested.Gauge", "Nested.Reported"
        )));
    }

    @Test
    public void testFlattenBucketed() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(FlattenBucketedClass.class);
                });
        BucketedClass.assertProviderSupplied(injector.getInstance(FlattenBucketedClass.class).getFlatten());
        assertReportRegistration(injector, "FlattenBucketedClass", ImmutableMap.of(), Optional.of(ImmutableSet.of("Gauge", "Reported")));
    }

    @Test
    public void testDeepBucketed() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(DeepBucketedClass.class);
                });
        BucketedClass.assertProviderSupplied(injector.getInstance(DeepBucketedClass.class).getNested());
        BucketedClass.assertProviderSupplied(injector.getInstance(DeepBucketedClass.class).getFlatten());
        BucketedClass.assertProviderSupplied(injector.getInstance(DeepBucketedClass.class).getFlatten().getNested());
        assertReportRegistration(injector, "DeepBucketedClass", ImmutableMap.of(), Optional.of(ImmutableSet.of(
                "Gauge", "Reported",
                "Nested.Gauge", "Nested.Reported"
        )));
    }

    @Test
    public void testReportCollectionGeneratedName()
            throws MalformedObjectNameException
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.requireExplicitBindings();
                        binder.disableCircularProxies();
                        binder.bind(ReportCollectionFactory.class).in(Scopes.SINGLETON);
                        binder.bind(ReportExporter.class).asEagerSingleton();
                        newSetBinder(binder, Mapping.class);
                        binder.bind(ReportedBeanRegistry.class).in(Scopes.SINGLETON);

                        reportBinder(binder).bindReportCollection(KeyedDistribution.class).withGeneratedName();
                    }

                    @Provides
                    @Singleton
                    BucketIdProvider getBucketIdProvider()
                    {
                        return () -> 0;
                    }
                });
        KeyedDistribution keyedDistribution = injector.getInstance(KeyedDistribution.class);
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, "KeyedDistribution.Add", ImmutableMap.of("foo", "value", "bar", "false"), Optional.empty());
    }

    @Test
    public void testReportCollectionSpecifiedName()
            throws MalformedObjectNameException
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.requireExplicitBindings();
                        binder.disableCircularProxies();
                        binder.bind(ReportCollectionFactory.class).in(Scopes.SINGLETON);
                        binder.bind(ReportExporter.class).asEagerSingleton();
                        newSetBinder(binder, Mapping.class);
                        binder.bind(ReportedBeanRegistry.class).in(Scopes.SINGLETON);

                        reportBinder(binder).bindReportCollection(KeyedDistribution.class).as("com.example:type=\"Foo\\\",Bar\",a=b");
                    }

                    @Provides
                    @Singleton
                    BucketIdProvider getBucketIdProvider()
                    {
                        return () -> 0;
                    }
                });
        KeyedDistribution keyedDistribution = injector.getInstance(KeyedDistribution.class);
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, "Foo\",Bar.Add", ImmutableMap.of("foo", "value", "bar", "false", "a", "b"), Optional.empty());
    }

    @Test
    public void testReportCollectionAnnotation()
            throws MalformedObjectNameException
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.requireExplicitBindings();
                        binder.disableCircularProxies();
                        binder.bind(ReportCollectionFactory.class).in(Scopes.SINGLETON);
                        binder.bind(ReportExporter.class).asEagerSingleton();
                        newSetBinder(binder, Mapping.class);
                        binder.bind(ReportedBeanRegistry.class).in(Scopes.SINGLETON);

                        reportBinder(binder).bindReportCollection(KeyedDistribution.class).annotatedWith(TestingAnnotation.class).as("com.example:type=\"Foo\\\",Bar\",a=b");
                    }

                    @Provides
                    @Singleton
                    BucketIdProvider getBucketIdProvider()
                    {
                        return () -> 0;
                    }
                });
        KeyedDistribution keyedDistribution = injector.getInstance(com.google.inject.Key.get(KeyedDistribution.class, TestingAnnotation.class));
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, "Foo\",Bar.Add", ImmutableMap.of("foo", "value", "bar", "false", "a", "b"), Optional.empty());
    }

    @Test
    public void testReportCollectionNameAnnotated()
            throws MalformedObjectNameException
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.requireExplicitBindings();
                        binder.disableCircularProxies();
                        binder.bind(ReportCollectionFactory.class).in(Scopes.SINGLETON);
                        binder.bind(ReportExporter.class).asEagerSingleton();
                        newSetBinder(binder, Mapping.class);
                        binder.bind(ReportedBeanRegistry.class).in(Scopes.SINGLETON);

                        reportBinder(binder).bindReportCollection(KeyedDistribution.class).annotatedWith(Names.named("testing")).as("com.example:type=\"Foo\\\",Bar\",a=b");
                    }

                    @Provides
                    @Singleton
                    BucketIdProvider getBucketIdProvider()
                    {
                        return () -> 0;
                    }
                });
        KeyedDistribution keyedDistribution = injector.getInstance(com.google.inject.Key.get(KeyedDistribution.class, Names.named("testing")));
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, "Foo\",Bar.Add", ImmutableMap.of("foo", "value", "bar", "false", "a", "b"), Optional.empty());
    }

    private void assertNoReportRegistration(Injector injector)
    {
        ReportedBeanRegistry reportedBeanRegistry = injector.getInstance(ReportedBeanRegistry.class);
        assertEquals(reportedBeanRegistry.getReportedBeans(), ImmutableList.<RegistrationInfo>of());
    }

    private void assertReportRegistration(Injector injector, String namePrefix, Map<String, String> tags, Optional<Set<String>> expectedAttributes)
    {
        ReportedBeanRegistry reportedBeanRegistry = injector.getInstance(ReportedBeanRegistry.class);
        RegistrationInfo registrationInfo = getOnlyElement(reportedBeanRegistry.getReportedBeans());
        assertEquals(registrationInfo.getNamePrefix(), namePrefix, "name prefix");
        assertEquals(registrationInfo.getTags(), tags, "tags");

        if (expectedAttributes.isPresent()) {
            MBeanInfo mBeanInfo = registrationInfo.getReportedBean().getMBeanInfo();
            assertAttributes(mBeanInfo, expectedAttributes.get());
        }
    }

    private void assertAttributes(MBeanInfo mBeanInfo, Set<String> expected)
    {
        Builder<String> builder = ImmutableSet.builder();
        for (MBeanAttributeInfo mBeanAttributeInfo : mBeanInfo.getAttributes()) {
            String name = mBeanAttributeInfo.getName();
            builder.add(name);
            assertTrue(mBeanAttributeInfo.isReadable(), name + " is readable");
            assertFalse(mBeanAttributeInfo.isWritable(), name + " is writable");
        }
        assertEquals(builder.build(), expected);
    }

    public static class GaugeClass {
        @Gauge
        public int getGauge()
        {
            return 1;
        }

        @Reported
        public int getReported()
        {
            return 2;
        }

        @Managed
        public int getManaged()
        {
            return 3;
        }
    }

    private static class ReportedClass {
        @Reported
        public int getReported()
        {
            return 2;
        }
    }

    private static class ManagedClass {
        @Managed
        public int getManaged()
        {
            return 3;
        }
    }

    public static class NestedClass {
        private final GaugeClass nested = new GaugeClass();

        @Nested
        public GaugeClass getNested()
        {
            return nested;
        }
    }

    public static class FlattenClass {
        private final GaugeClass flatten = new GaugeClass();

        @Flatten
        public GaugeClass getFlatten()
        {
            return flatten;
        }
    }

    private class TestingModule implements Module
    {
        @Override
        public void configure(Binder binder)
        {
            binder.requireExplicitBindings();
            binder.disableCircularProxies();
            binder.bind(GaugeClass.class).in(Scopes.SINGLETON);
            binder.bind(GaugeClass.class).annotatedWith(TestingAnnotation.class).to(GaugeClass.class).in(Scopes.SINGLETON);
            binder.bind(GaugeClass.class).annotatedWith(Names.named("TestingAnnotation")).to(GaugeClass.class).in(Scopes.SINGLETON);
            binder.bind(GaugeClass.class).annotatedWith(TESTING_ANNOTATION).to(GaugeClass.class).in(Scopes.SINGLETON);
            binder.bind(ReportedClass.class).in(Scopes.SINGLETON);
            binder.bind(ManagedClass.class).in(Scopes.SINGLETON);
            binder.bind(NestedClass.class).in(Scopes.SINGLETON);
            binder.bind(FlattenClass.class).in(Scopes.SINGLETON);
            binder.bind(FlattenBucketedClass.class).in(Scopes.SINGLETON);

            binder.bind(ReportExporter.class).asEagerSingleton();
            binder.bind(GuiceReportExporter.class).asEagerSingleton();
            newSetBinder(binder, Mapping.class);
            binder.bind(ReportedBeanRegistry.class).in(Scopes.SINGLETON);
        }

        @Provides
        @Singleton
        BucketedClass getBucketedClass()
        {
            return BucketedClass.createBucketedClass();
        }

        @Provides
        @Singleton
        NestedBucketedClass getNestedBucketedClass()
        {
            return NestedBucketedClass.createNestedBucketedClass();
        }

        @Provides
        @Singleton
        DeepBucketedClass getDeepBucketedClass()
        {
            return DeepBucketedClass.createDeepBucketedClass();
        }

        @Provides
        @Singleton
        BucketIdProvider getBucketIdProvider()
        {
            return () -> 0;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    private @interface TestingAnnotation
    {}

    private static class BucketedClass
        extends Bucketed
    {
        private BucketedClass()
        {
        }

        static BucketedClass createBucketedClass()
        {
            return spy(new BucketedClass());
        }

        @Override
        protected GaugeClass createBucket()
        {
            return new GaugeClass();
        }

        public static void assertProviderSupplied(BucketedClass mock)
        {
            verify(mock).setBucketIdProvider(any(BucketIdProvider.class));
        }
    }

    private static class NestedBucketedClass
        extends BucketedClass
    {
        private BucketedClass nested = createBucketedClass();

        private NestedBucketedClass()
        {
        }

        private static NestedBucketedClass createNestedBucketedClass()
        {
            return spy(new NestedBucketedClass());
        }

        @Nested
        BucketedClass getNested()
        {
            return nested;
        }
    }

    private static class FlattenBucketedClass
    {
        private BucketedClass flatten = BucketedClass.createBucketedClass();

        @Flatten
        private BucketedClass getFlatten()
        {
            return flatten;
        }
    }

    private static class DeepBucketedClass
    {
        private NestedBucketedClass flatten = NestedBucketedClass.createNestedBucketedClass();
        private BucketedClass nested = BucketedClass.createBucketedClass();

        private DeepBucketedClass()
        {
        }

        private static DeepBucketedClass createDeepBucketedClass()
        {
            return spy(new DeepBucketedClass());
        }

        @Flatten
        private NestedBucketedClass getFlatten()
        {
            return flatten;
        }

        @Nested
        BucketedClass getNested()
        {
            return nested;
        }
    }

    private interface KeyedDistribution
    {
        SomeObject add(@Key("foo") String key, @NotNull @Key("bar") boolean bool);
    }

    public static class SomeObject
    {
        @Reported
        private int getValue()
        {
            return 0;
        }
    }
}
