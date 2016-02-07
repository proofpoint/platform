/*
 * Copyright 2014 Proofpoint, Inc.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

@AutoValue
abstract class HealthReport
{
    @JsonProperty("host")
    abstract String getHost();

    @JsonProperty("time")
    abstract long getTime();

    @JsonProperty("results")
    abstract List<HealthResult> getResults();

    static HealthReport healthReport(String host, long time, List<HealthResult> results)
    {
        return new AutoValue_HealthReport(host, time, ImmutableList.copyOf(results));
    }
}
