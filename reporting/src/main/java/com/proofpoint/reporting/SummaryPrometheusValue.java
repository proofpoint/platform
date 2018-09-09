/*
 * Copyright 2018 Proofpoint, Inc.
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

@AutoValue
abstract class SummaryPrometheusValue implements PrometheusValue
{
    private static final ImmutableMap<String, String> QUANTILES = ImmutableMap.<String, String>builder()
            .put("0", "Min")
            .put("0.5", "P50")
            .put("0.75", "P75")
            .put("0.90", "P90")
            .put("0.95", "P95")
            .put("0.99", "P99")
            .put("1", "Max")
            .build();
    private static final ImmutableMap<String, String> EXTRAS = ImmutableMap.<String, String>builder()
            .put("_sum", "Sum")
            .put("_count", "Count")
            .build();

    static PrometheusValue summaryPrometheusValue(Map<String, PrometheusValue> values) {
        return new AutoValue_SummaryPrometheusValue(values);
    }

    abstract Map<String, PrometheusValue> getValues();

    @Override
    public void writeMetric(BufferedWriter writer, String name, Iterable<Entry<String, String>> tags, @Nullable Long timestamp)
            throws IOException
    {
        Map<String, PrometheusValue> values = getValues();
        for (Entry<String, String> quantile : QUANTILES.entrySet()) {
            PrometheusValue value = values.get(quantile.getValue());
            if (value != null) {
                value.writeMetric(writer, name, Iterables.concat(ImmutableMap.of("quantile", quantile.getKey()).entrySet(), tags), timestamp);
            }
        }
        for (Entry<String, String> quantile : EXTRAS.entrySet()) {
            PrometheusValue value = values.get(quantile.getValue());
            if (value != null) {
                value.writeMetric(writer, name + quantile.getKey(), tags, timestamp);
            }
        }
    }
}
