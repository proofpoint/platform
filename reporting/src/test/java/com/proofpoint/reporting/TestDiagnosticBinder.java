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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.proofpoint.reporting.DiagnosticBeanRegistry.RegistrationInfo;
import org.testng.annotations.Test;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Nested;

import javax.inject.Qualifier;
import javax.management.MalformedObjectNameException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.reporting.DiagnosticBinder.diagnosticBinder;
import static org.testng.Assert.assertEquals;

public class TestDiagnosticBinder
{
    private static final TestingAnnotation TESTING_ANNOTATION = new TestingAnnotation()
    {
        @Override
        public Class<? extends Annotation> annotationType()
        {
            return TestingAnnotation.class;
        }
    };

    public TestDiagnosticBinder()
            throws MalformedObjectNameException
    {
        Guice.createInjector(new TestingModule());
    }

    @Test
    public void testDiagnostic() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    diagnosticBinder(binder).export(DiagnosticClass.class);
                });
        assertDiagnosticRegistration(injector, "DiagnosticClass", Optional.of(ImmutableSet.of("Diagnostic")));
    }

    @Test
    public void testDiagnosticWithAnnotationClass() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    diagnosticBinder(binder).export(DiagnosticClass.class).annotatedWith(TestingAnnotation.class);
                });
        assertDiagnosticRegistration(injector, "DiagnosticClass.TestingAnnotation", Optional.of(ImmutableSet.of("Diagnostic")));
    }

    @Test
    public void testDiagnosticWithNameAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    diagnosticBinder(binder).export(DiagnosticClass.class).annotatedWith(Names.named("TestingAnnotation"));
                });
        assertDiagnosticRegistration(injector, "DiagnosticClass.TestingAnnotation", Optional.of(ImmutableSet.of("Diagnostic")));
    }

    @Test
    public void testDiagnosticWithAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    diagnosticBinder(binder).export(DiagnosticClass.class).annotatedWith(TESTING_ANNOTATION);
                });
        assertDiagnosticRegistration(injector, "DiagnosticClass.TestingAnnotation", Optional.of(ImmutableSet.of("Diagnostic")));
    }

    @Test
    public void testDiagnosticKey() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    diagnosticBinder(binder).export(Key.get(DiagnosticClass.class));
                });
        assertDiagnosticRegistration(injector, "DiagnosticClass", Optional.of(ImmutableSet.of("Diagnostic")));
    }

    @Test
    public void testDiagnosticKeyWithAnnotationClass() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    diagnosticBinder(binder).export(Key.get(DiagnosticClass.class, TestingAnnotation.class));
                });
        assertDiagnosticRegistration(injector, "DiagnosticClass.TestingAnnotation", Optional.of(ImmutableSet.of("Diagnostic")));
    }

    @Test
    public void testDiagnosticKeyWithNameAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    diagnosticBinder(binder).export(Key.get(DiagnosticClass.class, Names.named("TestingAnnotation")));
                });
        assertDiagnosticRegistration(injector, "DiagnosticClass.TestingAnnotation", Optional.of(ImmutableSet.of("Diagnostic")));
    }

    @Test
    public void testDiagnosticKeyWithAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    diagnosticBinder(binder).export(Key.get(DiagnosticClass.class, TESTING_ANNOTATION));
                });
        assertDiagnosticRegistration(injector, "DiagnosticClass.TestingAnnotation", Optional.of(ImmutableSet.of("Diagnostic")));
    }

    @Test
    public void testDiagnosticWithNamePrefix() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    diagnosticBinder(binder).export(DiagnosticClass.class).withNamePrefix("TestingNamePrefix");
                });
        assertDiagnosticRegistration(injector, "TestingNamePrefix", Optional.of(ImmutableSet.of("Diagnostic")));
    }

    @Test
    public void testNested() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    diagnosticBinder(binder).export(NestedClass.class);
                });
        injector.getInstance(NestedClass.class);
        assertDiagnosticRegistration(injector, "NestedClass", Optional.of(ImmutableSet.of("Nested.Diagnostic")));
    }

    @Test
    public void testFlatten() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    diagnosticBinder(binder).export(FlattenClass.class);
                });
        assertDiagnosticRegistration(injector, "FlattenClass", Optional.of(ImmutableSet.of("Diagnostic")));
    }

    private void assertDiagnosticRegistration(Injector injector, String namePrefix, Optional<Set<String>> expectedAttributes)
    {
        DiagnosticBeanRegistry diagnosticBeanRegistry = injector.getInstance(DiagnosticBeanRegistry.class);
        RegistrationInfo registrationInfo = getOnlyElement(diagnosticBeanRegistry.getDiagnosticBeans());
        assertEquals(registrationInfo.getNamePrefix(), namePrefix, "name prefix");

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

    public static class DiagnosticClass
    {
        @Diagnostic
        public int getDiagnostic()
        {
            return 1;
        }
    }

    public static class NestedClass {
        private final DiagnosticClass nested = new DiagnosticClass();

        @Nested
        public DiagnosticClass getNested()
        {
            return nested;
        }
    }

    public static class FlattenClass {
        private final DiagnosticClass flatten = new DiagnosticClass();

        @Flatten
        public DiagnosticClass getFlatten()
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
            binder.bind(DiagnosticClass.class).in(Scopes.SINGLETON);
            binder.bind(DiagnosticClass.class).annotatedWith(TestingAnnotation.class).to(DiagnosticClass.class).in(Scopes.SINGLETON);
            binder.bind(DiagnosticClass.class).annotatedWith(Names.named("TestingAnnotation")).to(DiagnosticClass.class).in(Scopes.SINGLETON);
            binder.bind(DiagnosticClass.class).annotatedWith(TESTING_ANNOTATION).to(DiagnosticClass.class).in(Scopes.SINGLETON);
            binder.bind(NestedClass.class).in(Scopes.SINGLETON);
            binder.bind(FlattenClass.class).in(Scopes.SINGLETON);

            binder.bind(DiagnosticExporter.class).asEagerSingleton();
            binder.bind(GuiceDiagnosticExporter.class).asEagerSingleton();
            newSetBinder(binder, DiagnosticMapping.class);
            binder.bind(DiagnosticBeanRegistry.class).in(Scopes.SINGLETON);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    private @interface TestingAnnotation
    {}
}
