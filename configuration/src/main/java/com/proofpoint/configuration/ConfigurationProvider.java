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
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

class ConfigurationProvider<T> implements ConfigurationAwareProvider<T>
{
    private final AtomicReference<Key<T>> key = new AtomicReference<>();
    private final Class<T> configClass;
    private final AtomicReference<String> prefix = new AtomicReference<>();
    private final AtomicBoolean built = new AtomicBoolean();
    private ConfigurationFactory configurationFactory;

    ConfigurationProvider(Class<T> configClass)
    {
        requireNonNull(configClass, "configClass is null");

        key.set(Key.get(configClass));
        this.configClass = configClass;
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
        return key.get();
    }

    void setKey(Key<T> key)
    {
        checkState(!built.get(), "Provider has already produced an instance");
        this.key.set(key);
    }

    public Class<T> getConfigClass()
    {
        return configClass;
    }

    @Nullable
    public String getPrefix()
    {
        return prefix.get();
    }

    void setPrefix(String prefix)
    {
        checkState(!built.get(), "Provider has already produced an instance");
        this.prefix.set(prefix);
    }

    public ConfigurationMetadata<T> getConfigurationMetadata() {
        return ConfigurationMetadata.getConfigurationMetadata(configClass);
    }

    @Override
    public T get()
    {
        requireNonNull(configurationFactory, "configurationFactory is null");

        built.set(true);
        return configurationFactory.build(configClass, prefix.get(), key.get());
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
        return Objects.equals(this.configClass, other.configClass) && Objects.equals(this.prefix.get(), other.prefix.get());
    }
}
