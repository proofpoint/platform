/*
 * Copyright 2022 Proofpoint, Inc.
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
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.proofpoint.http.client.BodySource;
import com.proofpoint.http.client.DynamicBodySource;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.inject.Inject;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import static com.proofpoint.http.client.Request.Builder.preparePost;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static java.util.Objects.requireNonNull;

public class SplunkObservabilityClient
{
    private static final Logger logger = Logger.get(SplunkObservabilityClient.class);
    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final URI UPLOAD_DATAPOINTS_URI = URI.create("v2/datapoint");
    private static final URI UPLOAD_EVENTS_URI = URI.create("v2/event");
    private final String authToken;
    private final Map<String, String> instanceTags;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Inject
    SplunkObservabilityClient(NodeInfo nodeInfo, @ForSplunkObservabilityClient HttpClient httpClient, SplunkObservabilityClientConfig splunkObservabilityClientConfig,
                              ReportTagConfig reportTagConfig, ObjectMapper objectMapper)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        requireNonNull(nodeInfo,"nodeInfo is null");
        requireNonNull(splunkObservabilityClientConfig, "splunkObservabilityClientConfig is null");
        this.authToken = splunkObservabilityClientConfig.getAuthToken();
        requireNonNull(reportTagConfig, "reportTagConfig is null");

        Builder<String, String> builder = ImmutableMap.builder();
        builder.put("application", nodeInfo.getApplication());
        if (splunkObservabilityClientConfig.isIncludeHostTag()) {
            builder.put("host", nodeInfo.getInternalHostname());
        }
        builder.put("environment", nodeInfo.getEnvironment());
        builder.put("pool", nodeInfo.getPool());
        builder.putAll(reportTagConfig.getTags());
        this.instanceTags = builder.build();

        this.httpClient = requireNonNull(httpClient, "httpClient is null");
    }

    public void report(long systemTimeMillis, Table<String, Map<String, String>, Object> collectedData)
    {
        //Reporting data points
        reportEach(systemTimeMillis, collectedData.cellSet().stream()
                .filter(cell -> cell.getValue() instanceof Number)
                .collect(ImmutableTable.toImmutableTable(
                        Table.Cell::getRowKey,
                        Table.Cell::getColumnKey,
                        Table.Cell::getValue
                )), true);

        //Reporting events
        reportEach(systemTimeMillis, collectedData.cellSet().stream()
                .filter(cell -> !(cell.getValue() instanceof Number))
                .filter(cell -> !cell.getValue().equals(""))
                .collect(ImmutableTable.toImmutableTable(
                        Table.Cell::getRowKey,
                        Table.Cell::getColumnKey,
                        Table.Cell::getValue
                )), false);
    }

    private void reportEach(long systemTimeMillis, Table<String, Map<String, String>, Object> collectedData, boolean reportDatapoints)
    {
        if (collectedData.isEmpty()) {
            return;
        }
        URI uploadUri;
        BodySource jsonBodySource;
        if (reportDatapoints) {
            uploadUri = UPLOAD_DATAPOINTS_URI;
            jsonBodySource = new DataPointJsonBodySource(systemTimeMillis, collectedData);
        } else {
            uploadUri = UPLOAD_EVENTS_URI;
            jsonBodySource = new EventJsonBodySource(systemTimeMillis, collectedData);
        }
        Request request = preparePost()
                .setUri(uploadUri)
                .setHeader("Content-Type", "application/json")
                .setHeader("X-SF-Token", this.authToken)
                .setBodySource(jsonBodySource)
                .build();
        try {
            StringResponse response = httpClient.execute(request, createStringResponseHandler());
            if (response.getStatusCode() != 200) {
                logger.warn("Failed to report stats: %s %s %s", response.getStatusCode(), response.getStatusMessage(), response.getBody());
            }
        } catch (RuntimeException e) {
            logger.warn(e, "Exception when trying to report stats");
        }
    }

    private static abstract class CollectedDataPoint
    {
        private static final Pattern NOT_ACCEPTED_CHARACTER_PATTERN_DIMENSION_NAME = Pattern.compile("[^-A-Za-z0-9_]");
        private static final Pattern NOT_ALPHA_PREFIX_PATTERN = Pattern.compile("^[^A-Za-z]+[-A-Za-z0-9_]*");
        private static final Pattern PROHIBITED_ALPHA_PREFIXES_PATTERN = Pattern.compile("^(sf_|aws_|gcp_|azure_)[-A-Za-z0-9_]*");
        private static final Pattern CLEAN_DIMENSION_VALUE = Pattern.compile("[^-A-Za-z0-9./_]");
        private static final String RULE_ENFORCEMENT_PREFIX = "z_";
        private static final int MAX_DIMENSION_NAME_LENGTH = 128;
        private static final int MAX_DIMENSION_VALUE_LENGTH = 256;
        private static final String collectedNameToDimension = "Platform_Metric_Name";

        @JsonProperty
        private final long timestamp;
        @JsonProperty
        private final Map<String, String> dimensions;

        @SuppressWarnings("ConstantConditions")
        @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
        CollectedDataPoint(long systemTimeMillis, Cell<String, Map<String, String>, Object> cell, Map<String, String> instanceTags, boolean isEvent)
        {
            timestamp = systemTimeMillis;
            HashMap<String, String> dims = new HashMap<>(instanceTags);
            for (Entry<String, String> entry : cell.getColumnKey().entrySet()) {
                String dimensionName = enforceDimensionNameRules(entry.getKey());
                String dimensionValue = CLEAN_DIMENSION_VALUE.matcher(entry.getValue()).replaceAll("_");
                if (dimensionName.length() == 0 || dimensionValue.length() == 0) {
                    logger.warn("Dropped empty dimension name: %s or value: %s", dimensionName, dimensionValue);
                    continue;
                }
                putOrLogDroppedDuplicateDimension(dims, dimensionName, enforceLengthMaximum(dimensionValue, MAX_DIMENSION_VALUE_LENGTH));
            }
            if (isEvent) {
                String dimensionValue = CLEAN_DIMENSION_VALUE.matcher(cell.getRowKey()).replaceAll("_");
                putOrLogDroppedDuplicateDimension(dims, collectedNameToDimension, enforceLengthMaximum(dimensionValue, MAX_DIMENSION_VALUE_LENGTH));
            }
            dimensions = dims;
        }

        private String enforceLengthMaximum(String str, int maximum)
        {
            if (str.length() > maximum) {
                str = str.substring(0,maximum);
            }
            return str;
        }

        private String enforceDimensionNameRules(String dimensionName)
        {
            dimensionName = NOT_ACCEPTED_CHARACTER_PATTERN_DIMENSION_NAME.matcher(dimensionName).replaceAll("_");
            if (NOT_ALPHA_PREFIX_PATTERN.matcher(dimensionName).matches() || PROHIBITED_ALPHA_PREFIXES_PATTERN.matcher(dimensionName).matches()) {
                dimensionName = RULE_ENFORCEMENT_PREFIX + dimensionName;
            }
            dimensionName = enforceLengthMaximum(dimensionName, MAX_DIMENSION_NAME_LENGTH);
            return dimensionName;
        }

        private void putOrLogDroppedDuplicateDimension(Map<String,String> dimensions, String dimensionName, String dimensionValue)
        {
            if (dimensions.putIfAbsent(dimensionName, dimensionValue) != null) {
                logger.warn("Dropped duplicate dimension name: %s, value %s", dimensionName, dimensionValue);
            }
        }
    }

    private static class DataPoint extends CollectedDataPoint
    {
        private static final Pattern NOT_ACCEPTED_CHARACTER_PATTERN_METRIC = Pattern.compile("[^-A-Za-z0-9./_]");
        private static final int MAX_METRIC_LENGTH = 256;
        @JsonProperty
        private final String metric;
        @JsonProperty
        private final Object value;

        @SuppressWarnings("ConstantConditions")
        @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
        DataPoint(long systemTimeMillis, Cell<String, Map<String, String>, Object> cell, Map<String, String> instanceTags)
        {
            super(systemTimeMillis, cell, instanceTags, false);
            String metricName = NOT_ACCEPTED_CHARACTER_PATTERN_METRIC.matcher(cell.getRowKey()).replaceAll("_");
            metric = super.enforceLengthMaximum(metricName,MAX_METRIC_LENGTH);
            value = cell.getValue();
        }
    }

    private static class Event extends CollectedDataPoint
    {
        private static final Pattern NOT_ACCEPTED_CHARACTER_PATTERN_EVENTTYPE = Pattern.compile("[^\\x21-\\x7E]");
        private static final int MAX_EVENTTYPE_LENGTH = 256;
        @JsonProperty
        private static final String category = "USER_DEFINED";
        @JsonProperty
        private final String eventType;

        @SuppressWarnings("ConstantConditions")
        @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
        Event(long systemTimeMillis, Cell<String, Map<String, String>, Object> cell, Map<String, String> instanceTags)
        {
            super(systemTimeMillis, cell, instanceTags, true);
            String eventString = NOT_ACCEPTED_CHARACTER_PATTERN_EVENTTYPE.matcher((String) cell.getValue()).replaceAll("_");
            eventString = super.enforceLengthMaximum(eventString, MAX_EVENTTYPE_LENGTH);
            eventType = eventString;
        }
    }

    private abstract class JsonBodySource implements DynamicBodySource
    {
        private final long systemTimeMillis;
        private final Table<String, Map<String, String>, Object> collectedData;

        JsonBodySource(long systemTimeMillis, Table<String, Map<String, String>, Object> collectedData)
        {
            this.systemTimeMillis = systemTimeMillis;
            this.collectedData = collectedData;
        }
    }

    private class DataPointJsonBodySource extends JsonBodySource
    {
        DataPointJsonBodySource(long systemTimeMillis, Table<String, Map<String, String>, Object> collectedData)
        {
            super(systemTimeMillis,collectedData);
        }

        @Override
        public Writer start(final OutputStream out)
            throws Exception
        {
            final BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(out);
            final JsonGenerator generator = JSON_FACTORY.createGenerator(bufferedOutputStream, JsonEncoding.UTF8)
                    .setCodec(objectMapper);
            final Iterator<Cell<String, Map<String, String>, Object>> iterator = super.collectedData.cellSet().iterator();

            generator.writeStartObject();
            generator.writeArrayFieldStart("gauge");

            return () -> {
                if (iterator.hasNext()) {
                    generator.writeObject(new DataPoint(super.systemTimeMillis, iterator.next(), instanceTags));
                }
                else {
                    generator.writeEndArray();
                    generator.writeEndObject();
                    generator.flush();
                    bufferedOutputStream.flush();
                    out.close();
                }
            };
        }
    }

    private class EventJsonBodySource extends JsonBodySource
    {
        EventJsonBodySource(long systemTimeMillis, Table<String, Map<String, String>, Object> collectedData)
        {
            super(systemTimeMillis,collectedData);
        }

        @Override
        public Writer start(final OutputStream out)
                throws Exception
        {
            final BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(out);
            final JsonGenerator generator = JSON_FACTORY.createGenerator(bufferedOutputStream, JsonEncoding.UTF8)
                    .setCodec(objectMapper);
            final Iterator<Cell<String, Map<String, String>, Object>> iterator = super.collectedData.cellSet().iterator();

            generator.writeStartArray();

            return () -> {
                if (iterator.hasNext()) {
                    generator.writeObject(new Event(super.systemTimeMillis, iterator.next(), instanceTags));
                }
                else {
                    generator.writeEndArray();
                    generator.flush();
                    bufferedOutputStream.flush();
                    out.close();
                }
            };
        }
    }
}
