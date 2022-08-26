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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.HttpStatus;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.testing.TestingHttpClient;
import com.proofpoint.http.client.testing.TestingHttpClient.Processor;
import com.proofpoint.json.ObjectMapperProvider;
import com.proofpoint.node.NodeConfig;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.proofpoint.http.client.testing.BodySourceTester.writeBodySourceTo;
import static com.proofpoint.http.client.testing.TestingResponse.mockResponse;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestSplunkObservabilityClient
{
    private static final int TEST_TIME = 1234567890;
    private NodeInfo nodeInfo;
    private Table<String, Map<String, String>, Object> collectedData;
    private HttpClient httpClientDatapoint;
    private HttpClient httpClientEvent;
    private HashMap<String, List<Map<String, Object>>> sentDataPointsJson;
    private List<Map<String, Object>> sentEventsJson;
    private final ObjectMapper objectMapper = new ObjectMapperProvider().get();

    @BeforeMethod
    public void setup()
    {
        nodeInfo = new NodeInfo("test-application", new NodeConfig()
                .setEnvironment("test_environment")
                .setNodeInternalHostname("test.hostname")
                .setPool("test_pool")
        );

        collectedData = HashBasedTable.create();
        collectedData.put("Foo.Size", ImmutableMap.of(), 1.1);
        collectedData.put("Foo.Ba:r.Size", ImmutableMap.of("dimension1","B\\a\"z"), 1.2);

        httpClientDatapoint = new TestingHttpClient(new TestingDatapointResponseFunction());
        httpClientEvent = new TestingHttpClient(new TestingEventResponseFunction());
        sentDataPointsJson = null;
        sentEventsJson = null;
    }

    @Test
    public void testReportingDisabled()
    {
        httpClientDatapoint = new TestingHttpClient();
        SplunkObservabilityClient client = new SplunkObservabilityClient(nodeInfo, httpClientDatapoint,
                new SplunkObservabilityClientConfig().setEnabled(false).setAuthToken("test"), new ReportTagConfig(), objectMapper);
        client.report(System.currentTimeMillis(), collectedData);
    }

    @Test
    public void testReportDataPoints()
    {
        SplunkObservabilityClient client = new SplunkObservabilityClient(nodeInfo, httpClientDatapoint,
                new SplunkObservabilityClientConfig().setAuthToken("test"), new ReportTagConfig(), objectMapper);
        client.report(TEST_TIME, collectedData);
        assertEquals(sentDataPointsJson.get("gauge").size(), 2);

        for (Map<String, Object> map : sentDataPointsJson.get("gauge")) {
            assertEquals(map.keySet(), Set.of("metric", "timestamp", "value", "dimensions"));
            assertEquals(map.get("timestamp"), TEST_TIME);
            Map<String, String> tags = (Map<String, String>) map.get("dimensions");
            assertEquals(tags.get("application"), "test-application");
            assertEquals(tags.get("host"), "test.hostname");
            assertEquals(tags.get("environment"), "test_environment");
            assertEquals(tags.get("pool"), "test_pool");
        }
        assertEquals(sentDataPointsJson.get("gauge").get(0).get("metric"), "Foo.Ba_r.Size");
        assertEquals(sentDataPointsJson.get("gauge").get(1).get("metric"), "Foo.Size");
        assertEquals(sentDataPointsJson.get("gauge").get(0).get("value"), 1.2);
        assertEquals(sentDataPointsJson.get("gauge").get(1).get("value"), 1.1);
        Map<String, String> dimensions = (Map<String, String>) sentDataPointsJson.get("gauge").get(0).get("dimensions");
        assertEquals(dimensions.keySet(), Set.of("application", "host", "environment", "pool", "dimension1"));
        assertEquals(dimensions.get("dimension1"), "B_a_z"); // "B\\a\"z");
        dimensions = (Map<String, String>) sentDataPointsJson.get("gauge").get(1).get("dimensions");
        assertEquals(dimensions.keySet(), Set.of("application", "host", "environment", "pool"));
    }

    @Test
    public void testBadDimensions()
    {
        SplunkObservabilityClient client = new SplunkObservabilityClient(nodeInfo, httpClientDatapoint,
                new SplunkObservabilityClientConfig().setAuthToken("test"), new ReportTagConfig(), objectMapper);
        collectedData = HashBasedTable.create();
        collectedData.put("Foo\\_8-_.bar", ImmutableMap.of("8di/.me_ns-ion2","B:a_d-i.s=h",
                "sf_","dim/en._si#*on-v!alu8e",
                "gcp_9dke", "test3",
                "ab97_sf_aws_gcp_azure_10_ak","test4"), 6);
        client.report(TEST_TIME, collectedData);
        assertEquals(sentDataPointsJson.get("gauge").size(), 1);

        assertEquals(sentDataPointsJson.get("gauge").get(0).get("metric"),"Foo__8-_.bar");
        assertEquals(sentDataPointsJson.get("gauge").get(0).get("value"), 6);
        Map<String, String> dimensions = (Map<String, String>) sentDataPointsJson.get("gauge").get(0).get("dimensions");
        assertEquals(dimensions.keySet(), Set.of("application", "host", "environment", "pool", "z_8di__me_ns-ion2", "z_sf_","z_gcp_9dke","ab97_sf_aws_gcp_azure_10_ak"));
        assertEquals(dimensions.get("z_8di__me_ns-ion2"), "B_a_d-i.s_h");
        assertEquals(dimensions.get("z_sf_"), "dim/en._si__on-v_alu8e");
        assertEquals(dimensions.get("z_gcp_9dke"), "test3");
        assertEquals(dimensions.get("ab97_sf_aws_gcp_azure_10_ak"), "test4");
    }

    @Test
    public void testEmptyDimensions()
    {
        SplunkObservabilityClient client = new SplunkObservabilityClient(nodeInfo, httpClientDatapoint,
                new SplunkObservabilityClientConfig().setAuthToken("test"), new ReportTagConfig(), objectMapper);
        collectedData = HashBasedTable.create();
        collectedData.put("Foo.String", ImmutableMap.of("afds",""), 2);
        collectedData.put("Foo.String2", ImmutableMap.of("","dfa"), 3);
        client.report(TEST_TIME, collectedData);
        assertEquals(sentDataPointsJson.get("gauge").size(), 2);
        Map<String, String> dimensions = (Map<String, String>) sentDataPointsJson.get("gauge").get(0).get("dimensions");
        assertEquals(dimensions.keySet(), Set.of("application", "host", "environment", "pool"));
        dimensions = (Map<String, String>) sentDataPointsJson.get("gauge").get(1).get("dimensions");
        assertEquals(dimensions.keySet(), Set.of("application", "host", "environment", "pool"));
    }

    @Test
    public void testDuplicateDimensions()
    {
        SplunkObservabilityClient client = new SplunkObservabilityClient(nodeInfo, httpClientDatapoint,
                new SplunkObservabilityClientConfig().setAuthToken("test"), new ReportTagConfig(), objectMapper);
        collectedData = HashBasedTable.create();
        collectedData.put("Foo.String", ImmutableMap.of("test_name","value_1",
                "test&name", "value_2"), 2);
        client.report(TEST_TIME, collectedData);
        assertEquals(sentDataPointsJson.get("gauge").size(), 1);
        Map<String, String> dimensions = (Map<String, String>) sentDataPointsJson.get("gauge").get(0).get("dimensions");
        assertEquals(dimensions.keySet(), Set.of("application", "host", "environment", "pool", "test_name"));
        assertEquals(dimensions.get("test_name"), "value_1");
    }

    @Test
    public void testReportEvents()
    {
        SplunkObservabilityClient client = new SplunkObservabilityClient(nodeInfo, httpClientEvent,
                new SplunkObservabilityClientConfig().setAuthToken("test"), new ReportTagConfig(), objectMapper);
        collectedData = HashBasedTable.create();
        collectedData.put("Foo.String", ImmutableMap.of(), "test value");
        client.report(TEST_TIME, collectedData);
        assertEquals(sentEventsJson, List.of(
                ImmutableMap.of(
                        "timestamp", TEST_TIME,
                        "dimensions", ImmutableMap.of(
                                "application", "test-application",
                                "host", "test.hostname",
                                "environment", "test_environment",
                                "pool", "test_pool",
                                "Platform_Metric_Name","Foo.String"
                        ),
                        "eventType", "test_value"
                )
        ));
    }

    @Test
    public void testEmptyEventType()
    {
        SplunkObservabilityClient client = new SplunkObservabilityClient(nodeInfo, httpClientEvent,
                new SplunkObservabilityClientConfig().setAuthToken("test"), new ReportTagConfig(), objectMapper);
        collectedData = HashBasedTable.create();
        collectedData.put("Foo.String", ImmutableMap.of(), "");
        client.report(TEST_TIME, collectedData);
        assertNull(sentEventsJson);
    }

    @Test
    public void testConfiguredTags()
    {
        SplunkObservabilityClient client = new SplunkObservabilityClient(nodeInfo, httpClientDatapoint,
                new SplunkObservabilityClientConfig().setAuthToken("test"), new ReportTagConfig()
                        .setTags(ImmutableMap.of("foo", "ba:r", "baz", "quux")), objectMapper);
        client.report(TEST_TIME, collectedData);
        assertEquals(sentDataPointsJson.get("gauge").size(),2);

        for (Map<String, Object> map : sentDataPointsJson.get("gauge")) {
            assertEquals(map.keySet(), Set.of("metric", "timestamp", "value", "dimensions"));
            Map<String, String> dimensions = (Map<String, String>) map.get("dimensions");
            assertEquals(dimensions.get("foo"), "ba:r");
            assertEquals(dimensions.get("baz"), "quux");
        }
        Map<String, String> dimensions = (Map<String, String>) sentDataPointsJson.get("gauge").get(0).get("dimensions");
        assertEquals(dimensions.keySet(), Set.of("application", "host", "environment", "pool", "foo", "baz", "dimension1"));
        dimensions = (Map<String, String>) sentDataPointsJson.get("gauge").get(1).get("dimensions");
        assertEquals(dimensions.keySet(), Set.of("application", "host", "environment", "pool", "foo", "baz"));
    }

    @Test
    public void testNoReportHost()
    {
        SplunkObservabilityClient client = new SplunkObservabilityClient(nodeInfo, httpClientDatapoint,
                new SplunkObservabilityClientConfig().setAuthToken("test").setIncludeHostTag(false), new ReportTagConfig()
                .setTags(ImmutableMap.of("foo", "ba:r", "baz", "quux")), objectMapper);
        client.report(TEST_TIME, collectedData);
        assertEquals(sentDataPointsJson.get("gauge").size(),2);

        for (Map<String, Object> map : sentDataPointsJson.get("gauge")) {
            assertEquals(map.keySet(), Set.of("metric", "timestamp", "value", "dimensions"));
            Map<String, String> dimensions = (Map<String, String>) map.get("dimensions");
            assertEquals(dimensions.get("foo"), "ba:r");
            assertEquals(dimensions.get("baz"), "quux");
        }
        Map<String, String> dimensions = (Map<String, String>) sentDataPointsJson.get("gauge").get(0).get("dimensions");
        assertEquals(dimensions.keySet(), Set.of("application", "environment", "pool", "foo", "baz", "dimension1"));
        dimensions = (Map<String, String>) sentDataPointsJson.get("gauge").get(1).get("dimensions");
        assertEquals(dimensions.keySet(), Set.of("application", "environment", "pool", "foo", "baz"));
    }

    private class TestingDatapointResponseFunction
        implements Processor
    {
        @Override
        public Response handle(Request input)
        {
            assertNull(sentDataPointsJson);
            assertEquals(input.getMethod(), "POST");
            assertEquals(input.getUri().toString(), "v2/datapoint");
            assertEquals(input.getHeader("Content-Type"), "application/json");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                writeBodySourceTo(input.getBodySource(), outputStream);
                BufferedInputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(outputStream.toByteArray()));

                sentDataPointsJson = new ObjectMapper().readValue(inputStream, new TypeReference<HashMap<String,List<Map<String, Object>>>>()
                {
                });
                sentDataPointsJson = new HashMap<>(sentDataPointsJson);
                sentDataPointsJson.get("gauge").sort(Comparator.comparing(o -> ((String) o.get("metric"))));

            } catch (Exception e) {
                throwIfUnchecked(e);
                throw new RuntimeException(e);
            }

            return mockResponse(HttpStatus.OK);
        }
    }

    private class TestingEventResponseFunction
            implements Processor
    {
        @Override
        public Response handle(Request input)
        {
            assertNull(sentEventsJson);
            assertEquals(input.getMethod(), "POST");
            assertEquals(input.getUri().toString(), "v2/event");
            assertEquals(input.getHeader("Content-Type"), "application/json");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                writeBodySourceTo(input.getBodySource(), outputStream);
                BufferedInputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(outputStream.toByteArray()));

                sentEventsJson = new ObjectMapper().readValue(inputStream, new TypeReference<List<Map<String, Object>>>()
                {
                });
                sentEventsJson = Lists.newArrayList(sentEventsJson);
                sentEventsJson.sort(Comparator.comparing(o -> ((String) o.get("eventType"))));

            } catch (Exception e) {
                throwIfUnchecked(e);
                throw new RuntimeException(e);
            }

            return mockResponse(HttpStatus.OK);
        }
    }
}
