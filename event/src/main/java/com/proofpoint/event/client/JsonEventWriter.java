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
import com.fasterxml.jackson.databind.JsonSerializer;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.http.client.DynamicBodySource.Writer;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.tracetoken.TraceToken;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

class JsonEventWriter
{
    private final NodeInfo nodeInfo;
    private final JsonFactory jsonFactory;
    private final Map<Class<?>, EventTypeMetadata<?>> metadataMap;

    @Inject
    JsonEventWriter(NodeInfo nodeInfo, Set<EventTypeMetadata<?>> eventTypes)
    {
        this.nodeInfo = requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(eventTypes, "eventTypes is null");

        this.jsonFactory = new JsonFactory();

        ImmutableMap.Builder<Class<?>, EventTypeMetadata<?>> metadataBuilder = ImmutableMap.builder();

        for (EventTypeMetadata<?> eventType : eventTypes) {
            metadataBuilder.put(eventType.getEventClass(), eventType);
        }
        this.metadataMap = metadataBuilder.build();
    }

    <T> Writer createEventWriter(final Iterator<T> eventIterator, final TraceToken token, final OutputStream out)
            throws IOException
    {
        requireNonNull(eventIterator, "eventIterator is null");
        requireNonNull(out, "out is null");

        JsonGenerator jsonGenerator = jsonFactory.createGenerator(out, JsonEncoding.UTF8);

        jsonGenerator.writeStartArray();

        return () -> {
            if (eventIterator.hasNext()) {
                T event = eventIterator.next();
                JsonSerializer<T> serializer = getSerializer(event, token);
                if (serializer == null) {
                    throw new InvalidEventException("Event class [%s] has not been registered as an event", event.getClass().getName());
                }

                serializer.serialize(event, jsonGenerator, null);
            }
            else {
                jsonGenerator.writeEndArray();
                jsonGenerator.flush();
                out.close();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> JsonSerializer<T> getSerializer(T event, @Nullable TraceToken token)
    {
        Class<?> eventClass = event.getClass();
        EventTypeMetadata<T> metadata = (EventTypeMetadata<T>) metadataMap.get(eventClass);
        while (metadata == null) {
            if (eventClass.getAnnotation(EventType.class) != null) {
                return null;
            }
            eventClass = eventClass.getSuperclass();
            if (eventClass == null) {
                return null;
            }
            metadata = (EventTypeMetadata<T>) metadataMap.get(eventClass);
        }
        return new EventJsonSerializer<>(nodeInfo, token, metadata);
    }
}
