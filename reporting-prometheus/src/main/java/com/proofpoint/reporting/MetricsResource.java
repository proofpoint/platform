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

import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.proofpoint.jaxrs.AccessDoesNotRequireAuthentication;
import com.proofpoint.node.NodeInfo;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

@Path("/metrics")
public class MetricsResource
{
    private final PrometheusCollector prometheusCollector;
    private final Map<String, String> instanceTags;

    @Inject
    public MetricsResource(PrometheusCollector prometheusCollector, NodeInfo nodeInfo, ReportTagConfig reportTagConfig)
    {
        this.prometheusCollector = requireNonNull(prometheusCollector, "prometheusCollector is null");
        requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(reportTagConfig, "reportTagConfig is null");

        Builder<String, String> builder = ImmutableSortedMap.naturalOrder();
        builder.put("application", nodeInfo.getApplication());
        builder.put("host", nodeInfo.getInternalHostname());
        builder.put("environment", nodeInfo.getEnvironment());
        builder.put("pool", nodeInfo.getPool());
        builder.putAll(reportTagConfig.getTags());
        this.instanceTags = builder.build();
    }

    @GET
    @AccessDoesNotRequireAuthentication
    @Produces("text/plain; version=0.0.4; charset=utf-8")
    public StreamingOutput getMetrics()
    {
        return output -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, UTF_8))) {
                for (Entry<String, Collection<TaggedValue>> entry : prometheusCollector.collectData().asMap().entrySet()) {
                    boolean first = true;

                    for (TaggedValue taggedValue : entry.getValue()) {
                        if (first) {
                            first = false;
                            writer.write("#TYPE ");
                            writer.write(entry.getKey());
                            writer.write(" gauge\n");
                        }

                        taggedValue.valueAndTimestamp().value().writeMetric(
                                writer,
                                entry.getKey(),
                                Iterables.concat(taggedValue.tags().entrySet(), instanceTags.entrySet()),
                                taggedValue.valueAndTimestamp().timestamp()
                        );
                    }
                }
            }
        };
    }
}

