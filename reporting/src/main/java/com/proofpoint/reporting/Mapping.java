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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Key;
import jakarta.annotation.Nullable;

import java.util.Map;

import static java.util.Objects.requireNonNull;

class Mapping
{
    private Key<?> key;
    private boolean applicationPrefix = false;
    private String namePrefix;
    private Map<String, String> tags = ImmutableMap.of();
    private String legacyName = null;

    Mapping(Key<?> key, String namePrefix)
    {
        this.key = key;
        this.namePrefix = namePrefix;
    }

    Key<?> getKey()
    {
        return key;
    }

    void setKey(Key<?> key)
    {
        this.key = requireNonNull(key, "key is null");
    }

    public boolean isApplicationPrefix()
    {
        return applicationPrefix;
    }

    public void setApplicationPrefix(boolean applicationPrefix)
    {
        this.applicationPrefix = applicationPrefix;
    }

    String getNamePrefix()
    {
        return namePrefix;
    }

    void setNamePrefix(String namePrefix)
    {
        this.namePrefix = requireNonNull(namePrefix, "namePrefix is null");
    }

    public Map<String, String> getTags()
    {
        return tags;
    }

    public void setTags(Map<String, String> tags)
    {
        this.tags = ImmutableMap.copyOf(tags);
    }

    @Nullable
    String getLegacyName()
    {
        return legacyName;
    }

    void setLegacyName(String legacyName)
    {
        this.legacyName = requireNonNull(legacyName, "legacyName is null");
    }
}
