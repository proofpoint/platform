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
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.testng.Assert.assertEquals;

public class TestJsonEventSerializer
{
    private static final Map<String, Object> EXPECTED_EVENT_JSON = ImmutableMap.<String, Object>builder()
            .put("type", "FixedDummy")
            .put("uuid", "8e248a16-da86-11e0-9e77-9fc96e21a396")
            .put("host", "localhost")
            .put("timestamp", "2011-09-09T01:35:28.333Z")
            .put("traceToken", "sample-trace-token")
            .put("data", ImmutableMap.of(
                    "intValue", 5678,
                    "stringValue", "foo"
            ))
           .build();

    @Test
    public void testEventSerializer()
            throws Exception
    {
        JsonEventSerializer eventSerializer = new JsonEventSerializer(new NodeInfo("test"), FixedDummyEventClass.class);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator jsonGenerator = new JsonFactory().createGenerator(out, JsonEncoding.UTF8);

        FixedDummyEventClass event = TestingUtils.getEvents().get(0);
        eventSerializer.serialize(event, "sample-trace-token", jsonGenerator);

        String json = out.toString(Charsets.UTF_8.name());
        assertEquals(new ObjectMapper().readValue(json, Object.class), EXPECTED_EVENT_JSON, "JSON encoding " + json);
    }

    @Test(expectedExceptions = InvalidEventException.class)
    public void testUnregisteredEventClass()
            throws Exception
    {
        JsonEventSerializer eventSerializer = new JsonEventSerializer(new NodeInfo("test"), DummyEventClass.class);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator jsonGenerator = new JsonFactory().createGenerator(out, JsonEncoding.UTF8);

        FixedDummyEventClass event = TestingUtils.getEvents().get(0);
        eventSerializer.serialize(event, "sample-trace-token", jsonGenerator);
    }
}
