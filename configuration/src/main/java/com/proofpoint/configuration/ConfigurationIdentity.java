/*
 * Copyright 2017 Proofpoint, Inc.
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

import com.google.auto.value.AutoValue;
import com.google.inject.Key;
import jakarta.annotation.Nullable;

@AutoValue
abstract class ConfigurationIdentity<T>
{
    private Key<T> key;

    static <T> ConfigurationIdentity<T> configurationIdentity(Class<T> clazz, @Nullable String prefix, @Nullable Key<T> key)
    {
        ConfigurationIdentity<T> identity = new AutoValue_ConfigurationIdentity<>(clazz, prefix);
        identity.key = key;
        return identity;
    }

    abstract Class<T> getConfigClass();

    @Nullable
    abstract String getPrefix();

    // Must not participate in equals()
    Key<T> getKey()
    {
        return key;
    }
}
