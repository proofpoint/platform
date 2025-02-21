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
package com.proofpoint.tracetoken;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public class TraceToken
        extends ForwardingMap<String, String>
{
    private final Map<String, String> delegate;

    TraceToken(Map<String, String> map)
    {
        requireNonNull(map, "map is null");
        requireNonNull(map.get("id"), "map{id} is null");
        delegate = ImmutableMap.copyOf(map);
    }

    @JsonCreator
    static TraceToken createJson(Map<String, String> map)
    {
        return new TraceToken(Maps.filterKeys(map, key -> !key.startsWith("_")));
    }

    @JsonValue
    Map<String, String> getJson()
    {
        return Maps.filterKeys(this, key -> !key.startsWith("_"));
    }

    @Override
    protected Map<String, String> delegate()
    {
        return delegate;
    }

    @Override
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public String toString()
    {
        if (delegate.size() == 1) {
            return delegate.get("id");
        }
        return super.toString();
    }
}
