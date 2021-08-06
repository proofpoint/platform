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

import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class TestingUtils
{
    private TestingUtils()
    {
    }

    public static List<FixedDummyEventClass> getEvents()
    {
        return List.of(
                new FixedDummyEventClass("localhost", new DateTime("2011-09-09T01:35:28.333Z"), UUID.fromString("8e248a16-da86-11e0-9e77-9fc96e21a396"), 5678, "foo"),
                new FixedDummyEventClass("localhost", new DateTime("2011-09-09T01:43:18.123Z"), UUID.fromString("94ac328a-da86-11e0-afe9-d30a5b7c4f68"), 1, "bar"),
                new FixedDummyEventClass("localhost", new DateTime("2011-09-09T01:45:55.555Z"), UUID.fromString("a30671a6-da86-11e0-bc43-971987242263"), 1234, "hello")
        );
    }

    public static List<Map<String, Object>> getExpectedJson()
    {
        List<Map<String, Object>> expected = new ArrayList<>();
        expected.add(new LinkedHashMap<>(ImmutableMap.<String, Object>builder()
                .put("type", "FixedDummy")
                .put("uuid", "8e248a16-da86-11e0-9e77-9fc96e21a396")
                .put("host", "localhost")
                .put("timestamp", "2011-09-09T01:35:28.333Z")
                .put("traceToken", "sample-trace-token")
                .put("data", ImmutableMap.of("intValue", 5678, "stringValue", "foo"))
                .build()
        ));
        expected.add(new LinkedHashMap<>(ImmutableMap.<String, Object>builder()
                .put("type", "FixedDummy")
                .put("uuid", "94ac328a-da86-11e0-afe9-d30a5b7c4f68")
                .put("host", "localhost")
                .put("timestamp", "2011-09-09T01:43:18.123Z")
                .put("traceToken", "sample-trace-token")
                .put("data", ImmutableMap.of("intValue", 1, "stringValue", "bar"))
                .build()
        ));
        expected.add(new LinkedHashMap<>(ImmutableMap.<String, Object>builder()
                .put("type", "FixedDummy")
                .put("uuid", "a30671a6-da86-11e0-bc43-971987242263")
                .put("host", "localhost")
                .put("timestamp", "2011-09-09T01:45:55.555Z")
                .put("traceToken", "sample-trace-token")
                .put("data", ImmutableMap.of("intValue", 1234, "stringValue", "hello"))
                .build()
        ));

        return expected;
    }
}
