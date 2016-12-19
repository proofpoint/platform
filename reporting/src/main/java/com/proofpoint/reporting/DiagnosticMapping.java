/*
 * Copyright 2016 Proofpoint, Inc.
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

import static java.util.Objects.requireNonNull;

class DiagnosticMapping
{
    private Key<?> key;
    private String namePrefix;

    DiagnosticMapping(Key<?> key, String namePrefix)
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

    String getNamePrefix()
    {
        return namePrefix;
    }

    void setNamePrefix(String namePrefix)
    {
        this.namePrefix = requireNonNull(namePrefix, "namePrefix is null");
    }
}
