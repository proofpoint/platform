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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.proofpoint.json.ObjectMapperProvider;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.tracetoken.TraceToken;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.UUID;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static java.util.Objects.requireNonNull;

class EventJsonSerializer<T>
        extends JsonSerializer<T>
{
    private static final Supplier<ObjectMapper> OBJECT_MAPPER_SUPPLIER = Suppliers.memoize(() -> new ObjectMapperProvider().get());

    private final String token;
    private final EventTypeMetadata<T> eventTypeMetadata;
    private final String hostName;

    EventJsonSerializer(NodeInfo nodeInfo, @Nullable TraceToken token, EventTypeMetadata<T> eventTypeMetadata)
    {
        if (token == null || eventTypeMetadata.getTraceTokenField() != null) {
            this.token = null;
        }
        else if (token.size() == 1) {
            this.token = token.get("id");
        }
        else {
            try {
                this.token = OBJECT_MAPPER_SUPPLIER.get().writeValueAsString(token);
            }
            catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        this.eventTypeMetadata = requireNonNull(eventTypeMetadata, "eventTypeMetadata is null");
        if (eventTypeMetadata.getHostField() == null) {
            hostName = nodeInfo.getInternalHostname();
        }
        else {
            hostName = null;
        }
    }

    @Override
    public Class<T> handledType()
    {
        return eventTypeMetadata.getEventClass();
    }

    @Override
    public void serialize(T event, JsonGenerator jsonGenerator, SerializerProvider provider)
            throws IOException
    {
        jsonGenerator.writeStartObject();

        jsonGenerator.writeStringField("type", eventTypeMetadata.getTypeName());

        if (eventTypeMetadata.getUuidField() != null) {
            eventTypeMetadata.getUuidField().writeField(jsonGenerator, event);
        }
        else {
            jsonGenerator.writeStringField("uuid", UUID.randomUUID().toString());
        }

        if (eventTypeMetadata.getHostField() != null) {
            eventTypeMetadata.getHostField().writeField(jsonGenerator, event);
        }
        else {
            jsonGenerator.writeStringField("host", hostName);
        }

        if (eventTypeMetadata.getTimestampField() != null) {
            eventTypeMetadata.getTimestampField().writeField(jsonGenerator, event);
        }
        else {
            jsonGenerator.writeFieldName("timestamp");
            EventDataType.DATETIME.writeFieldValue(jsonGenerator, new DateTime());
        }

        if (eventTypeMetadata.getTraceTokenField() != null) {
            eventTypeMetadata.getTraceTokenField().writeField(jsonGenerator, event);
        }
        else if (token != null) {
            jsonGenerator.writeFieldName("traceToken");
            EventDataType.STRING.writeFieldValue(jsonGenerator, token);
        }

        jsonGenerator.writeObjectFieldStart("data");
        for (EventFieldMetadata field : eventTypeMetadata.getFields()) {
            field.writeField(jsonGenerator, event);
        }
        jsonGenerator.writeEndObject();

        jsonGenerator.writeEndObject();
        jsonGenerator.flush();
    }
}
