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

import com.google.inject.Key;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

class HealthMapping
{
    private String nameSuffix;

    private Key<?> key;

    HealthMapping(@Nullable String nameSuffix, Key<?> key)
    {
        this.nameSuffix = nameSuffix;
        this.key = requireNonNull(key, "key is null");
    }

    @Nullable
    String getNameSuffix()
    {
        return nameSuffix;
    }

    void setNameSuffix(@Nullable String nameSuffix)
    {
        this.nameSuffix = nameSuffix;
    }

    Key<?> getKey()
    {
        return key;
    }

    void setKey(Key<?> key)
    {
        this.key = requireNonNull(key, "key is null");
    }
}
