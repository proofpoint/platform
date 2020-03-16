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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.spi.Message;
import com.proofpoint.configuration.ConfigurationAwareModule;
import com.proofpoint.configuration.ConfigurationDefaultingModule;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationFactoryBuilder;
import com.proofpoint.configuration.ConfigurationInspector;
import com.proofpoint.configuration.ConfigurationInspector.ConfigAttribute;
import com.proofpoint.configuration.ConfigurationInspector.ConfigRecord;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.configuration.ConfigurationValidator;
import com.proofpoint.configuration.ValidationErrorModule;
import com.proofpoint.configuration.WarningsMonitor;
import com.proofpoint.log.Logger;
import com.proofpoint.log.Logging;
import com.proofpoint.log.LoggingConfiguration;
import com.proofpoint.node.ApplicationNameModule;
import com.proofpoint.node.NodeInfo;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * Entry point for an application built using the platform codebase.
 * <p>
 * This class will:
 * <ul>
 * <li>load, validate and bind configurations</li>
 * <li>initialize logging</li>
 * <li>set up lifecycle management</li>
 * <li>create an Guice injector</li>
 * </ul>
 * <p>
 * An application is started with an invocation such as:
 * <pre>
 *   try {
 *       Injector injector = bootstrapApplication("nameOfApplication")
 *               .withModules(
 *                       new NodeModule(),
 *                       new DiscoveryModule(),
 *                       new HttpServerModule(),
 *                       new JsonModule(),
 *                       explicitJaxrsModule(),
 *                       new MBeanModule(),
 *                       new JmxModule(),
 *                       new JmxHttpModule(),
 *                       new LogJmxModule(),
 *                       new ReportingModule(),
 *                       new ReportingClientModule(),
 *                       new MainModule()
 *               )
 *               .withApplicationDefaults(ImmutableMap.&lt;String, String&gt;builder()
 *                       .put("http-server.http.enabled", "false")
 *                       .put("http-server.https.enabled", "true")
 *                       .put("http-server.https.port", "8443")
 *                       .build()
 *               )
 *               .initialize();
 *
 *       injector.getInstance(Announcer.class).start();
 *   }
 *   catch (Throwable e) {
 *       log.error(e);
 *       System.exit(1);
 *   }
 * </pre>
 *
 * The configuration is read from a file specified by the "config" system property.
 *
 * <p>
 * A unit test would start an application instance with an invocation such as:
 * <pre>
 *   &#64;BeforeMethod
 *   public void setup()
 *           throws Exception
 *   {
 *       Injector injector = bootstrapTest()
 *               .withModules(
 *                       new TestingNodeModule(),
 *                       new TestingHttpServerModule(),
 *                       new JsonModule(),
 *                       explicitJaxrsModule(),
 *                       new ReportingModule(),
 *                       new TestingMBeanModule(),
 *                       new MainModule()
 *               )
 *               .setRequiredConfigurationProperties(properties)
 *               .initialize();
 *
 *       lifeCycleManager = injector.getInstance(LifeCycleManager.class);
 *       server = injector.getInstance(TestingHttpServer.class);
 *   }
 *
 *   &#64;AfterMethod(alwaysRun = true)
 *   public void teardown()
 *           throws Exception
 *   {
 *       if (lifeCycleManager != null) {
 *           lifeCycleManager.stop();
 *       }
 *   }
 * </pre>
 */
public class Bootstrap
{
    private final Logger log = Logger.get("Bootstrap");
    private final Logging logging;
    private final List<Module> modules;

    private Map<String, String> requiredConfigurationProperties = null;
    private Map<String, String> applicationDefaults = null;
    private boolean quiet = false;
    private boolean requireExplicitBindings = true;

    private boolean initialized = false;

    /**
     * Start building an object for starting an application.
     *
     * @param applicationName the lowercase hyphen-separated name of the application
     * @return an intermediate object for initializing the application
     */
    public static BootstrapBeforeModules bootstrapApplication(String applicationName)
    {
        return new StaticBootstrapBeforeModules(applicationName);
    }

    /**
     * Start building an object for starting an application whose name is dependent upon configuration.
     *
     * @param configClass the configuration class needed to determine the application name
     * @param applicationNameFunction the {@link Function} to map from the configuration class
     * to the lowercase hyphen-separated name of the application
     * @return an intermediate object for initializing the application
     */
    public static <T> BootstrapBeforeModules bootstrapApplication(Class<T> configClass, Function<T, String> applicationNameFunction)
    {
        return new DynamicBootstrapBeforeModules<>(configClass, applicationNameFunction);
    }

    /**
     * Start building an object for starting an application for a unit test.
     *
     * Suppresses logging initializing, reading of a configuration file, and verbose logging.
     *
     * @return an intermediate object for initializing the test application
     */
    public static UnitTestBootstrapBeforeModules bootstrapTest()
    {
        return new UnitTestBootstrapBeforeModules();
    }

    private Bootstrap(Module applicationNameModule, Iterable<? extends Module> modules, boolean initializeLogging)
    {
        if (initializeLogging) {
            logging = Logging.initialize();
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> log.error(e, "Uncaught exception in thread %s", t.getName()));
        }
        else {
            logging = null;
        }

        this.modules = ImmutableList.<Module>builder()
                .add(requireNonNull(applicationNameModule, "applicationNameModule is null"))
                .add(new LifeCycleModule())
                .addAll(modules)
                .build();
    }

    /**
     * Set a configuration property for use by the application's configuration. The property
     * must be consumed by configuration. Suppresses reading a configuration file.
     * Intended for use in unit tests.
     *
     * @deprecated Use {@link #bootstrapTest()} to bootstrap a unit test.
     * @param key the name of the configuration property
     * @param value the value of the configuration property
     * @return the object, for chaining method calls.
     */
    @Deprecated
    public Bootstrap setRequiredConfigurationProperty(String key, String value)
    {
        if (this.requiredConfigurationProperties == null) {
            this.requiredConfigurationProperties = new TreeMap<>();
        }
        this.requiredConfigurationProperties.put(key, value);
        return this;
    }

    /**
     * Set configuration properties for use by the application's configuration.
     * All specified properties must be consumed by configuration.  Suppresses
     * reading a configuration file. Intended for use in unit tests.
     *
     * @deprecated Use {@link #bootstrapTest()} to bootstrap a unit test.
     * @param requiredConfigurationProperties the configuration properties
     * @return the object, for chaining method calls.
     */
    @Deprecated
    public Bootstrap setRequiredConfigurationProperties(Map<String, String> requiredConfigurationProperties)
    {
        if (this.requiredConfigurationProperties == null) {
            this.requiredConfigurationProperties = new TreeMap<>();
        }
        this.requiredConfigurationProperties.putAll(requiredConfigurationProperties);
        return this;
    }

    /**
     * Override the configuration parameter defaults with application-specific
     * values. All specified properties must be consumed by configuration,
     * though the values may be overridden by the application's configuration.
     *
     * An application would normally use this to, as a minimum, enable HTTPS
     * by default and specify the application's ports.
     *
     * @param applicationDefaults properties specifying the application's defaults
     * @return the object, for chaining method calls.
     */
    public Bootstrap withApplicationDefaults(Map<String, String> applicationDefaults)
    {
        checkState(this.applicationDefaults == null, "applicationDefaults already specified");
        this.applicationDefaults = requireNonNull(applicationDefaults, "applicationDefaults is null");
        return this;
    }

    /**
     * Suppress some logging, such as that of the configuration. Intended for
     * use in unit tests.
     *
     * @return the object, for chaining method calls.
     */
    public Bootstrap quiet()
    {
        this.quiet = true;
        return this;
    }

    /**
     * Set the policy on Guice implicit bindings.
     *
     * @param requireExplicitBindings true if bindings must be listed in a Module in
     * order to be injected. Default true.
     * @return the object, for chaining method calls.
     */
    @SuppressWarnings("unused")
    public Bootstrap requireExplicitBindings(boolean requireExplicitBindings)
    {
        this.requireExplicitBindings = requireExplicitBindings;
        return this;
    }

    /**
     * Initialize the application and start its lifecycle.
     *
     * @return the application's Guice injector
     * @throws Exception
     */
    public Injector initialize()
            throws Exception
    {
        checkState(!initialized, "Already initialized");
        initialized = true;

        Map<String, String> moduleDefaults = new HashMap<>();
        Map<String, ConfigurationDefaultingModule> moduleDefaultSource = new HashMap<>();
        List<Message> moduleDefaultErrors = new ArrayList<>();
        for (Module module : modules) {
            if (module instanceof ConfigurationDefaultingModule) {
                ConfigurationDefaultingModule configurationDefaultingModule = (ConfigurationDefaultingModule) module;
                Map<String, String> defaults = configurationDefaultingModule.getConfigurationDefaults();
                for (Entry<String, String> entry : defaults.entrySet()) {
                    ConfigurationDefaultingModule oldModule = moduleDefaultSource.put(entry.getKey(), configurationDefaultingModule);
                    if (oldModule != null) {
                        moduleDefaultErrors.add(
                                new Message(module, "Configuration default for \"" + entry.getKey() + "\" set by both " + oldModule.toString() + " and " + module.toString()));
                    }
                    moduleDefaults.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // initialize configuration
        ConfigurationFactoryBuilder builder = new ConfigurationFactoryBuilder();
        if (!moduleDefaults.isEmpty()) {
            builder = builder.withModuleDefaults(moduleDefaults, moduleDefaultSource);
        }
        if (applicationDefaults != null) {
            builder = builder.withApplicationDefaults(applicationDefaults);
        }
        if (requiredConfigurationProperties == null) {
            log.info("Loading configuration");
            builder = builder.withFile(firstNonNull(System.getProperty("config"), "etc/config.properties"));

            String secretsConfigPath = System.getProperty("secrets-config");
            if (secretsConfigPath == null && new File("etc/secrets.properties").exists()) {
                secretsConfigPath = "etc/secrets.properties";
            }
            if (secretsConfigPath != null) {
                builder = builder.withFile(secretsConfigPath);
            }

            builder = builder.withSystemProperties();
        }
        else {
            builder = builder.withRequiredProperties(requiredConfigurationProperties);
        }
        WarningLoggingMonitor warningsMonitor = new WarningLoggingMonitor();
        builder = builder.withWarningsMonitor(warningsMonitor);
        ConfigurationFactory configurationFactory = builder.build();

        if (logging != null) {
            // initialize logging
            log.info("Initializing logging");
            LoggingConfiguration configuration = configurationFactory.build(LoggingConfiguration.class);
            logging.configure(configuration);
        }

        warningsMonitor.loggingInitialized();

        // initialize configuration factory
        modules.stream()
                .filter(ConfigurationAwareModule.class::isInstance)
                .map(ConfigurationAwareModule.class::cast)
                .forEach(module -> module.setConfigurationFactory(configurationFactory));

        // Validate configuration
        ConfigurationValidator configurationValidator = new ConfigurationValidator(configurationFactory);
        List<Message> messages = configurationValidator.validate(modules);

        // Log effective configuration
        if (!quiet) {
            logConfiguration(configurationFactory);
        }

        // system modules
        Builder<Module> moduleList = ImmutableList.builder();
        moduleList.add(new ConfigurationModule(configurationFactory));
        if (!moduleDefaultErrors.isEmpty()) {
            moduleList.add(new ValidationErrorModule(moduleDefaultErrors));
        }
        if (!messages.isEmpty()) {
            moduleList.add(new ValidationErrorModule(messages));
        }
        moduleList.add(binder -> binder.bind(WarningsMonitor.class).toInstance(warningsMonitor));
        moduleList.add(binder -> binder.bindConstant().annotatedWith(QuietMode.class).to(quiet));

        // disable broken Guice "features"
        moduleList.add(Binder::disableCircularProxies);
        if (requireExplicitBindings) {
            moduleList.add(Binder::requireExplicitBindings);
        }

        moduleList.addAll(modules);

        // create the injector
        final Injector injector = Guice.createInjector(Stage.PRODUCTION, moduleList.build());

        if (!quiet) {
            try {
                NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
                log.info("Node ID %s", nodeInfo.getNodeId());
            }
            catch (ConfigurationException ignored) {
            }
        }

        // Create the life-cycle manager
        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        // Start services
        if (lifeCycleManager.size() > 0) {
            lifeCycleManager.start();
        }

        return injector;
    }

    private void logConfiguration(ConfigurationFactory configurationFactory)
    {
        ColumnPrinter columnPrinter = makePrinterForConfiguration(configurationFactory);

        try (PrintWriter out = new PrintWriter(new LoggingWriter(log))) {
            columnPrinter.print(out);
        }
    }

    private static ColumnPrinter makePrinterForConfiguration(ConfigurationFactory configurationFactory)
    {
        ConfigurationInspector configurationInspector = new ConfigurationInspector();

        ColumnPrinter columnPrinter = new ColumnPrinter(
                "PROPERTY", "DEFAULT", "RUNTIME", "DESCRIPTION");

        Set<ConfigAttribute> attributes = new TreeSet<>((o1, o2) -> o1.getPropertyName().compareTo(o2.getPropertyName()));

        for (ConfigRecord<?> record : configurationInspector.inspect(configurationFactory)) {
            attributes.addAll(record.getAttributes());
        }

        for (ConfigAttribute attribute : attributes) {
            columnPrinter.addValues(
                    attribute.getPropertyName(),
                    attribute.getDefaultValue(),
                    attribute.getCurrentValue(),
                    attribute.getDescription());
        }
        return columnPrinter;
    }

    public abstract static class BootstrapBeforeModules
    {
        private BootstrapBeforeModules()
        {
        }

        boolean initializeLogging = true;

        /**
         * Suppresses initialization of the logging subsystem. Intended for
         * use by unit tests.
         *
         * @return the object, for chaining method calls.
         */
        public BootstrapBeforeModules doNotInitializeLogging()
        {
            this.initializeLogging = false;
            return this;
        }

        /**
         * Specify the application's Guice Modules
         *
         * @param modules the application's Modules
         * @return the object, for chaining method calls.
         */
        public Bootstrap withModules(Module... modules)
        {
            return withModules(ImmutableList.copyOf(modules));
        }

        /**
         * Specify the application's Guice Modules
         *
         * @param modules the application's Modules
         * @return the object, for chaining method calls.
         */
        public abstract Bootstrap withModules(Iterable<? extends Module> modules);
    }

    private static class StaticBootstrapBeforeModules extends BootstrapBeforeModules
    {
        private final String applicationName;

        private StaticBootstrapBeforeModules(String applicationName)
        {
            this.applicationName = requireNonNull(applicationName, "applicationName is null");
        }

        @Override
        public Bootstrap withModules(Iterable<? extends Module> modules)
        {
            return new Bootstrap(new ApplicationNameModule(applicationName), modules, initializeLogging);
        }
    }

    private static class DynamicBootstrapBeforeModules<T> extends BootstrapBeforeModules
    {
        private final Class<T> configClass;
        private final Function<T, String> applicationNameFunction;

        private DynamicBootstrapBeforeModules(Class<T> configClass, Function<T, String> applicationNameFunction)
        {
            this.configClass = requireNonNull(configClass, "configClass is null");
            this.applicationNameFunction = requireNonNull(applicationNameFunction, "applicationNameFunction is null");
        }

        @Override
        public Bootstrap withModules(Iterable<? extends Module> modules)
        {
            return new Bootstrap(new DynamicApplicationNameModule<>(configClass, applicationNameFunction), modules, initializeLogging);
        }
    }

    public static class UnitTestBootstrapBeforeModules
    {
        private UnitTestBootstrapBeforeModules()
        {
        }

        /**
         * Specify the application's Guice Modules
         *
         * @param modules the application's Modules
         * @return the object, for chaining method calls.
         */
        public UnitTestBootstrap withModules(Iterable<? extends Module> modules)
        {
            return new UnitTestBootstrap(modules);
        }

        /**
         * Specify the application's Guice Modules
         *
         * @param modules the application's Modules
         * @return the object, for chaining method calls.
         */
        public UnitTestBootstrap withModules(Module... modules)
        {
            return withModules(ImmutableList.copyOf(modules));
        }
    }

    public static class UnitTestBootstrap
    {
        private Bootstrap bootstrap;

        @SuppressWarnings("deprecation")
        private UnitTestBootstrap(Iterable<? extends Module> modules)
        {
            bootstrap = new Bootstrap(new ApplicationNameModule("test-application"), modules, false)
                    .quiet()
                    .setRequiredConfigurationProperties(ImmutableMap.of()); // Suppress reading configuration file
        }

        /**
         * Set a configuration property for use by the application's configuration. The property
         * must be consumed by configuration.
         *
         * @param key the name of the configuration property
         * @param value the value of the configuration property
         * @return the object, for chaining method calls.
         */
        @SuppressWarnings("deprecation")
        public UnitTestBootstrap setRequiredConfigurationProperty(String key, String value)
        {
            bootstrap = bootstrap.setRequiredConfigurationProperty(key, value);
            return this;
        }

        /**
         * Set configuration properties for use by the application's configuration.
         * All specified properties must be consumed by configuration.
         *
         * @param requiredConfigurationProperties the configuration properties
         * @return the object, for chaining method calls.
         */
        @SuppressWarnings("deprecation")
        public UnitTestBootstrap setRequiredConfigurationProperties(Map<String, String> requiredConfigurationProperties)
        {
            bootstrap = bootstrap.setRequiredConfigurationProperties(requiredConfigurationProperties);
            return this;
        }

        /**
         * Override the configuration parameter defaults with application-specific
         * values. All specified properties must be consumed by configuration,
         * though the values may be overridden by the application's configuration.
         * <p>
         * An application would normally use this to, as a minimum, enable HTTPS
         * by default and specify the application's ports.
         *
         * @param applicationDefaults properties specifying the application's defaults
         * @return the object, for chaining method calls.
         */
        public UnitTestBootstrap withApplicationDefaults(Map<String, String> applicationDefaults)
        {
            bootstrap = bootstrap.withApplicationDefaults(applicationDefaults);
            return this;
        }

        /**
         * Set whether properties in configuration files must be consumed by
         * configuration.
         *
         * @param requireExplicitBindings true if properties in configuration
         * files must be consumed. Default true.
         * @return the object, for chaining method calls.
         */
        public UnitTestBootstrap requireExplicitBindings(boolean requireExplicitBindings)
        {
            bootstrap = bootstrap.requireExplicitBindings(requireExplicitBindings);
            return this;
        }

        /**
         * Initialize the application and start its lifecycle.
         *
         * @return the application's Guice injector
         */
        public Injector initialize()
                throws Exception
        {
            return bootstrap.initialize();
        }
    }

    private class WarningLoggingMonitor
            implements WarningsMonitor
    {
        private final AtomicBoolean loggingInitialized = new AtomicBoolean();
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void onWarning(String message)
        {
            if (loggingInitialized.get()) {
                log.warn(message);
            }
            else {
                warnings.add(message);
            }

        }

        public void loggingInitialized()
        {
            loggingInitialized.set(true);
            for (String warning : warnings) {
                onWarning(warning);
            }
            warnings.clear();
        }
    }
}
