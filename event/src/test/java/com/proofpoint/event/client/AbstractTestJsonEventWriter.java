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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.tracetoken.TraceToken;
import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.google.common.io.ByteStreams.nullOutputStream;
import static com.proofpoint.event.client.ChainedCircularEventClass.ChainedPart;
import static com.proofpoint.event.client.ChainedCircularEventClass.chainedCircularEventClass;
import static com.proofpoint.event.client.DummyEventClass.dummyEventClass;
import static com.proofpoint.event.client.EventTypeMetadata.getValidEventTypeMetaDataSet;
import static com.proofpoint.event.client.NestedDummyEventClass.NestedPart.nestedPart;
import static com.proofpoint.tracetoken.TraceTokenManager.addTraceTokenProperties;
import static com.proofpoint.tracetoken.TraceTokenManager.clearRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerRequestToken;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;

public abstract class AbstractTestJsonEventWriter
{
    protected JsonEventWriter eventWriter;

    abstract <T> void writeEvents(final Iterable<T> events, TraceToken token, OutputStream out)
            throws Exception;

    @BeforeMethod
    public void setup()
    {
        Set<EventTypeMetadata<?>> eventTypes = getValidEventTypeMetaDataSet(
                FixedDummyEventClass.class, FixedTokenEventClass.class, FixedStringTokenEventClass.class, NestedDummyEventClass.class, CircularEventClass.class, ChainedCircularEventClass.class);
        eventWriter = new JsonEventWriter(new NodeInfo("test"), eventTypes);
    }

    @Test
    public void testEventWriter()
            throws Exception
    {
        registerRequestToken("sample-trace-token");
        TraceToken traceToken = getCurrentTraceToken();
        clearRequestToken();

        assertEventJson(TestingUtils.getEvents(), traceToken, TestingUtils.getExpectedJson());
    }

    @Test
    public void testNullValue()
            throws Exception
    {
        List<Map<String, Object>> expected = List.of(ImmutableMap.<String, Object>builder()
                .put("type", "FixedDummy")
                .put("uuid", "1ea8ca34-db36-11e0-b76f-8b7d505ab1ad")
                .put("host", "localhost")
                .put("timestamp", "2011-09-09T01:59:59.999Z")
                .put("traceToken", "sample-trace-token")
                .put("data", ImmutableMap.of("intValue", 123))
                .build()
        );

        registerRequestToken("sample-trace-token");
        TraceToken traceToken = getCurrentTraceToken();
        clearRequestToken();

        FixedDummyEventClass event = new FixedDummyEventClass(
                "localhost", new DateTime("2011-09-09T01:59:59.999Z"), UUID.fromString("1ea8ca34-db36-11e0-b76f-8b7d505ab1ad"), 123, null);

        assertEventJson(List.of(event), traceToken, expected);
    }

    @Test
    public void testMapToken()
            throws Exception
    {
        List<Map<String, Object>> expected = TestingUtils.getExpectedJson();
        for (Map<String, Object> map : expected) {
            map.put("traceToken", "{\"id\":\"sample-trace-token\",\"key\":\"value\"}");
        }

        registerRequestToken("sample-trace-token");
        addTraceTokenProperties("key", "value", "_local", "shouldIgnore");
        TraceToken traceToken = getCurrentTraceToken();
        clearRequestToken();

        assertEventJson(TestingUtils.getEvents(), traceToken, expected);
    }

    @Test
    public void testNullToken()
            throws Exception
    {
        List<Map<String, Object>> expected = TestingUtils.getExpectedJson();
        for (Map<String, Object> map : expected) {
            map.remove("traceToken");
        }
        assertEventJson(TestingUtils.getEvents(), null, expected);
    }

    @Test
    public void testFixedToken()
            throws Exception
    {
        registerRequestToken("other-trace-token");
        addTraceTokenProperties("key", "value");
        TraceToken traceToken = getCurrentTraceToken();
        registerRequestToken("sample-trace-token");
        TraceToken callerTraceToken = getCurrentTraceToken();
        clearRequestToken();

        FixedTokenEventClass event = new FixedTokenEventClass(
                "localhost",
                new DateTime("2011-09-09T01:35:28.333Z"),
                UUID.fromString("8e248a16-da86-11e0-9e77-9fc96e21a396"),
                traceToken,
                5678,
                "foo");
        List<Map<String, Object>> expected1 = new ArrayList<>();
        expected1.add(new LinkedHashMap<>(ImmutableMap.<String, Object>builder()
                .put("type", "FixedDummy")
                .put("uuid", "8e248a16-da86-11e0-9e77-9fc96e21a396")
                .put("host", "localhost")
                .put("timestamp", "2011-09-09T01:35:28.333Z")
                .put("traceToken", ImmutableMap.of(
                                                    "id", "other-trace-token",
                                                    "key", "value"
                                            ))
                .put("data", ImmutableMap.of("intValue", 5678, "stringValue", "foo"))
                .build()
        ));

        assertEventJson(List.of(event), callerTraceToken, expected1);
    }

    @Test
    public void testFixedStringToken()
            throws Exception
    {
        registerRequestToken("sample-trace-token");
        TraceToken traceToken = getCurrentTraceToken();
        clearRequestToken();

        FixedStringTokenEventClass event = new FixedStringTokenEventClass(
                "localhost",
                new DateTime("2011-09-09T01:35:28.333Z"),
                UUID.fromString("8e248a16-da86-11e0-9e77-9fc96e21a396"),
                "other-trace-token",
                5678,
                "foo");
        List<Map<String, Object>> expected = new ArrayList<>();
        expected.add(new LinkedHashMap<>(ImmutableMap.<String, Object>builder()
                .put("type", "FixedDummy")
                .put("uuid", "8e248a16-da86-11e0-9e77-9fc96e21a396")
                .put("host", "localhost")
                .put("timestamp", "2011-09-09T01:35:28.333Z")
                .put("traceToken", "other-trace-token")
                .put("data", ImmutableMap.of("intValue", 5678, "stringValue", "foo"))
                .build()
        ));

        assertEventJson(List.of(event), traceToken, expected);
    }

    @Test
    public void testNestedEvent()
            throws Exception
    {
        List<Map<String, Object>> expected = List.of(ImmutableMap.<String, Object>builder()
                .put("type", "NestedDummy")
                .put("uuid", "6b598c2a-0a95-4f3f-9298-5a4d70ca13fc")
                .put("host", "localhost")
                .put("timestamp", "2011-09-09T01:48:08.888Z")
                .put("traceToken", "sample-trace-token")
                .put("data", ImmutableMap.<String, Object>builder()
                        .put("intValue", 9999)
                        .put("namedParts", ImmutableMap.<String, Object>of(
                                "listFirst", ImmutableMap.of(
                                        "name", "listFirst",
                                        "part", ImmutableMap.of("name", "listSecond")),
                                "listThird", ImmutableMap.of("name", "listThird")))
                        .put("namedStringList", ImmutableMap.of(
                                "abc", List.of("abc", "abc", "abc"),
                                "xyz", List.of("xyz", "xyz", "xyz")))
                        .put("namedStrings", ImmutableMap.of(
                                "abc", "abc",
                                "xyz", "xyz"))
                        .put("nestedPart", ImmutableMap.of(
                                "name", "first",
                                "part", ImmutableMap.of(
                                        "name", "second",
                                        "part", ImmutableMap.of("name", "third"))))
                        .put("nestedParts", List.of(
                                ImmutableMap.of(
                                         "name", "listFirst",
                                         "part", ImmutableMap.of("name", "listSecond")),
                                ImmutableMap.of("name", "listThird")))
                        .put("stringValue", "nested")
                        .put("strings", List.of("abc", "xyz"))
                        .build())
                .build()
        );

        registerRequestToken("sample-trace-token");
        TraceToken traceToken = getCurrentTraceToken();
        clearRequestToken();

        NestedDummyEventClass nestedEvent = new NestedDummyEventClass(
                "localhost", new DateTime("2011-09-09T01:48:08.888Z"), UUID.fromString("6b598c2a-0a95-4f3f-9298-5a4d70ca13fc"), 9999, "nested",
                List.of("abc", "xyz"),
                nestedPart("first", nestedPart("second", nestedPart("third", null))),
                List.of(nestedPart("listFirst", nestedPart("listSecond", null)), nestedPart("listThird", null))
        );

        assertEventJson(List.of(nestedEvent), traceToken, expected);
    }

    @Test(expectedExceptions = InvalidEventException.class, expectedExceptionsMessageRegExp = "Cycle detected in event data:.*")
    public void testCircularEvent()
            throws Exception
    {
        writeEvents(List.of(new CircularEventClass()), null, nullOutputStream());
    }

    @Test(expectedExceptions = InvalidEventException.class, expectedExceptionsMessageRegExp = "Cycle detected in event data:.*")
    public void testChainedCircularEvent()
            throws Exception
    {
        ChainedPart a = new ChainedPart("a");
        ChainedPart b = new ChainedPart("b");
        ChainedPart c = new ChainedPart("c");
        a.setPart(b);
        b.setPart(c);
        c.setPart(a);

        ChainedCircularEventClass event = chainedCircularEventClass(a);

        writeEvents(List.of(event), null, nullOutputStream());
    }

    @Test(expectedExceptions = InvalidEventException.class)
    public void testUnregisteredEventClass()
            throws Exception
    {
        DummyEventClass event = dummyEventClass(1.1, 1, "foo", false);
        writeEvents(List.of(event), null, nullOutputStream());
    }

    @Test(expectedExceptions = InvalidEventException.class)
    public void testUnregisteredEventSubclassOfRegisteredClass()
            throws Exception
    {
        FixedDummyEventClass event = new SubclassOfFixedDummyEventClass();
        writeEvents(List.of(event), null, nullOutputStream());
    }

    @Test(expectedExceptions = InvalidEventException.class)
    public void testNotAnEventClass()
            throws Exception
    {
        writeEvents(List.of("foo"), null, nullOutputStream());
    }

    private <T> void assertEventJson(Iterable<T> events, TraceToken token, List<Map<String, Object>> expected)
            throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writeEvents(events, token, out);

        String json = out.toString(UTF_8.name());
        assertEquals(new ObjectMapper().readValue(json, Object.class), expected, "JSON encoding " + json);
    }

    @EventType("Subclass")
    private static class SubclassOfFixedDummyEventClass
            extends FixedDummyEventClass
    {
        public SubclassOfFixedDummyEventClass()
        {
            super("localhost", new DateTime("2011-09-09T01:59:59.999Z"), UUID.fromString("1ea8ca34-db36-11e0-b76f-8b7d505ab1ad"), 123, null);
        }
    }
}
