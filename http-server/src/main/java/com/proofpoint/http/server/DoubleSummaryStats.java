/*
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
package com.proofpoint.http.server;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.proofpoint.units.Duration;

import java.util.DoubleSummaryStatistics;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@JsonPropertyOrder({"count", "max"})
class DoubleSummaryStats
{
    private final DoubleSummaryStatistics stats;

    DoubleSummaryStats(DoubleSummaryStatistics stats)
    {
        this.stats = requireNonNull(stats, "stats is null");
    }

    @JsonProperty
    public Duration getMax()
    {
        return new Duration(stats.getMax(), MILLISECONDS);
    }

    @JsonProperty
    public long getCount()
    {
        return stats.getCount();
    }
}
