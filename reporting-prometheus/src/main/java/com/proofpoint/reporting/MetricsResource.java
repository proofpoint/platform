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

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.StreamingOutput;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

@Path("/metrics")
public class MetricsResource
{
    private static final Pattern LABEL_NOT_ACCEPTED_CHARACTER_PATTERN = Pattern.compile("[^A-Za-z0-9_]");
    private static final Pattern INITIAL_DIGIT_PATTERN = Pattern.compile("[0-9]");
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
    public StreamingOutput getMetrics() {
        return output -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output))) {
                for (Entry<String, Collection<TaggedValue>> entry : prometheusCollector.collectData().asMap().entrySet()) {
                    boolean first = true;

                    for (TaggedValue taggedValue : entry.getValue()) {
                        if (first) {
                            first = false;
                            writer.write("#TYPE ");
                            writer.write(entry.getKey());
                            writer.append(' ');
                            writer.write(taggedValue.getType());
                            writer.append('\n');
                        }

                        writer.write(entry.getKey());

                        char prefix = '{';
                        for (Entry<String, String> tag : Iterables.concat(taggedValue.getTags().entrySet(), instanceTags.entrySet())) {
                            writer.append(prefix);
                            prefix = ',';
                            String label = LABEL_NOT_ACCEPTED_CHARACTER_PATTERN.matcher(tag.getKey()).replaceAll("_");
                            String value = tag.getValue();
                            if (INITIAL_DIGIT_PATTERN.matcher(label).lookingAt()) {
                                writer.append('_');
                            }
                            writer.write(label);
                            writer.append("=\"");
                            for (int i = 0; i < value.length(); i++) {
                                char c = value.charAt(i);
                                switch (c) {
                                    case '\\':
                                        writer.append("\\\\");
                                        break;
                                    case '\"':
                                        writer.append("\\\"");
                                        break;
                                    case '\n':
                                        writer.append("\\n");
                                        break;
                                    default:
                                        writer.append(c);
                                }
                            }
                            writer.append("\"");
                        }
                        if (prefix == ',') {
                            writer.append('}');
                        }
                        writer.append(' ');
                        writer.write(taggedValue.getValue().toString());
                        writer.append('\n');
                    }
                }
            }
        };
    }
}

