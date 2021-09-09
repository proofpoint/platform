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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.proofpoint.reporting.ReportedBeanRegistry.RegistrationInfo;
import jakarta.validation.constraints.NotNull;
import org.testng.annotations.Test;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.reporting.BucketIdProvider.BucketId.bucketId;
import static com.proofpoint.reporting.ReportBinder.reportBinder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestReportBinder
{
    private static final TestingAnnotation TESTING_ANNOTATION = new TestingAnnotation()
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
        assertReportRegistration(injector, false, "GaugeClass", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithAnnotationClass() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).annotatedWith(TestingAnnotation.class);
                });
        assertReportRegistration(injector, false, "GaugeClass.TestingAnnotation", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithNameAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).annotatedWith(Names.named("TestingAnnotation"));
                });
        assertReportRegistration(injector, false, "GaugeClass.TestingAnnotation", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).annotatedWith(TESTING_ANNOTATION);
                });
        assertReportRegistration(injector, false, "GaugeClass.TestingAnnotation", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGaugeWithLegacyName() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).as(testingClassName.getCanonicalName());
                });
        assertReportRegistration(injector, false, "TestingClass.TestingAnnotation", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeKey() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(Key.get(GaugeClass.class));
                });
        assertReportRegistration(injector, false, "GaugeClass", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeKeyWithAnnotationClass() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(Key.get(GaugeClass.class, TestingAnnotation.class));
                });
        assertReportRegistration(injector, false, "GaugeClass.TestingAnnotation", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeKeyWithNameAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(Key.get(GaugeClass.class, Names.named("TestingAnnotation")));
                });
        assertReportRegistration(injector, false, "GaugeClass.TestingAnnotation", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeKeyWithAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(Key.get(GaugeClass.class, TESTING_ANNOTATION));
                });
        assertReportRegistration(injector, false, "GaugeClass.TestingAnnotation", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGaugeKeyWithLegacyName() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(Key.get(GaugeClass.class)).as(testingClassName.getCanonicalName());
                });
        assertReportRegistration(injector, false, "TestingClass.TestingAnnotation", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithNamePrefix() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).withNamePrefix("TestingNamePrefix");
                });
        assertReportRegistration(injector, false, "TestingNamePrefix", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithTags() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).withTags(ImmutableMap.of("foo", "bar", "baz", "quux"));
                });
        assertReportRegistration(injector, false, "GaugeClass", ImmutableMap.of("foo", "bar", "baz", "quux"), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithNamePrefixAndTags() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).withNamePrefix("TestingNamePrefix").withTags(ImmutableMap.of("foo", "bar", "baz", "quux"));
                });
        assertReportRegistration(injector, false, "TestingNamePrefix", ImmutableMap.of("foo", "bar", "baz", "quux"), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithApplicationPrefix() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).withApplicationPrefix();
                });
        assertReportRegistration(injector, true, "GaugeClass", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithApplicationAndNamePrefix() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).withApplicationPrefix().withNamePrefix("TestingNamePrefix");
                });
        assertReportRegistration(injector, true, "TestingNamePrefix", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithApplicationPrefixAndTags() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).withApplicationPrefix().withTags(ImmutableMap.of("foo", "bar", "baz", "quux"));
                });
        assertReportRegistration(injector, true, "GaugeClass", ImmutableMap.of("foo", "bar", "baz", "quux"), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testGaugeWithApplicationAndNamePrefixAndTags() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).withApplicationPrefix().withNamePrefix("TestingNamePrefix").withTags(ImmutableMap.of("foo", "bar", "baz", "quux"));
                });
        assertReportRegistration(injector, true, "TestingNamePrefix", ImmutableMap.of("foo", "bar", "baz", "quux"), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testReportedOnly() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(ReportedClass.class);
                });
        assertReportRegistration(injector, false, "ReportedClass", ImmutableMap.of(), Optional.of(Set.of("Reported")));
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
        assertReportRegistration(injector, false, "NestedClass", ImmutableMap.of(), Optional.of(Set.of("Nested.Gauge", "Nested.Reported")));
    }

    @Test
    public void testFlatten() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(FlattenClass.class);
                });
        assertReportRegistration(injector, false, "FlattenClass", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testBucketed() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(BucketedClass.class);
                });
        BucketedClass.assertProviderSupplied(injector.getInstance(BucketedClass.class));
        assertReportRegistration(injector, false, "BucketedClass", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
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
        assertReportRegistration(injector, false, "NestedBucketedClass", ImmutableMap.of(), Optional.of(Set.of(
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
        assertReportRegistration(injector, false, "FlattenBucketedClass", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
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
        assertReportRegistration(injector, false, "DeepBucketedClass", ImmutableMap.of(), Optional.of(Set.of(
                "Gauge", "Reported",
                "Nested.Gauge", "Nested.Reported"
        )));
    }

    @Test
    public void testDuplicate() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class);
                    reportBinder(binder).export(GaugeClass.class);
                });
        assertReportRegistration(injector, false, "GaugeClass", ImmutableMap.of(), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test
    public void testDuplicateAllFeatures() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    reportBinder(binder).export(GaugeClass.class).annotatedWith(TestingAnnotation.class).withApplicationPrefix().withNamePrefix("Prefix").withTags(ImmutableMap.of("foo", "bar"));
                    reportBinder(binder).export(GaugeClass.class).annotatedWith(TestingAnnotation.class).withApplicationPrefix().withNamePrefix("Prefix").withTags(ImmutableMap.of("foo", "bar"));
                });
        assertReportRegistration(injector, true, "Prefix", ImmutableMap.of("foo", "bar"), Optional.of(Set.of("Gauge", "Reported")));
    }

    @Test(expectedExceptions = ReportException.class)
    public void testDuplicateDifferentApplicationPrefixThrows()
            throws Throwable
    {
        try {
            Guice.createInjector(
                    new TestingModule(),
                    binder -> {
                        reportBinder(binder).export(GaugeClass.class).withNamePrefix("Prefix").withTags(ImmutableMap.of("foo", "bar"));
                        reportBinder(binder).export(GaugeClass.class).withApplicationPrefix().withNamePrefix("Prefix").withTags(ImmutableMap.of("foo", "bar"));
                    });
            fail("Expected CreationException");
        } catch (CreationException e) {
            throw e.getCause();
        }
    }

    @Test(expectedExceptions = ReportException.class)
    public void testDuplicateDifferentNamePrefixThrows()
            throws Throwable
    {
        try {
            Guice.createInjector(
                    new TestingModule(),
                    binder -> {
                        reportBinder(binder).export(GaugeClass.class).withApplicationPrefix().withNamePrefix("Prefix").withTags(ImmutableMap.of("foo", "bar"));
                        reportBinder(binder).export(GaugeClass.class).withApplicationPrefix().withNamePrefix("Other").withTags(ImmutableMap.of("foo", "bar"));
                    });
            fail("Expected CreationException");
        } catch (CreationException e) {
            throw e.getCause();
        }
    }

    @Test(expectedExceptions = ReportException.class)
    public void testDuplicateDifferentTagsThrows()
            throws Throwable
    {
        try {
            Guice.createInjector(
                    new TestingModule(),
                    binder -> {
                        reportBinder(binder).export(GaugeClass.class).withApplicationPrefix().withNamePrefix("Prefix").withTags(ImmutableMap.of("foo", "bar"));
                        reportBinder(binder).export(GaugeClass.class).withApplicationPrefix().withNamePrefix("Prefix");
                    });
            fail("Expected CreationException");
        } catch (CreationException e) {
            throw e.getCause();
        }
    }

    @Test(expectedExceptions = ReportException.class)
    public void testDuplicateDifferentAnnotationThrows()
            throws Throwable
    {
        try {
            GaugeClass gaugeClass = new GaugeClass();
            Guice.createInjector(
                    Modules.override(new TestingModule()).with(
                            binder -> {
                                binder.bind(GaugeClass.class).toInstance(gaugeClass);
                                binder.bind(GaugeClass.class).annotatedWith(TestingAnnotation.class).toInstance(gaugeClass);
                                reportBinder(binder).export(GaugeClass.class);
                                reportBinder(binder).export(GaugeClass.class).annotatedWith(TestingAnnotation.class);
                            }));
            fail("Expected CreationException");
        } catch (CreationException e) {
            throw e.getCause();
        }
    }

    @Test
    public void testReportCollectionDefaultName()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class);
                }
        );
        KeyedDistribution keyedDistribution = injector.getInstance(KeyedDistribution.class);
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, false, "KeyedDistribution.Add", ImmutableMap.of("foo", "value", "bar", "false"), Optional.empty());
    }

    @Test
    public void testReportCollectionWithAnnotationClass()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class).annotatedWith(TestingAnnotation.class);
                });
        KeyedDistribution keyedDistribution = injector.getInstance(Key.get(KeyedDistribution.class, TestingAnnotation.class));
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, false, "KeyedDistribution.Add", ImmutableMap.of("foo", "value", "bar", "false"), Optional.empty());
    }

    @Test
    public void testReportCollectionWithAnnotation()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class).annotatedWith(TESTING_ANNOTATION);
                });
        KeyedDistribution keyedDistribution = injector.getInstance(Key.get(KeyedDistribution.class, TESTING_ANNOTATION));
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, false, "KeyedDistribution.Add", ImmutableMap.of("foo", "value", "bar", "false"), Optional.empty());
    }

    @Test
    public void testReportCollectionWithNamePrefix()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class).withNamePrefix("TestingNamePrefix");
                }
        );
        KeyedDistribution keyedDistribution = injector.getInstance(KeyedDistribution.class);
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, false, "TestingNamePrefix.Add", ImmutableMap.of("foo", "value", "bar", "false"), Optional.empty());
    }

    @Test
    public void testReportCollectionWithNullNamePrefix()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class).withNamePrefix(null);
                }
        );
        KeyedDistribution keyedDistribution = injector.getInstance(KeyedDistribution.class);
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, false, "Add", ImmutableMap.of("foo", "value", "bar", "false"), Optional.empty());
    }

    @Test
    public void testReportCollectionWithTags()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class).withTags(ImmutableMap.of("a", "bar", "b", "quux"));
                }
        );
        KeyedDistribution keyedDistribution = injector.getInstance(KeyedDistribution.class);
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, false, "KeyedDistribution.Add", ImmutableMap.of("foo", "value", "bar", "false", "a", "bar", "b", "quux"), Optional.empty());
    }

    @Test(expectedExceptions = ProvisionException.class,
            expectedExceptionsMessageRegExp = ".*KeyedDistribution\\.add\\(String, boolean\\) @Key\\(\"foo\"\\) duplicates tag on entire report collection.*")
    public void testReportCollectionWithConflictingTags()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class).withTags(ImmutableMap.of("foo", "bar"));
                }
        );
        injector.getInstance(KeyedDistribution.class);
    }

    @Test
    public void testReportCollectionWithNamePrefixAndTags()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class)
                            .withNamePrefix("TestingNamePrefix")
                            .withTags(ImmutableMap.of("a", "bar", "b", "quux"));
                }
        );
        KeyedDistribution keyedDistribution = injector.getInstance(KeyedDistribution.class);
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, false, "TestingNamePrefix.Add", ImmutableMap.of("foo", "value", "bar", "false", "a", "bar", "b", "quux"), Optional.empty());
    }

    @Test
    public void testReportCollectionWithApplicationPrefix()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class).withApplicationPrefix();
                }
        );
        KeyedDistribution keyedDistribution = injector.getInstance(KeyedDistribution.class);
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, true, "KeyedDistribution.Add", ImmutableMap.of("foo", "value", "bar", "false"), Optional.empty());
    }

    @Test
    public void testReportCollectionWithApplicationAndNamePrefix()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class)
                            .withApplicationPrefix()
                            .withNamePrefix("TestingNamePrefix");
                }
        );
        KeyedDistribution keyedDistribution = injector.getInstance(KeyedDistribution.class);
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, true, "TestingNamePrefix.Add", ImmutableMap.of("foo", "value", "bar", "false"), Optional.empty());
    }

    @Test
    public void testReportCollectionWithApplicationPrefixAndTags()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class)
                            .withApplicationPrefix()
                            .withTags(ImmutableMap.of("a", "bar", "b", "quux"));
                }
        );
        KeyedDistribution keyedDistribution = injector.getInstance(KeyedDistribution.class);
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, true, "KeyedDistribution.Add", ImmutableMap.of("foo", "value", "bar", "false", "a", "bar", "b", "quux"), Optional.empty());
    }

    @Test
    public void testReportCollectionWithApplicationAndNamePrefixAndTags()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class)
                            .withApplicationPrefix()
                            .withNamePrefix("TestingNamePrefix")
                            .withTags(ImmutableMap.of("a", "bar", "b", "quux"));
                }
        );
        KeyedDistribution keyedDistribution = injector.getInstance(KeyedDistribution.class);
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, true, "TestingNamePrefix.Add", ImmutableMap.of("foo", "value", "bar", "false", "a", "bar", "b", "quux"), Optional.empty());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testReportCollectionWithLegacyName()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class).as("com.example:type=\"Foo\\\",Bar\",a=b");
                });
        KeyedDistribution keyedDistribution = injector.getInstance(KeyedDistribution.class);
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, false, "Foo\",Bar.Add", ImmutableMap.of("foo", "value", "bar", "false", "a", "b"), Optional.empty());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testReportCollectionWithLegacyNameAndAnnotation()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class).annotatedWith(TestingAnnotation.class).as("com.example:type=\"Foo\\\",Bar\",a=b");
                });
        KeyedDistribution keyedDistribution = injector.getInstance(Key.get(KeyedDistribution.class, TestingAnnotation.class));
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, false, "Foo\",Bar.Add", ImmutableMap.of("foo", "value", "bar", "false", "a", "b"), Optional.empty());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testReportCollectionLegacyNameAndNameAnnotated()
    {
        Injector injector = Guice.createInjector(
                new TestingCollectionModule(),
                binder -> {
                    reportBinder(binder).bindReportCollection(KeyedDistribution.class).annotatedWith(Names.named("testing")).as("com.example:type=\"Foo\\\",Bar\",a=b");
                });
        KeyedDistribution keyedDistribution = injector.getInstance(Key.get(KeyedDistribution.class, Names.named("testing")));
        keyedDistribution.add("value", false);
        assertReportRegistration(injector, false, "Foo\",Bar.Add", ImmutableMap.of("foo", "value", "bar", "false", "a", "b"), Optional.empty());
    }

    private void assertNoReportRegistration(Injector injector)
    {
        ReportedBeanRegistry reportedBeanRegistry = injector.getInstance(ReportedBeanRegistry.class);
        assertEquals(reportedBeanRegistry.getReportedBeans(), List.of());
    }

    private void assertReportRegistration(Injector injector, boolean applicationPrefix, String namePrefix, Map<String, String> tags, Optional<Set<String>> expectedAttributes)
    {
        ReportedBeanRegistry reportedBeanRegistry = injector.getInstance(ReportedBeanRegistry.class);
        RegistrationInfo registrationInfo = getOnlyElement(reportedBeanRegistry.getReportedBeans());
        assertEquals(registrationInfo.isApplicationPrefix(), applicationPrefix);
        assertEquals(registrationInfo.getNamePrefix(), namePrefix, "name prefix");
        assertEquals(registrationInfo.getTags(), tags, "tags");

        if (expectedAttributes.isPresent()) {
            Collection<ReportedBeanAttribute> attributes = registrationInfo.getReportedBean().getAttributes();
            assertAttributes(attributes, expectedAttributes.get());
        }
    }

    private void assertAttributes(Collection<ReportedBeanAttribute> attributes, Set<String> expected)
    {
        Builder<String> builder = ImmutableSet.builder();
        for (ReportedBeanAttribute attribute : attributes) {
            String name = attribute.getName();
            builder.add(name);
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

    private static class TestingModule implements Module
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
            return () -> bucketId(0, 0);
        }
    }

    private static class TestingCollectionModule implements Module
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
        }

        @Provides
        @Singleton
        BucketIdProvider getBucketIdProvider()
        {
            return () -> bucketId(0, 0);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    private @interface TestingAnnotation
    {}

    private static class BucketedClass
        extends Bucketed<GaugeClass>
    {
        private BucketedClass()
        {
        }

        static BucketedClass createBucketedClass()
        {
            return spy(new BucketedClass());
        }

        @Override
        protected GaugeClass createBucket(GaugeClass previousBucket)
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
        SomeObject add(@com.proofpoint.reporting.Key("foo") String key, @NotNull @com.proofpoint.reporting.Key("bar") boolean bool);
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
