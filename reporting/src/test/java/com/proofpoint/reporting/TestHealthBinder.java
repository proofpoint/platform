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

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import jakarta.inject.Qualifier;
import org.testng.annotations.Test;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Nested;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.reporting.HealthBinder.healthBinder;
import static com.proofpoint.testing.Assertions.assertEqualsIgnoreOrder;

public class TestHealthBinder
{
    @Test
    public void testHealth() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    healthBinder(binder).export(HealthClass.class);
                });
        assertHealthRegistration(injector, Set.of("Health check", "Not Bean Getter"));
    }

    @Test
    public void testHealthWithAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    healthBinder(binder).export(HealthClass.class).annotatedWith(TestingAnnotation.class);
                });
        assertHealthRegistration(injector, Set.of("Health check (TestingAnnotation)", "Not Bean Getter (TestingAnnotation)"));
    }

    @Test
    public void testHealthWithNameAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    healthBinder(binder).export(HealthClass.class).annotatedWith(Names.named("TestingAnnotation"));
                });
        assertHealthRegistration(injector, Set.of("Health check (TestingAnnotation)", "Not Bean Getter (TestingAnnotation)"));
    }

    @Test
    public void testHealthWithName() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    healthBinder(binder).export(HealthClass.class).withNameSuffix("Specified name");
                });
        assertHealthRegistration(injector, Set.of("Health check (Specified name)", "Not Bean Getter (Specified name)"));
    }

    @Test
    public void testNested() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    healthBinder(binder).export(NestedClass.class);
                });
        injector.getInstance(NestedClass.class);
        assertHealthRegistration(injector, Set.of("Health check", "Not Bean Getter"));
    }

    @Test
    public void testFlatten() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                binder -> {
                    healthBinder(binder).export(FlattenClass.class);
                });
        assertHealthRegistration(injector, Set.of("Health check", "Not Bean Getter"));
    }

    private static void assertHealthRegistration(Injector injector, Set<String> expectedAttributes)
    {
        HealthBeanRegistry registry = injector.getInstance(HealthBeanRegistry.class);

        assertEqualsIgnoreOrder(registry.getHealthAttributes().keySet(), expectedAttributes);
    }

    public static class HealthClass
    {
        @HealthCheck("Health check")
        public int getHealth()
        {
            return 1;
        }
        
        @HealthCheck("Not Bean Getter")
        public String notBeanGetter()
        {
            return null;
        }
    }

    public static class NestedClass {
        private final HealthClass nested = new HealthClass();

        @Nested
        public HealthClass getNested()
        {
            return nested;
        }
    }

    public static class FlattenClass {
        private final HealthClass flatten = new HealthClass();

        @Flatten
        public HealthClass getFlatten()
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
            binder.bind(HealthClass.class).in(Scopes.SINGLETON);
            binder.bind(HealthClass.class).annotatedWith(TestingAnnotation.class).to(HealthClass.class).in(Scopes.SINGLETON);
            binder.bind(HealthClass.class).annotatedWith(Names.named("TestingAnnotation")).to(HealthClass.class).in(Scopes.SINGLETON);
            binder.bind(NestedClass.class).in(Scopes.SINGLETON);
            binder.bind(FlattenClass.class).in(Scopes.SINGLETON);

            binder.bind(HealthExporter.class).asEagerSingleton();
            binder.bind(GuiceHealthExporter.class).asEagerSingleton();
            newSetBinder(binder, HealthMapping.class);
            binder.bind(HealthBeanRegistry.class).in(Scopes.SINGLETON);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    private @interface TestingAnnotation
    {}
}
