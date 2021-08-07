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
package com.proofpoint.event.client;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.tracetoken.TraceToken;
import com.proofpoint.tracetoken.TraceTokenManager;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.proofpoint.tracetoken.TraceTokenManager.addTraceTokenProperties;
import static com.proofpoint.tracetoken.TraceTokenManager.clearRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerRequestToken;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;

public class TestJsonEventSerializer
{
    private Map<String, Object> expectedEventJson;

    @BeforeMethod
    public void setup()
    {
        expectedEventJson = new LinkedHashMap<>();
        expectedEventJson.put("type", "FixedDummy");
        expectedEventJson.put("uuid", "8e248a16-da86-11e0-9e77-9fc96e21a396");
        expectedEventJson.put("host", "localhost");
        expectedEventJson.put("timestamp", "2011-09-09T01:35:28.333Z");
        expectedEventJson.put("traceToken", "sample-trace-token");
        expectedEventJson.put("data", ImmutableMap.of(
                "intValue", 5678,
                "stringValue", "foo"
        ));
    }

    @Test
    public void testEventSerializer()
            throws Exception
    {
        registerRequestToken("sample-trace-token");
        addTraceTokenProperties("key", "value");
        TraceToken traceToken = TraceTokenManager.getCurrentTraceToken();
        clearRequestToken();

        expectedEventJson.put("traceToken", "{\"id\":\"sample-trace-token\",\"key\":\"value\"}");

        JsonEventSerializer eventSerializer = new JsonEventSerializer(new NodeInfo("test"), FixedDummyEventClass.class);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator jsonGenerator = new JsonFactory().createGenerator(out, JsonEncoding.UTF8);

        FixedDummyEventClass event = TestingUtils.getEvents().get(0);
        eventSerializer.serialize(event, traceToken, jsonGenerator);

        String json = out.toString(UTF_8);
        assertEquals(new ObjectMapper().readValue(json, Object.class), expectedEventJson, "JSON encoding " + json);
    }

    @Test
    public void testEventSerializerNullToken()
            throws Exception
    {
        expectedEventJson.remove("traceToken");

        JsonEventSerializer eventSerializer = new JsonEventSerializer(new NodeInfo("test"), FixedDummyEventClass.class);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator jsonGenerator = new JsonFactory().createGenerator(out, JsonEncoding.UTF8);

        FixedDummyEventClass event = TestingUtils.getEvents().get(0);
        eventSerializer.serialize(event, null, jsonGenerator);

        String json = out.toString(UTF_8);
        assertEquals(new ObjectMapper().readValue(json, Object.class), expectedEventJson, "JSON encoding " + json);
    }

    @Test(expectedExceptions = InvalidEventException.class)
    public void testUnregisteredEventClass()
            throws Exception
    {
        JsonEventSerializer eventSerializer = new JsonEventSerializer(new NodeInfo("test"), DummyEventClass.class);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator jsonGenerator = new JsonFactory().createGenerator(out, JsonEncoding.UTF8);

        FixedDummyEventClass event = TestingUtils.getEvents().get(0);
        eventSerializer.serialize(event, null, jsonGenerator);
    }
}
