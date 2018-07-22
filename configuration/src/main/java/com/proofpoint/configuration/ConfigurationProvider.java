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

import com.google.inject.Key;

import javax.inject.Inject;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * @deprecated Will no longer be public.
 */
@Deprecated
public class ConfigurationProvider<T> implements ConfigurationAwareProvider<T>
{
    private final Key<T> key;
    private final Class<T> configClass;
    private final String prefix;
    private ConfigurationFactory configurationFactory;

    /**
     * @deprecated Will no longer be public.
     */
    @Deprecated
    public ConfigurationProvider(Key<T> key, Class<T> configClass, String prefix)
    {
        requireNonNull(key, "key is null");
        requireNonNull(configClass, "configClass is null");

        this.key = key;
        this.configClass = configClass;
        this.prefix = prefix;
    }

    @Override
    @Inject
    public void setConfigurationFactory(ConfigurationFactory configurationFactory)
    {
        this.configurationFactory = configurationFactory;
    }

    ConfigurationFactory getConfigurationFactory()
    {
        return configurationFactory;
    }

    public Key<T> getKey()
    {
        return key;
    }

    public Class<T> getConfigClass()
    {
        return configClass;
    }

    public String getPrefix()
    {
        return prefix;
    }

    public ConfigurationMetadata<T> getConfigurationMetadata() {
        return ConfigurationMetadata.getConfigurationMetadata(configClass);
    }

    @Override
    public T get()
    {
        requireNonNull(configurationFactory, "configurationFactory is null");

        return configurationFactory.build(configClass, prefix, key);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(configClass, prefix);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final ConfigurationProvider other = (ConfigurationProvider) obj;
        return Objects.equals(this.configClass, other.configClass) && Objects.equals(this.prefix, other.prefix);
    }
}
