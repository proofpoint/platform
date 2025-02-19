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

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableSortedSet.Builder;
import com.google.common.collect.Ordering;
import com.google.inject.Key;
import com.proofpoint.configuration.ConfigurationMetadata.AttributeMetadata;
import jakarta.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SortedSet;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.proofpoint.configuration.ConfigurationMetadata.getConfigurationMetadata;
import static com.proofpoint.configuration.ConfigurationMetadata.isConfigClass;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public class ConfigurationInspector
{
    public SortedSet<ConfigRecord<?>> inspect(ConfigurationFactory configurationFactory)
    {
        ImmutableSortedSet.Builder<ConfigRecord<?>> builder = ImmutableSortedSet.naturalOrder();
        for (ConfigurationIdentity<?> configurationIdentity : configurationFactory.getRegisteredConfigs()) {
            builder.add(ConfigRecord.createConfigRecord(configurationFactory, configurationIdentity));
        }

        return builder.build();
    }

    private static <T> T newDefaultInstance(ConfigurationMetadata<T> configurationMetadata)
    {
        try {
            return configurationMetadata.getConstructor().newInstance();
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static String getValue(Method getter, Object instance, String defaultValue)
    {
        if (getter == null || instance == null) {
            return defaultValue;
        }

        try {
            Object value = getter.invoke(instance);
            if (value == null) {
                return "null";
            }
            return value.toString();
        }
        catch (Throwable e) {
            return "-- ERROR --";
        }
    }

    public static class ConfigRecord<T> implements Comparable<ConfigRecord<?>>
    {
        private final Key<T> key;
        private final Class<T> configClass;
        private final String prefix;
        private final SortedSet<ConfigAttribute> attributes;

        static <T> ConfigRecord<T> createConfigRecord(ConfigurationFactory configurationFactory, ConfigurationIdentity<T> configurationIdentity)
        {
            return new ConfigRecord<>(configurationFactory, configurationIdentity.configClass(), configurationIdentity.prefix(), configurationIdentity.key());
        }

        private ConfigRecord(ConfigurationFactory configurationFactory, Class<T> configClass, @Nullable String prefix, @Nullable Key<T> key)
        {
            this.configClass = requireNonNull(configClass, "configClass is null");
            this.prefix = prefix;
            this.key = key;

            ConfigurationMetadata<T> metadata = getConfigurationMetadata(configClass);

            T instance = null;
            try {
                instance = configurationFactory.build(configClass, prefix);
            }
            catch (Throwable ignored) {
                // provider could blow up for any reason, which is fine for this code
                // this is catch throwable because we may get an AssertionError
            }

            T defaults = null;
            try {
                defaults = configurationFactory.buildDefaults(configClass, prefix);
            }
            catch (Throwable ignored) {
            }

            prefix = prefix == null ? "" : (prefix + ".");

            ImmutableSortedSet.Builder<ConfigAttribute> builder = ImmutableSortedSet.naturalOrder();
            enumerateConfig(metadata, instance, defaults, prefix, builder, "");
            attributes = builder.build();
        }

        private static <T> void enumerateConfig(ConfigurationMetadata<T> metadata, T instance, T defaults, String prefix, Builder<ConfigAttribute> builder, String attributePrefix)
        {
            for (AttributeMetadata attribute : metadata.getAttributes().values()) {
                String propertyName = prefix + attribute.getInjectionPoint().getProperty();
                Method getter = attribute.getGetter();

                String description = requireNonNullElse(attribute.getDescription(), "");

                final MapClasses mapClasses = attribute.getMapClasses();
                if (getter != null && instance != null && !attribute.isSecuritySensitive() && mapClasses != null) {
                    final Class<?> valueClass = mapClasses.getValue();
                    Class<?> valueConfigClass = null;
                    if (isConfigClass(valueClass)) {
                        valueConfigClass = valueClass;
                    }

                    enumerateMap(instance, attributePrefix + attribute.getName(), propertyName, description, getter, valueConfigClass, builder);
                }
                else {
                    String defaultValue = getValue(getter, defaults, "-- none --");
                    String currentValue = getValue(getter, instance, "-- n/a --");

                    builder.add(new ConfigAttribute(attributePrefix + attribute.getName(), propertyName, defaultValue, currentValue, description, attribute.isSecuritySensitive()));
                }
            }
        }

        private static <T, K, V> void enumerateMap(T instance, String attributeName, String propertyName, String description, Method getter, Class<V> valueConfigClass, Builder<ConfigAttribute> builder)
        {
            Map<K, V> map;
            try {
                map = (Map<K, V>) getter.invoke(instance);
            }
            catch (Throwable e) {
                builder.add(new ConfigAttribute(attributeName, propertyName, "-- n/a --", "-- ERROR --", description, false));
                return;
            }

            if (map == null) {
                builder.add(new ConfigAttribute(attributeName, propertyName, "-- n/a --", "null", description, false));
                return;
            }
            if (map.isEmpty()) {
                builder.add(new ConfigAttribute(attributeName, propertyName, "-- n/a --", "-- empty --", description, false));
                return;
            }
            for (Entry<K, V> entry : map.entrySet()) {
                if (valueConfigClass != null) {
                    enumerateConfig(getConfigurationMetadata(valueConfigClass),
                            entry.getValue(),
                            newDefaultInstance(getConfigurationMetadata(valueConfigClass)),
                            propertyName + "." + entry.getKey().toString() + ".",
                            builder,
                            attributeName + "[" + entry.getKey().toString() + "]");
                }
                else {
                    builder.add(new ConfigAttribute(attributeName + "[" + entry.getKey().toString() + "]",
                            propertyName + "." + entry.getKey().toString(),
                            "-- n/a --", entry.getValue().toString(), description, false));
                }
            }
        }

        public String getComponentName()
        {
            Key<?> key = getKey();
            if (key == null) {
                return "";
            }
            String componentName = "";
            if (key.getAnnotationType() != null) {
                componentName = "@" + key.getAnnotationType().getSimpleName() + " ";
            }
            componentName += key.getTypeLiteral();
            return componentName;
        }

        @Nullable
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

        public SortedSet<ConfigAttribute> getAttributes()
        {
            return attributes;
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
            final ConfigRecord other = (ConfigRecord) obj;
            return Objects.equals(this.configClass, other.configClass) && Objects.equals(this.prefix, other.prefix);
        }

        @Override
        public int compareTo(ConfigRecord<?> that)
        {
            return ComparisonChain.start()
                    .compare(this.configClass.getCanonicalName(), that.configClass.getCanonicalName())
                    .compare(this.prefix, that.prefix, Ordering.natural().nullsFirst())
                    .result();
        }
    }

    public static class ConfigAttribute implements Comparable<ConfigAttribute>
    {
        private final String attributeName;
        private final String propertyName;
        private final String defaultValue;
        private final String currentValue;
        private final String description;

        // todo this class needs to be updated to include the concept of deprecated property names

        private ConfigAttribute(String attributeName, String propertyName, String defaultValue, String currentValue, String description, boolean securitySensitive)
        {
            requireNonNull(attributeName, "attributeName is null");
            requireNonNull(propertyName, "propertyName is null");
            requireNonNull(defaultValue, "defaultValue is null");
            requireNonNull(currentValue, "currentValue is null");
            requireNonNull(description, "description is null");

            this.attributeName = attributeName;
            this.propertyName = propertyName;
            if (securitySensitive) {
                this.defaultValue = "[REDACTED]";
            }
            else {
                this.defaultValue = defaultValue;
            }
            if (securitySensitive) {
                this.currentValue = "[REDACTED]";
            }
            else {
                this.currentValue = currentValue;
            }
            this.description = description;
        }

        public String getAttributeName()
        {
            return attributeName;
        }

        public String getPropertyName()
        {
            return propertyName;
        }

        public String getDefaultValue()
        {
            return defaultValue;
        }

        public String getCurrentValue()
        {
            return currentValue;
        }

        public String getDescription()
        {
            return description;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ConfigAttribute that = (ConfigAttribute) o;

            return attributeName.equals(that.attributeName);
        }

        @Override
        public int hashCode()
        {
            return attributeName.hashCode();
        }

        @Override
        public int compareTo(ConfigAttribute that)
        {
            return this.attributeName.compareTo(that.attributeName);
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("attributeName", attributeName)
                    .add("propertyName", propertyName)
                    .add("defaultValue", defaultValue)
                    .add("currentValue", currentValue)
                    .add("description", description)
                    .toString();
        }
    }
}
