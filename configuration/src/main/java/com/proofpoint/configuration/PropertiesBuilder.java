/*
 * Copyright 2020 Proofpoint, Inc.
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
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static java.lang.String.format;

public class PropertiesBuilder
{
    private final Map<String, String> properties = new HashMap<>();
    private final Set<String> expectToUse = new HashSet<>();
    private final Collection<String> errors = new ArrayList<>();

    /**
     * Loads properties from the given file
     *
     * @param path file path
     * @return self
     * @throws java.io.IOException errors
     */
    public PropertiesBuilder withPropertiesFile(@Nullable String path)
            throws IOException
    {
        if (path == null) {
            return this;
        }

        Properties properties = new Properties() {
            @SuppressWarnings("UseOfPropertiesAsHashtable")
            @Override
            public synchronized Object put(Object key, Object value) {
                Object old = super.put(key, value);
                if (old != null) {
                    errors.add(format("Duplicate configuration property '%s' in file %s", key, path));
                }
                return old;
            }
        };

        try (Reader reader = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        mergeProperties(properties);
        expectToUse.addAll(properties.stringPropertyNames());
        return this;
    }

    public PropertiesBuilder withSystemProperties()
    {
        mergeProperties(System.getProperties());
        return this;
    }

    private void mergeProperties(Map<Object, Object> properties)
    {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            this.properties.put(entry.getKey().toString(), entry.getValue().toString());
        }
    }

    public PropertiesBuilder withRequiredProperties(Map<String, String> requiredConfigurationProperties)
    {
        properties.putAll(requiredConfigurationProperties);
        expectToUse.addAll(requiredConfigurationProperties.keySet());
        return this;
    }

    public Map<String, String> getProperties()
    {
        return ImmutableMap.copyOf(properties);
    }

    public Set<String> getExpectToUse()
    {
        return ImmutableSet.copyOf(expectToUse);
    }

    public Collection<String> getErrors()
    {
        return ImmutableList.copyOf(errors);
    }
}
