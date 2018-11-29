/*
 * Copyright 2010 Proofpoint, Inc.
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
package com.proofpoint.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import org.testng.annotations.Test;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.proofpoint.configuration.ConfigBinder.bindConfig;
import static com.proofpoint.testing.Assertions.assertContainsAllOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

@SuppressWarnings("deprecation")
public class ConfigurationFactoryTest
{
    private static final ConfigurationDefaultingModule TEST_DEFAULTING_MODULE = new ConfigurationDefaultingModule()
    {
        @Override
        public Map<String, String> getConfigurationDefaults()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void configure(Binder binder)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString()
        {
            return "testing module";
        }
    };

    @Test
    public void testAnnotatedGettersThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(AnnotatedGetter.class));

            fail("Expected an exception in object creation due to conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(2);
            assertContainsAllOf(e.getMessage(), "not a valid setter", "getStringValue");
            assertContainsAllOf(e.getMessage(), "not a valid setter", "isBooleanValue");
        }
    }

    @Test
    public void testAnnotatedSetters()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(AnnotatedSetter.class));
        AnnotatedSetter annotatedSetter = injector.getInstance(AnnotatedSetter.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        assertNotNull(annotatedSetter);
        assertEquals(annotatedSetter.getStringValue(), "some value");
        assertEquals(annotatedSetter.isBooleanValue(), true);
    }

    @Test
    public void testModuleDefaults()
    {
        Map<String, String> moduleDefaults = ImmutableMap.of(
                "string-value", "some value",
                "boolean-value", "true"
        );
        Map<String, ConfigurationDefaultingModule> moduleDefaultSource = ImmutableMap.of(
                "string-value", TEST_DEFAULTING_MODULE,
                "boolean-value", TEST_DEFAULTING_MODULE
        );
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(ImmutableMap.of(), null, moduleDefaults, moduleDefaultSource, monitor, binder -> bindConfig(binder).bind(AnnotatedSetter.class));
        AnnotatedSetter annotatedSetter = injector.getInstance(AnnotatedSetter.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        assertNotNull(annotatedSetter);
        assertEquals(annotatedSetter.getStringValue(), "some value");
        assertEquals(annotatedSetter.isBooleanValue(), true);
    }

    @Test
    public void testApplicationDefaults()
    {
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-value", "some value",
                "boolean-value", "true"
        );
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(ImmutableMap.of(), applicationDefaults, null, null, monitor, binder -> bindConfig(binder).bind(AnnotatedSetter.class));
        AnnotatedSetter annotatedSetter = injector.getInstance(AnnotatedSetter.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        assertNotNull(annotatedSetter);
        assertEquals(annotatedSetter.getStringValue(), "some value");
        assertEquals(annotatedSetter.isBooleanValue(), true);
    }

    @Test
    public void testPropertiesOverrideModuleDefaults()
    {
        Map<String, String> properties = ImmutableMap.of(
                "string-value", "some value",
                "boolean-value", "false"
        );
        Map<String, String> moduleDefaults = ImmutableMap.of(
                "string-value", "some default value",
                "boolean-value", "true"
        );
        Map<String, ConfigurationDefaultingModule> moduleDefaultSource = ImmutableMap.of(
                "string-value", TEST_DEFAULTING_MODULE,
                "boolean-value", TEST_DEFAULTING_MODULE
        );
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, moduleDefaults, moduleDefaultSource, monitor, binder -> bindConfig(binder).bind(AnnotatedSetter.class));
        AnnotatedSetter annotatedSetter = injector.getInstance(AnnotatedSetter.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        assertNotNull(annotatedSetter);
        assertEquals(annotatedSetter.getStringValue(), "some value");
        assertEquals(annotatedSetter.isBooleanValue(), false);
    }

    @Test
    public void testApplicationDefaultsOverrideModuleDefaults()
    {
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-value", "some value",
                "boolean-value", "false"
        );
        Map<String, String> moduleDefaults = ImmutableMap.of(
                "string-value", "some default value",
                "boolean-value", "true"
        );
        Map<String, ConfigurationDefaultingModule> moduleDefaultSource = ImmutableMap.of(
                "string-value", TEST_DEFAULTING_MODULE,
                "boolean-value", TEST_DEFAULTING_MODULE
        );
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(ImmutableMap.of(), applicationDefaults, moduleDefaults, moduleDefaultSource, monitor, binder -> bindConfig(binder).bind(AnnotatedSetter.class));
        AnnotatedSetter annotatedSetter = injector.getInstance(AnnotatedSetter.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        assertNotNull(annotatedSetter);
        assertEquals(annotatedSetter.getStringValue(), "some value");
        assertEquals(annotatedSetter.isBooleanValue(), false);
    }

    @Test
    public void testPropertiesOverrideApplicationDefaults()
    {
        Map<String, String> properties = ImmutableMap.of(
                "string-value", "some value",
                "boolean-value", "false"
        );
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-value", "some default value",
                "boolean-value", "true"
        );
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, applicationDefaults, null, null, monitor, binder -> bindConfig(binder).bind(AnnotatedSetter.class));
        AnnotatedSetter annotatedSetter = injector.getInstance(AnnotatedSetter.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        assertNotNull(annotatedSetter);
        assertEquals(annotatedSetter.getStringValue(), "some value");
        assertEquals(annotatedSetter.isBooleanValue(), false);
    }

    @Test
    public void testConfigurationDespiteLegacyConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(LegacyConfigPresent.class));
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        assertNotNull(legacyConfigPresent);
        assertEquals(legacyConfigPresent.getStringA(), "this is a");
        assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationThroughLegacyConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, null, null, monitor,
                binder -> bindConfig(binder).bind(LegacyConfigPresent.class));
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
        assertNotNull(legacyConfigPresent);
        assertEquals(legacyConfigPresent.getStringA(), "this is a");
        assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithPrefixThroughLegacyConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("example.string-value", "this is a");
        properties.put("example.string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, null, null, monitor,
                binder -> bindConfig(binder).bind(LegacyConfigPresent.class).prefixedWith("example"));
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("Configuration property 'example.string-value' has been replaced. Use 'example.string-a' instead.");
        assertNotNull(legacyConfigPresent);
        assertEquals(legacyConfigPresent.getStringA(), "this is a");
        assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationThroughLegacyConfigOverridesModuleDefaults()
    {
        Map<String, String> properties = ImmutableMap.of(
                "string-value", "this is a",
                "string-b", "this is b"
        );
        Map<String, String> moduleDefaults = ImmutableMap.of(
                "string-a", "some default value",
                "string-b", "some other default value"
        );
        Map<String, ConfigurationDefaultingModule> moduleDefaultSource = ImmutableMap.of(
                "string-a", TEST_DEFAULTING_MODULE,
                "string-b", TEST_DEFAULTING_MODULE
        );
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, moduleDefaults, moduleDefaultSource, monitor, binder -> bindConfig(binder).bind(LegacyConfigPresent.class));
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
        assertNotNull(legacyConfigPresent);
        assertEquals(legacyConfigPresent.getStringA(), "this is a");
        assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationThroughLegacyConfigOverridesApplicationDefaults()
    {
        Map<String, String> properties = ImmutableMap.of(
                "string-value", "this is a",
                "string-b", "this is b"
        );
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-a", "some default value",
                "string-b", "some other default value"
        );
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, applicationDefaults, null, null, monitor, binder -> bindConfig(binder).bind(LegacyConfigPresent.class));
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
        assertNotNull(legacyConfigPresent);
        assertEquals(legacyConfigPresent.getStringA(), "this is a");
        assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithRedundantLegacyConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "this is a");
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(LegacyConfigPresent.class));

            fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(1);
            monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
            assertContainsAllOf(e.getMessage(), "string-value", "conflicts with property", "string-a");
        }
    }

    @Test
    public void testConfigurationWithRedundantDeprecatedConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "this is a");
        properties.put("deprecated-string-value", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(LegacyConfigPresent.class));

            fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(2);
            monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
            monitor.assertMatchingWarningRecorded("deprecated-string-value", "replaced", "Use 'string-a'");
            assertContainsAllOf(e.getMessage(), "string-value", "conflicts with property", "deprecated-string-value");
        }
    }

    @Test
    public void testConfigurationWithConflictingLegacyConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "this is the old value");
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(LegacyConfigPresent.class));

            fail("Expected an exception in object creation due to conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(1);
            monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
            assertContainsAllOf(e.getMessage(), "string-value", "conflicts with property", "string-a");
        }
    }

    @Test
    public void testLegacyModuleDefaultsThrows()
    {
        Map<String, String> moduleDefaults = ImmutableMap.of(
                "string-value", "some default value",
                "string-b", "some other default value"
        );
        Map<String, ConfigurationDefaultingModule> moduleDefaultSource = ImmutableMap.of(
                "string-value", TEST_DEFAULTING_MODULE,
                "string-b", TEST_DEFAULTING_MODULE
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.of(), null, moduleDefaults, moduleDefaultSource, monitor, binder -> bindConfig(binder).bind(LegacyConfigPresent.class));

            fail("Expected an exception in object creation due to conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            assertContainsAllOf(e.getMessage(), "Module default property", "string-value", "(from testing module) has been replaced", "string-a");
        }
    }

    @Test
    public void testLegacyApplicationDefaultsThrows()
    {
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-value", "some default value",
                "string-b", "some other default value"
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.of(), applicationDefaults, null, null, monitor, binder -> bindConfig(binder).bind(LegacyConfigPresent.class));

            fail("Expected an exception in object creation due to conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            assertContainsAllOf(e.getMessage(), "Application default property", "string-value", "has been replaced", "string-a");
        }
    }

    @Test
    public void testConfigurationDespiteDeprecatedConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(DeprecatedConfigPresent.class));
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        assertNotNull(deprecatedConfigPresent);
        assertEquals(deprecatedConfigPresent.getStringA(), "defaultA");
        assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationThroughDeprecatedConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(DeprecatedConfigPresent.class));
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-a", "deprecated and should not be used");
        assertNotNull(deprecatedConfigPresent);
        assertEquals(deprecatedConfigPresent.getStringA(), "this is a");
        assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testModuleDefaultsThroughDeprecatedConfig()
    {
        Map<String, String> moduleDefaults = ImmutableMap.of(
                "string-a", "this is a",
                "string-b", "this is b"
        );
        Map<String, ConfigurationDefaultingModule> moduleDefaultSource = ImmutableMap.of(
                "string-a", TEST_DEFAULTING_MODULE,
                "string-b", TEST_DEFAULTING_MODULE
        );
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(ImmutableMap.of(), null, moduleDefaults, moduleDefaultSource, monitor, binder -> bindConfig(binder).bind(DeprecatedConfigPresent.class));
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        assertNotNull(deprecatedConfigPresent);
        assertEquals(deprecatedConfigPresent.getStringA(), "this is a");
        assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testApplicationDefaultsThroughDeprecatedConfig()
    {
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-a", "this is a",
                "string-b", "this is b"
        );
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(ImmutableMap.of(), applicationDefaults, null, null, monitor, binder -> bindConfig(binder).bind(DeprecatedConfigPresent.class));
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        assertNotNull(deprecatedConfigPresent);
        assertEquals(deprecatedConfigPresent.getStringA(), "this is a");
        assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testDefunctPropertyInConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "this is a");
        properties.put("defunct-value", "this shouldn't work");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(DefunctConfigPresent.class));

            fail("Expected an exception in object creation due to use of defunct config");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Defunct property", "'defunct-value", "cannot be configured");
        }
    }

    @Test
    public void testDefunctPropertyInModuleDefaultsThrows()
    {
        Map<String, String> moduleDefaults = ImmutableMap.of(
                "string-value", "this is a",
                "defunct-value", "this shouldn't work"
        );
        Map<String, ConfigurationDefaultingModule> moduleDefaultSource = ImmutableMap.of(
                "string-value", TEST_DEFAULTING_MODULE,
                "defunct-value", TEST_DEFAULTING_MODULE
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.of(), null, moduleDefaults, moduleDefaultSource, monitor, binder -> bindConfig(binder).bind(DefunctConfigPresent.class));

            fail("Expected an exception in object creation due to use of defunct config");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Defunct property", "'defunct-value", "cannot have a module default (from testing module)");
        }
    }

    @Test
    public void testDefunctPropertyInApplicationDefaultsThrows()
    {
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-value", "this is a",
                "defunct-value", "this shouldn't work"
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.of(), applicationDefaults, null, null, monitor, binder -> bindConfig(binder).bind(DefunctConfigPresent.class));

            fail("Expected an exception in object creation due to use of defunct config");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Defunct property", "'defunct-value", "cannot have an application default");
        }
    }

    @Test
    public void testNoCoerceValueThrows()
    {
        Map<String, String> properties = new HashMap<>();
        properties.put("string-value", "has a value");
        properties.put("int-value", "not a number");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(BeanValidationClass.class));

            fail("Expected an exception in object creation due to coercion failure");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Could not coerce value 'not a number' to int", "'int-value'", "in order to call", "BeanValidationClass.setIntValue(int)");
        }
    }

    @Test
    public void testNoCoerceBoolValueThrows()
    {
        Map<String, String> properties = new HashMap<>();
        properties.put("string-value", "has a value");
        properties.put("boolean-value", "not a boolean");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(AnnotatedSetter.class));

            fail("Expected an exception in object creation due to coercion failure");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Could not coerce value 'not a boolean' to boolean", "'boolean-value'", "in order to call", "AnnotatedSetter.setBooleanValue(boolean)");
        }
    }

    @Test
    public void testSuccessfulBeanValidation()
    {
        Map<String, String> properties = new HashMap<>();
        properties.put("string-value", "has a value");
        properties.put("int-value", "50");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(BeanValidationClass.class));
        BeanValidationClass beanValidationClass = injector.getInstance(BeanValidationClass.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        assertNotNull(beanValidationClass);
        assertEquals(beanValidationClass.getStringValue(), "has a value");
        assertEquals(beanValidationClass.getIntValue(), 50);
    }

    @Test
    public void testFailedBeanValidationThrows()
    {
        Map<String, String> properties = ImmutableMap.of(
                // string-value left at invalid default
                "int-value", "5000"  // out of range
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(BeanValidationClass.class));

            fail("Expected an exception in object creation due to bean validation failure");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(2);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Invalid configuration property", "'int-value'", "must be less than or equal to 100", "BeanValidationClass");
            monitor.assertMatchingErrorRecorded("Missing required configuration property", "'string-value'", "BeanValidationClass");
        }
    }

    @Test
    public void testMapModuleDefaultsThrows()
    {
        Map<String, String> moduleDefaults = ImmutableMap.of(
                "map-a.k3", "this is a"
        );
        Map<String, ConfigurationDefaultingModule> moduleDefaultSource = ImmutableMap.of(
                "map-a.k3", TEST_DEFAULTING_MODULE
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.of(), null, moduleDefaults, moduleDefaultSource, monitor, binder -> bindConfig(binder).bind(MapConfigPresent.class));

            fail("Expected an exception in object creation due to use of application default for map config");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            assertContainsAllOf(e.getMessage(), "Cannot have module default property", "map-a.k3", "(from testing module) for a configuration map");
        }
    }

    @Test
    public void testMapValueModuleDefaultsThrows()
    {
        Map<String, String> moduleDefaults = ImmutableMap.of(
                "map-b.k3.string-value", "this is a"
        );
        Map<String, ConfigurationDefaultingModule> moduleDefaultSource = ImmutableMap.of(
                "map-b.k3.string-value", TEST_DEFAULTING_MODULE
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.of(), null, moduleDefaults, moduleDefaultSource, monitor, binder -> bindConfig(binder).bind(MapConfigPresent.class));

            fail("Expected an exception in object creation due to use of application default for map config");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            assertContainsAllOf(e.getMessage(), "Cannot have module default property", "map-b.k3.string-value", "(from testing module) for a configuration map");
        }
    }

    @Test
    public void testMapApplicationDefaultsThrows()
    {
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "map-a.k3", "this is a"
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.of(), applicationDefaults, null, null, monitor, binder -> bindConfig(binder).bind(MapConfigPresent.class));

            fail("Expected an exception in object creation due to use of application default for map config");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            assertContainsAllOf(e.getMessage(), "Cannot have application default property", "map-a.k3", "for a configuration map");
        }
    }

    @Test
    public void testMapValueApplicationDefaultsThrows()
    {
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "map-b.k3.string-value", "this is a"
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.of(), applicationDefaults, null, null, monitor, binder -> bindConfig(binder).bind(MapConfigPresent.class));

            fail("Expected an exception in object creation due to use of application default for map config");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            assertContainsAllOf(e.getMessage(), "Cannot have application default property", "map-b.k3.string-value", "for a configuration map");
        }
    }

    @Test
    public void testConfigurationThroughLegacyMapConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-value.k", "this is a");
        properties.put("map-b.k2", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(LegacyMapConfigPresent.class));
        LegacyMapConfigPresent legacyMapConfigPresent = injector.getInstance(LegacyMapConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("map-value", "replaced", "Use 'map-a'");
        assertNotNull(legacyMapConfigPresent);
        assertEquals(legacyMapConfigPresent.getMapA(), ImmutableMap.of("k", "this is a"));
        assertEquals(legacyMapConfigPresent.getMapB(), ImmutableMap.of("k2", "this is b"));
    }

    @Test
    public void testConfigurationWithRedundantLegacyMapConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-value.k", "this is a");
        properties.put("map-a.k3", "this is a");
        properties.put("map-b.k2", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(LegacyMapConfigPresent.class));

            fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(1);
            monitor.assertMatchingWarningRecorded("map-value", "replaced", "Use 'map-a'");
            assertContainsAllOf(e.getMessage(), "map-value", "conflicts with map property prefix", "map-a");
        }
    }

    @Test
    public void testConfigurationWithRedundantDeprecatedMapConfigThrows()
    {
        Map<String, String> properties = ImmutableMap.of(
                "map-value.k", "this is a",
                "deprecated-map-value.k3", "this is a",
                "map-b.k2", "this is b"
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(LegacyMapConfigPresent.class));

            fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(2);
            monitor.assertMatchingWarningRecorded("map-value", "replaced", "Use 'map-a'");
            monitor.assertMatchingWarningRecorded("deprecated-map-value", "replaced", "Use 'map-a'");
            assertContainsAllOf(e.getMessage(), "map-value", "conflicts with map property prefix", "deprecated-map-value");
        }
    }

    @Test
    public void testConfigurationThroughDeprecatedMapConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k1", "this is a");
        properties.put("map-b.k2", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(DeprecatedMapConfigPresent.class));
        DeprecatedMapConfigPresent deprecatedMapConfigPresent = injector.getInstance(DeprecatedMapConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("map-a", "deprecated and should not be used");
        assertNotNull(deprecatedMapConfigPresent);
        assertEquals(deprecatedMapConfigPresent.getMapA(), ImmutableMap.of("k1", "this is a"));
        assertEquals(deprecatedMapConfigPresent.getMapB(), ImmutableMap.of("k2", "this is b"));
    }

    @Test
    public void testConfigurationThroughLegacyMapValueConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k.string-value", "this is a");
        properties.put("map-a.k.string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(LegacyMapValueConfigPresent.class));
        LegacyMapValueConfigPresent legacyMapValueConfigPresent = injector.getInstance(LegacyMapValueConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("map-a.k.string-value", "replaced", "Use 'map-a.k.string-a'");
        assertNotNull(legacyMapValueConfigPresent);
        assertEquals(legacyMapValueConfigPresent.getMapA().get("k").getStringA(), "this is a");
        assertEquals(legacyMapValueConfigPresent.getMapA().get("k").getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithRedundantLegacyMapValueConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k.string-value", "this is a");
        properties.put("map-a.k.string-a", "this is a");
        properties.put("map-a.k.string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(LegacyMapValueConfigPresent.class));

            fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(1);
            monitor.assertMatchingWarningRecorded("map-a.k.string-value", "replaced", "Use 'map-a.k.string-a'");
            assertContainsAllOf(e.getMessage(), "map-a.k.string-value", "conflicts with property", "map-a.k.string-a");
        }
    }

    @Test
    public void testConfigurationWithRedundantDeprecatedMapValueConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k.string-value", "this is a");
        properties.put("map-a.k.deprecated-string-value", "this is a");
        properties.put("map-a.k.string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(LegacyMapValueConfigPresent.class));

            fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(2);
            monitor.assertMatchingWarningRecorded("map-a.k.string-value", "replaced", "Use 'map-a.k.string-a'");
            monitor.assertMatchingWarningRecorded("map-a.k.deprecated-string-value", "replaced", "Use 'map-a.k.string-a'");
            assertContainsAllOf(e.getMessage(), "map-a.k.string-value", "conflicts with property", "map-a.k.deprecated-string-value");
        }
    }

    @Test
    public void testConfigurationThroughDeprecatedMapValueConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k.string-a", "this is a");
        properties.put("map-a.k.string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(DeprecatedMapValueConfigPresent.class));
        DeprecatedMapValueConfigPresent deprecatedMapValueConfigPresent = injector.getInstance(DeprecatedMapValueConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("map-a.k.string-a", "deprecated and should not be used");
        assertNotNull(deprecatedMapValueConfigPresent);
        assertEquals(deprecatedMapValueConfigPresent.getMapA().get("k").getStringA(), "this is a");
        assertEquals(deprecatedMapValueConfigPresent.getMapA().get("k").getStringB(), "this is b");
    }

    @Test
    public void testDefunctPropertyInMapValueConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k.string-value", "this is a");
        properties.put("map-a.k.defunct-value", "this shouldn't work");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(DefunctMapValueConfigPresent.class));

            fail("Expected an exception in object creation due to use of defunct config");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Defunct property", "'map-a.k.defunct-value", "cannot be configured");
        }
    }

    @Test
    public void testSuccessfulMapValueBeanValidation()
    {
        Map<String, String> properties = new HashMap<>();
        properties.put("map-a.k.string-value", "has a value");
        properties.put("map-a.k.int-value", "50");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(MapValueBeanValidationClass.class));
        MapValueBeanValidationClass mapValueBeanValidationClass = injector.getInstance(MapValueBeanValidationClass.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        assertNotNull(mapValueBeanValidationClass);
        assertEquals(mapValueBeanValidationClass.getMapA().get("k").getStringValue(), "has a value");
        assertEquals(mapValueBeanValidationClass.getMapA().get("k").getIntValue(), 50);
    }

    @Test
    public void testFailedMapValueBeanValidation()
    {
        Map<String, String> properties = new HashMap<>();
        // string-value left at invalid default
        properties.put("map-a.k.int-value", "5000");  // out of range
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(MapValueBeanValidationClass.class));
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(2);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Invalid configuration property", "'map-a.k.int-value'", "must be less than or equal to 100", "BeanValidationClass");
            monitor.assertMatchingErrorRecorded("Missing required configuration property", "'map-a.k.string-value'", "BeanValidationClass");
        }
    }

    @Test
    public void testConfigurationWithDifferentRepresentationOfSameMapKeyThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.01337.string-a", "this is a");
        properties.put("map-a.1337.string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(IntegerLegacyMapConfig.class));

            fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            assertContainsAllOf(e.getMessage(), "Configuration property prefixes", "'map-a.1337'", "'map-a.01337'", "convert to the same map key", "setMapA");
        }
    }

    @Test
    public void testConfigurationOfSimpleMapValueWithComplexPropertyThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.1337.string-a", "this is a");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(IntegerStringMapConfig.class));

            fail("Expected an exception in object creation due to use of invalid configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            assertContainsAllOf(e.getMessage(), "Configuration map has non-configuration value class java.lang.String, so key '1337' cannot be followed by '.'",
                    "property 'map-a.1337.string-a'", "setMapA");
        }
    }

    @Test
    public void testConfigurationWithInvalidMapKeyThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k.string-a", "this is a");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(IntegerLegacyMapConfig.class));

            fail("Expected an exception in object creation due to use of invalid configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            assertContainsAllOf(e.getMessage(), "Could not coerce map key 'k' to java.lang.Integer", "property prefix 'map-a.k'", "setMapA");
        }
    }

    @Test
    public void testFailedCoercion()
    {
        Map<String, String> properties = new HashMap<>();
        properties.put("int-value", "abc %s xyz");  // not an int
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, null, null, monitor, binder -> bindConfig(binder).bind(BeanValidationClass.class));
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Could not coerce value 'abc %s xyz' to int (property 'int-value')", "BeanValidationClass");
        }
    }

    private Injector createInjector(Map<String, String> properties, Map<String, String> applicationDefaults, Map<String, String> moduleDefaults, Map<String, ConfigurationDefaultingModule> moduleDefaultSource, TestMonitor monitor, Module module)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(
                properties,
                firstNonNull(applicationDefaults, ImmutableMap.of()),
                firstNonNull(moduleDefaults, ImmutableMap.of()),
                firstNonNull(moduleDefaultSource, ImmutableMap.of()),
                Collections.emptySet(),
                ImmutableList.of(),
                monitor);
        List<Message> messages = new ConfigurationValidator(configurationFactory).validate(module);
        return Guice.createInjector(new ConfigurationModule(configurationFactory), module, new ValidationErrorModule(messages));
    }

    public static class AnnotatedGetter
    {
        private String stringValue;
        private boolean booleanValue;

        @Config("string-value")
        public String getStringValue()
        {
            return stringValue;
        }

        public void setStringValue(String stringValue)
        {
            this.stringValue = stringValue;
        }

        @Config("boolean-value")
        public boolean isBooleanValue()
        {
            return booleanValue;
        }

        public void setBooleanValue(boolean booleanValue)
        {
            this.booleanValue = booleanValue;
        }
    }

    static class AnnotatedSetter
    {
        private String stringValue;
        private boolean booleanValue;

        String getStringValue()
        {
            return stringValue;
        }

        @Config("string-value")
        void setStringValue(String stringValue)
        {
            this.stringValue = stringValue;
        }

        private boolean isBooleanValue()
        {
            return booleanValue;
        }

        @Config("boolean-value")
        private void setBooleanValue(boolean booleanValue)
        {
            this.booleanValue = booleanValue;
        }
    }

    private static class LegacyConfigPresent
    {
        private String stringA = "defaultA";
        private String stringB = "defaultB";

        String getStringA()
        {
            return stringA;
        }

        @Config("string-a")
        @LegacyConfig("string-value")
        private void setStringA(String stringValue)
        {
            this.stringA = stringValue;
        }

        @Deprecated
        @LegacyConfig(value = "deprecated-string-value", replacedBy = "string-a")
        private void setDeprecatedStringA(String stringValue)
        {
            this.stringA = stringValue;
        }

        private String getStringB()
        {
            return stringB;
        }

        @Config("string-b")
        public void setStringB(String stringValue)
        {
            this.stringB = stringValue;
        }
    }

    static class DeprecatedConfigPresent
    {
        private String stringA = "defaultA";
        private String stringB = "defaultB";

        @Deprecated
        String getStringA()
        {
            return stringA;
        }

        @Deprecated
        @Config("string-a")
        void setStringA(String stringValue)
        {
            this.stringA = stringValue;
        }

        private String getStringB()
        {
            return stringB;
        }

        @Config("string-b")
        private void setStringB(String stringValue)
        {
            this.stringB = stringValue;
        }
    }

    @DefunctConfig("defunct-value")
    private static class DefunctConfigPresent
    {
        private String stringValue;
        private boolean booleanValue;

        String getStringValue()
        {
            return stringValue;
        }

        @Config("string-value")
        private void setStringValue(String stringValue)
        {
            this.stringValue = stringValue;
        }
    }

    private static class BeanValidationClass
    {
        @NotNull
        private String stringValue = null;

        private int myIntValue;

        String getStringValue()
        {
            return stringValue;
        }

        @Config("string-value")
        private void setStringValue(String value)
        {
            this.stringValue = value;
        }

        @Min(1)
        @Max(100)
        private int getIntValue()
        {
            return myIntValue;
        }

        @Config("int-value")
        void setIntValue(int value)
        {
            this.myIntValue = value;
        }
    }

    static class MapConfigPresent
    {
        private Map<String, String> mapA = new HashMap<>();
        private Map<String, Config1> mapB = new HashMap<>();

        Map<String, String> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        void setMapA(Map<String, String> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }

        private Map<String, Config1> getMapB()
        {
            return mapB;
        }

        @Config("map-b")
        public void setMapB(Map<String, Config1> mapValue)
        {
            this.mapB = ImmutableMap.copyOf(mapValue);
        }
    }

    static class LegacyMapConfigPresent
    {
        private Map<String, String> mapA = new HashMap<>();
        private Map<String, String> mapB = new HashMap<>();

        Map<String, String> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        @LegacyConfig("map-value")
        void setMapA(Map<String, String> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }

        @Deprecated
        @LegacyConfig(value = "deprecated-map-value", replacedBy = "map-a")
        private void setDeprecatedMapA(Map<String, String> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }

        private Map<String, String> getMapB()
        {
            return mapB;
        }

        @Config("map-b")
        public void setMapB(Map<String, String> mapValue)
        {
            this.mapB = ImmutableMap.copyOf(mapValue);
        }
    }

    private static class DeprecatedMapConfigPresent
    {
        private Map<String, String> mapA = new HashMap<>();
        private Map<String, String> mapB = new HashMap<>();

        @Deprecated
        Map<String, String> getMapA()
        {
            return mapA;
        }

        @Deprecated
        @Config("map-a")
        void setMapA(Map<String, String> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }

        private Map<String, String> getMapB()
        {
            return mapB;
        }

        @Config("map-b")
        private void setMapB(Map<String, String> mapValue)
        {
            this.mapB = ImmutableMap.copyOf(mapValue);
        }
    }

    static class LegacyMapValueConfigPresent
    {
        private Map<String, LegacyConfigPresent> mapA = new HashMap<>();

        private Map<String, LegacyConfigPresent> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        void setMapA(Map<String, LegacyConfigPresent> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }
    }

    private static class DeprecatedMapValueConfigPresent
    {
        private Map<String, DeprecatedConfigPresent> mapA = new HashMap<>();

        Map<String, DeprecatedConfigPresent> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        private void setMapA(Map<String, DeprecatedConfigPresent> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }
    }

    static class DefunctMapValueConfigPresent
    {
        private Map<String, DefunctConfigPresent> mapA = new HashMap<>();

        private Map<String, DefunctConfigPresent> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        void setMapA(Map<String, DefunctConfigPresent> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }
    }

    private static class MapValueBeanValidationClass
    {
        private Map<String, BeanValidationClass> mapA = new HashMap<>();

        Map<String, BeanValidationClass> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        private void setMapA(Map<String, BeanValidationClass> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }
    }

    private static class IntegerLegacyMapConfig
    {
        private Map<Integer, LegacyConfigPresent> mapA = new HashMap<>();

        private Map<Integer, LegacyConfigPresent> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        private void setMapA(Map<Integer, LegacyConfigPresent> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }
    }

    private static class IntegerStringMapConfig
    {
        private Map<Integer, String> mapA = new HashMap<>();

        Map<Integer, String> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        void setMapA(Map<Integer, String> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }
    }
}
