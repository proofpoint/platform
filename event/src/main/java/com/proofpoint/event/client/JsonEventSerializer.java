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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.tracetoken.TraceToken;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static com.proofpoint.event.client.EventTypeMetadata.getValidEventTypeMetaDataSet;
import static java.util.Objects.requireNonNull;

public class JsonEventSerializer
{
    private final NodeInfo nodeinfo;
    private final Map<Class<?>, EventTypeMetadata<?>> metadataMap;

    @Inject
    public JsonEventSerializer(NodeInfo nodeInfo, Set<EventTypeMetadata<?>> eventTypes)
    {
        this.nodeinfo = requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(eventTypes, "eventTypes is null");

        ImmutableMap.Builder<Class<?>, EventTypeMetadata<?>> map = ImmutableMap.builder();
        for (EventTypeMetadata<?> eventType : eventTypes) {
            map.put(eventType.getEventClass(), eventType);
        }
        this.metadataMap = map.build();
    }

    public JsonEventSerializer(NodeInfo nodeInfo, Class<?>... eventClasses)
    {
        this(nodeInfo, getValidEventTypeMetaDataSet(eventClasses));
    }

    public <T> void serialize(T event, @Nullable TraceToken token, JsonGenerator jsonGenerator)
            throws IOException
    {
        requireNonNull(event, "event is null");
        requireNonNull(jsonGenerator, "jsonGenerator is null");

        JsonSerializer<T> serializer = getSerializer(event, token);
        if (serializer == null) {
            throw new InvalidEventException("Event class [%s] has not been registered as an event", event.getClass().getName());
        }

        serializer.serialize(event, jsonGenerator, null);
    }

    @SuppressWarnings("unchecked")
    private <T> JsonSerializer<T> getSerializer(T event, @Nullable TraceToken token)
    {
        EventTypeMetadata<T> metadata = (EventTypeMetadata<T>) metadataMap.get(event.getClass());
        if (metadata == null) {
            return null;
        }
        return new EventJsonSerializer<>(nodeinfo, token, metadata);
    }
}
