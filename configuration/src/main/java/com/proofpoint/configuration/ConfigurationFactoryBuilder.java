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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.spi.Message;
import com.proofpoint.configuration.Problems.Monitor;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ConfigurationFactoryBuilder
{
    private PropertiesBuilder propertiesBuilder = new PropertiesBuilder();
    private final Set<String> expectToUse = new HashSet<>();
    private Monitor monitor = Problems.NULL_MONITOR;
    private Map<String, String> applicationDefaults = ImmutableMap.of();
    private Map<String, String> moduleDefaults = ImmutableMap.of();
    private Map<String, ConfigurationDefaultingModule> moduleDefaultSource = ImmutableMap.of();

    /**
     * Loads properties from the given file
     *
     * @param path file path
     * @return self
     * @throws java.io.IOException errors
     */
    public ConfigurationFactoryBuilder withFile(@Nullable final String path)
            throws IOException
    {
        propertiesBuilder = propertiesBuilder.withPropertiesFile(path);
        return this;
    }

    /**
     * Loads properties from the given JSON-format file
     *
     * @param path file path
     * @return self
     * @throws java.io.IOException errors
     */
    public ConfigurationFactoryBuilder withJsonFile(@Nullable String path)
            throws IOException
    {
        propertiesBuilder = propertiesBuilder.withJsonFile(path);
        return this;
    }

    public ConfigurationFactoryBuilder withSystemProperties()
    {
        propertiesBuilder = propertiesBuilder.withSystemProperties();
        return this;
    }

    public ConfigurationFactoryBuilder withRequiredProperties(Map<String, String> requiredConfigurationProperties)
    {
        propertiesBuilder = propertiesBuilder.withRequiredProperties(requiredConfigurationProperties);
        return this;
    }

    public ConfigurationFactoryBuilder withApplicationDefaults(Map<String, String> applicationDefaults)
    {
        this.applicationDefaults = ImmutableMap.copyOf(applicationDefaults);
        expectToUse.addAll(applicationDefaults.keySet());
        return this;
    }

    public ConfigurationFactoryBuilder withModuleDefaults(Map<String, String> moduleDefaults, Map<String, ConfigurationDefaultingModule> moduleDefaultSource)
    {
        this.moduleDefaults = ImmutableMap.copyOf(moduleDefaults);
        this.moduleDefaultSource = ImmutableMap.copyOf(moduleDefaultSource);
        expectToUse.addAll(moduleDefaults.keySet());
        return this;
    }

    public ConfigurationFactoryBuilder withMonitor(Monitor monitor)
    {
        this.monitor = monitor;
        return this;
    }

    public ConfigurationFactoryBuilder withWarningsMonitor(WarningsMonitor warningsMonitor)
    {
        this.monitor = new Monitor()
        {
            @Override
            public void onError(Message errorMessage)
            {
            }

            @Override
            public void onWarning(Message warningMessage)
            {
                warningsMonitor.onWarning(warningMessage.getMessage());
            }
        };
        return this;
    }

    public ConfigurationFactory build()
    {
        return new ConfigurationFactory(propertiesBuilder.getProperties(),
                applicationDefaults,
                moduleDefaults,
                moduleDefaultSource,
                Sets.union(expectToUse, propertiesBuilder.getExpectToUse()),
                propertiesBuilder.getErrors(),
                monitor);
    }
}
