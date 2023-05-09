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
package com.proofpoint.bootstrap;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.inject.Binder;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import com.proofpoint.bootstrap.Bootstrap.UnitTestBootstrap;
import com.proofpoint.configuration.AbstractConfigurationAwareModule;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigurationAwareProvider;
import com.proofpoint.configuration.ConfigurationDefaultingModule;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.log.Level;
import com.proofpoint.log.Logging;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.node.NodeModule;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.inject.Scopes.SINGLETON;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.configuration.ConfigBinder.bindConfig;
import static com.proofpoint.testing.Assertions.assertContains;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestBootstrap
{
    @BeforeMethod
    public void setup()
    {
        System.clearProperty("config");
        System.clearProperty("secrets-config");
        System.clearProperty("property");
    }

    @Test
    public void testRequiresExplicitBindings()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules()
                .initialize();
        try {
            injector.getInstance(Instance.class);
            fail("should require explicit bindings");
        }
        catch (ConfigurationException e) {
            assertContains(e.getErrorMessages().iterator().next().getMessage(), "Explicit bindings are required");
        }
    }

    @Test
    public void testDisableRequiresExplicitBindings()
            throws Exception
    {
        bootstrapTest()
                .withModules()
                .requireExplicitBindings(false)
                .initialize()
                .getInstance(Instance.class);
    }

    @Test
    public void testDoesNotAllowCircularDependencies()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules((Module) binder -> {
                    binder.bind(InstanceA.class);
                    binder.bind(InstanceB.class);
                })
                .initialize();

        try {
            injector.getInstance(InstanceA.class);
            fail("should not allow circular dependencies");
        }
        catch (ProvisionException e) {
            assertContains(e.getErrorMessages().iterator().next().getMessage(), "circular dependencies are disabled");
        }
    }

    @Test
    public void testConfigFile()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("simple-config.properties").getFile());
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules((Module) binder -> bindConfig(binder).bind(SimpleConfig.class))
                .quiet()
                .initialize();

        SimpleConfig simpleConfig = injector.getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "value");
    }

    @Test
    public void testSecretConfig()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("simple-config.properties").getFile());
        System.setProperty("secrets-config", Resources.getResource("secret-config.properties").getFile());

        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules((Module) binder -> bindConfig(binder).bind(SimpleConfig.class))
                .quiet()
                .initialize();

        SimpleConfig simpleConfig = injector.getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "value");
        assertEquals(simpleConfig.getOtherProperty(), "secret-value");
    }

    @Test
    public void testConfigSystemProperties()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("empty-config.properties").getFile());
        System.setProperty("property", "value");
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules((Module) binder -> bindConfig(binder).bind(SimpleConfig.class))
                .quiet()
                .initialize();

        SimpleConfig simpleConfig = injector.getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "value");
    }

    @Test
    public void testSystemPropertiesOverrideConfigFile()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("simple-config.properties").getFile());
        System.setProperty("property", "system property value");
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules((Module) binder -> bindConfig(binder).bind(SimpleConfig.class))
                .quiet()
                .initialize();

        SimpleConfig simpleConfig = injector.getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "system property value");
    }

    @Test
    public void testRequiredConfig()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules((Module) binder -> bindConfig(binder).bind(SimpleConfig.class))
                .setRequiredConfigurationProperty("property", "required value")
                .initialize();

        SimpleConfig simpleConfig = injector.getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "required value");
    }

    @Test
    public void testRequiredConfigWithMap()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules((Module) binder -> bindConfig(binder).bind(SimpleConfig.class))
                .setRequiredConfigurationProperties(ImmutableMap.of("property", "required value"))
                .initialize();

        SimpleConfig simpleConfig = injector.getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "required value");
    }

    @Test
    public void testMissingRequiredConfig()
            throws Exception
    {
        UnitTestBootstrap bootstrap = bootstrapTest()
                .withModules((Module) binder -> bindConfig(binder).bind(SimpleConfig.class))
                .setRequiredConfigurationProperty("unknown", "required value");

        try {
            bootstrap.initialize();
            fail("should not allow unknown required configuration properties");
        }
        catch (CreationException e) {
            assertContains(e.getErrorMessages().iterator().next().getMessage(), "Configuration property 'unknown' was not used");
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testRequiredConfigIgnoresSystemPropertiesAndConfigFile()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("simple-config.properties").getFile());
        System.setProperty("property", "system property value");
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules((Module) binder -> bindConfig(binder).bind(SimpleConfig.class))
                .setRequiredConfigurationProperty("other-property", "value")
                .initialize();

        SimpleConfig simpleConfig = injector.getInstance(SimpleConfig.class);
        assertNull(simpleConfig.getProperty());
    }

    @Test
    public void testBootstrapTestIgnoresSystemPropertiesAndConfigFile()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("simple-config.properties").getFile());
        System.setProperty("property", "system property value");
        Injector injector = bootstrapTest()
                .withModules((Module) binder -> bindConfig(binder).bind(SimpleConfig.class))
                .initialize();

        SimpleConfig simpleConfig = injector.getInstance(SimpleConfig.class);
        assertNull(simpleConfig.getProperty());
    }

    @Test
    public void testApplicationDefaults()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("simple-config.properties").getFile());
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules((Module) binder -> bindConfig(binder).bind(SimpleConfig.class))
                .withApplicationDefaults(ImmutableMap.of(
                        "property", "default value",
                        "other-property", "other default value"
                ))
                .initialize();

        SimpleConfig simpleConfig = injector.getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "value");
        assertEquals(simpleConfig.getOtherProperty(), "other default value");
    }

    @Test
    public void testModuleDefaults()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("simple-config.properties").getFile());
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(binder -> bindConfig(binder).bind(SimpleConfig.class), new ConfigurationDefaultingModule()
                {
                    @Override
                    public Map<String, String> getConfigurationDefaults()
                    {
                        return ImmutableMap.of(
                                "property", "default value",
                                "other-property", "other default value"
                        );
                    }

                    @Override
                    public void configure(Binder binder)
                    {
                    }
                })
                .initialize();

        SimpleConfig simpleConfig = injector.getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "value");
        assertEquals(simpleConfig.getOtherProperty(), "other default value");
    }

    @Test
    public void testConflictingModuleDefaults()
            throws Exception
    {
        UnitTestBootstrap bootstrap = bootstrapTest()
                .withModules(binder -> bindConfig(binder).bind(SimpleConfig.class), new ConfigurationDefaultingModule()
                {
                    @Override
                    public Map<String, String> getConfigurationDefaults()
                    {
                        return ImmutableMap.of("property", "default value");
                    }

                    @Override
                    public void configure(Binder binder)
                    {
                    }

                    @Override
                    public String toString()
                    {
                        return "first test module";
                    }
                }, new ConfigurationDefaultingModule()
                {
                    @Override
                    public Map<String, String> getConfigurationDefaults()
                    {
                        return ImmutableMap.of("property", "default value");
                    }

                    @Override
                    public void configure(Binder binder)
                    {
                    }

                    @Override
                    public String toString()
                    {
                        return "second test module";
                    }
                })
                .setRequiredConfigurationProperty("other-property", "other value");

        try {
            bootstrap.initialize();
            fail("should not allow duplicate module defaults");
        }
        catch (CreationException e) {
            assertContains(e.getErrorMessages().iterator().next().getMessage(), "Configuration default for \"property\" set by both first test module and second test module");
        }
    }

    @Test
    public void testApplicationDefaultsOverrideBoundDefaults()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(binder -> bindConfig(binder).bind(SimpleConfig.class), new ConfigurationDefaultingModule()
                {
                    @Override
                    public Map<String, String> getConfigurationDefaults()
                    {
                        return ImmutableMap.of("property", "bound default value");
                    }

                    @Override
                    public void configure(Binder binder)
                    {
                    }
                })
                .withApplicationDefaults(ImmutableMap.of(
                        "property", "application default value"
                ))
                .initialize();

        SimpleConfig simpleConfig = injector.getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "application default value");
    }

    @Test
    public void testConfigAwareModule()
            throws Exception
    {
        final AtomicReference<SimpleConfig> simpleConfig = new AtomicReference<>();
        bootstrapTest()
                .withModules(new AbstractConfigurationAwareModule()
                {
                    @Override
                    public void setup(Binder binder)
                    {
                        simpleConfig.set(buildConfigObject(SimpleConfig.class));
                    }
                })
                .setRequiredConfigurationProperty("property", "required value")
                .initialize();

        assertEquals(simpleConfig.get().getProperty(), "required value");
    }

    @Test
    public void testConfigAwareModuleWithPrefix()
            throws Exception
    {
        final AtomicReference<SimpleConfig> simpleConfig = new AtomicReference<>();
        bootstrapTest()
                .withModules(new AbstractConfigurationAwareModule()
                {
                    @Override
                    public void setup(Binder binder)
                    {
                        simpleConfig.set(buildConfigObject(SimpleConfig.class, "some-prefix"));
                    }
                })
                .setRequiredConfigurationProperty("some-prefix.property", "required value")
                .initialize();

        assertEquals(simpleConfig.get().getProperty(), "required value");
    }

    @Test
    public void testConfigObjectsNotShared()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules((Module) binder -> bindConfig(binder).bind(SimpleConfig.class))
                .initialize();

        injector.getInstance(SimpleConfig.class).setProperty("changed");
        SimpleConfig simpleConfig = injector.getInstance(SimpleConfig.class);
        assertNull(simpleConfig.getProperty());
    }

    @Test
    public void testConfigWarnings()
            throws Exception
    {
        Logging.initialize();
        AtomicBoolean sawWarning = new AtomicBoolean();

        try {
            Logging.addLogTester("Bootstrap", (level, message, thrown) -> {
                if (level == Level.WARN && "Warning: Configuration property 'property' is deprecated and should not be used".equals(message)) {
                    assertFalse(sawWarning.get());
                    sawWarning.set(true);
                }
            });

            Injector injector = bootstrapTest()
                    .withModules((Module) binder -> {
                        bindConfig(binder).bind(ContainsDeprecatedConfig.class);
                        binder.bind(UsesDeprecatedConfig2.class);
                    })
                    .setRequiredConfigurationProperty("property", "value")
                    .initialize();
        }
        finally {
            Logging.resetLogTesters();
        }
        assertTrue(sawWarning.get());
    }

    @Test
    public void testConfigWarningsConfigAwareModule()
            throws Exception
    {
        Logging.initialize();
        AtomicBoolean sawWarning = new AtomicBoolean();

        try {
            Logging.addLogTester("Bootstrap", (level, message, thrown) -> {
                if (level == Level.WARN && "Warning: Configuration property 'property' is deprecated and should not be used".equals(message)) {
                    assertFalse(sawWarning.get());
                    sawWarning.set(true);
                }
            });

            Injector injector = bootstrapTest()
                    .withModules(new AbstractConfigurationAwareModule()
                    {
                        @Override
                        protected void setup(Binder binder)
                        {
                            buildConfigObject(ContainsDeprecatedConfig.class);
                        }
                    }, new AbstractConfigurationAwareModule()
                    {
                        @Override
                        protected void setup(Binder binder)
                        {
                            buildConfigObject(ContainsDeprecatedConfig.class);
                        }
                    })
                    .setRequiredConfigurationProperty("property", "value")
                    .initialize();
        }
        finally {
            Logging.resetLogTesters();
        }
        assertTrue(sawWarning.get());
    }

    @Test
    public void testConfigWarningsConfigAwareProvider()
            throws Exception
    {
        Logging.initialize();
        AtomicBoolean sawWarning = new AtomicBoolean();

        try {
            Logging.addLogTester("Bootstrap", (level, message, thrown) -> {
                if (level == Level.WARN && "Warning: Configuration property 'property' is deprecated and should not be used".equals(message)) {
                    assertFalse(sawWarning.get());
                    sawWarning.set(true);
                }
            });

            Injector injector = bootstrapTest()
                    .withModules(binder -> {
                        binder.bind(Integer.class).toProvider(new ConfigurationAwareProvider<Integer>()
                        {
                            private ConfigurationFactory configurationFactory;

                            @Override
                            public void setConfigurationFactory(ConfigurationFactory configurationFactory)
                            {
                                this.configurationFactory = configurationFactory;
                            }

                            @Override
                            public Integer get()
                            {
                                configurationFactory.build(ContainsDeprecatedConfig.class);
                                return 3;
                            }
                        });
                        binder.bind(Long.class).toProvider(new ConfigurationAwareProvider<Long>()
                        {
                            private ConfigurationFactory configurationFactory;

                            @Override
                            public void setConfigurationFactory(ConfigurationFactory configurationFactory)
                            {
                                this.configurationFactory = configurationFactory;
                            }

                            @Override
                            public Long get()
                            {
                                configurationFactory.build(ContainsDeprecatedConfig.class);
                                return 3L;
                            }
                        });
                    })
                    .setRequiredConfigurationProperty("property", "value")
                    .initialize();
        }
        finally {
            Logging.resetLogTesters();
        }
        assertTrue(sawWarning.get());
    }

    @Test
    public void testPostConstructCalled()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules((Module) binder -> binder.bind(LifecycleInstance.class).in(SINGLETON))
                .initialize();

        LifecycleInstance lifecycleInstance = injector.getInstance(LifecycleInstance.class);
        assertTrue(lifecycleInstance.isInitialized());
    }

    @Test
    public void testPreDestroyCalled()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules((Module) binder -> binder.bind(PredestroyInstance.class).in(SINGLETON))
                .initialize();

        PredestroyInstance predestroyInstance = injector.getInstance(PredestroyInstance.class);
        assertFalse(predestroyInstance.isStopped());
        injector.getInstance(LifeCycleManager.class).stop();
        assertTrue(predestroyInstance.isStopped());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testApplicationName()
            throws Exception
    {
        NodeInfo nodeInfo = bootstrapApplication("test-application-name")
                .doNotInitializeLogging()
                .withModules(new NodeModule())
                .quiet()
                .setRequiredConfigurationProperties(ImmutableMap.of(
                        "node.environment", "test"
                ))
                .initialize()
                .getInstance(NodeInfo.class);
        assertEquals(nodeInfo.getApplication(), "test-application-name");
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testDynamicApplicationName()
            throws Exception
    {
        NodeInfo nodeInfo = bootstrapApplication(
                SimpleConfig.class,
                SimpleConfig::getProperty)
                .doNotInitializeLogging()
                .withModules(new NodeModule(),
                        binder -> bindConfig(binder).bind(SimpleConfig.class))
                .quiet()
                .setRequiredConfigurationProperties(ImmutableMap.of(
                        "node.environment", "test",
                        "property", "test-dynamic-application"
                ))
                .initialize()
                .getInstance(NodeInfo.class);
        assertEquals(nodeInfo.getApplication(), "test-dynamic-application");
    }

    public static class Instance
    {
    }

    public static class InstanceA
    {
        @Inject
        @SuppressWarnings("unused")
        public InstanceA(InstanceB b)
        {
        }
    }

    public static class InstanceB
    {
        @Inject
        @SuppressWarnings("unused")
        public InstanceB(InstanceA a)
        {
        }
    }

    private static class SimpleConfig
    {
        private String property = null;
        private String otherProperty = null;

        public String getProperty()
        {
            return property;
        }

        @Config("property")
        public SimpleConfig setProperty(String property)
        {
            this.property = property;
            return this;
        }

        public String getOtherProperty()
        {
            return otherProperty;
        }

        @Config("other-property")
        public SimpleConfig setOtherProperty(String otherProperty)
        {
            this.otherProperty = otherProperty;
            return this;
        }
    }

    private static class ContainsDeprecatedConfig
    {
        private String property = null;

        @Deprecated
        public String getProperty()
        {
            return property;
        }

        @Config("property")
        @Deprecated
        public ContainsDeprecatedConfig setProperty(String property)
        {
            this.property = property;
            return this;
        }
    }

    private static class UsesDeprecatedConfig1
    {
        @Inject
        UsesDeprecatedConfig1(ContainsDeprecatedConfig config)
        {
        }
    }

    private static class UsesDeprecatedConfig2
    {
        @Inject
        UsesDeprecatedConfig2(ContainsDeprecatedConfig config)
        {
        }
    }

    private static class LifecycleInstance
    {
        private final AtomicBoolean initialized = new AtomicBoolean();

        @PostConstruct
        public void start()
        {
            initialized.set(true);
        }

        public boolean isInitialized()
        {
            return initialized.get();
        }
    }

    private static class PredestroyInstance
    {
        private final AtomicBoolean stopped = new AtomicBoolean();

        @PreDestroy
        public void stop()
        {
            stopped.set(true);
        }

        public boolean isStopped()
        {
            return stopped.get();
        }
    }
}
