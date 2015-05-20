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
package com.proofpoint.platform.sample;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.LegacyConfig;
import com.proofpoint.units.Duration;
import com.proofpoint.units.MinDuration;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class StoreConfig
{
    private Duration ttl = new Duration(1, TimeUnit.HOURS);

    @Deprecated
    @LegacyConfig(value = "store.ttl-in-ms", replacedBy = "store.ttl")
    StoreConfig setTtlInMs(int duration)
    {
        return setTtl(new Duration(duration, TimeUnit.MILLISECONDS));
    }

    @Config("store.ttl")
    StoreConfig setTtl(Duration ttl)
    {
        this.ttl = requireNonNull(ttl, "ttl must not be null");
        return this;
    }

    @MinDuration(value = "1m", message = "must be at least 1m")
    Duration getTtl()
    {
        return ttl;
    }
}
