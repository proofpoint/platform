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
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.common.io.BaseEncoding;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.tracetoken.TraceToken;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.io.IOException;
import java.security.SecureRandom;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static java.util.Objects.requireNonNull;

class EventJsonSerializer<T>
        extends JsonSerializer<T>
{
    private static final JsonCodec<TraceToken> TRACE_TOKEN_JSON_CODEC = jsonCodec(TraceToken.class).withoutPretty();
    private static final ThreadLocal<SecureRandom> SECURE_RANDOM = ThreadLocal.withInitial(SecureRandom::new);
    private static final BaseEncoding BASE_16 = BaseEncoding.base16();

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
            this.token = TRACE_TOKEN_JSON_CODEC.toJson(token);
        }

        this.eventTypeMetadata = requireNonNull(eventTypeMetadata, "eventTypeMetadata is null");
        if (eventTypeMetadata.getHostField() == null) {
            hostName = nodeInfo.getInternalHostname();
        }
        else {
            hostName = null;
        }
    }

    // Protect against finalizer attacks, as constructor can throw exception.
    @SuppressWarnings("deprecation")
    @Override
    protected final void finalize()
    {
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
            jsonGenerator.writeStringField("uuid", randomUUID());
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

    private static String randomUUID()
    {
        byte[] randomBytes = new byte[16];
        SECURE_RANDOM.get().nextBytes(randomBytes);
        randomBytes[6]  &= 0x0f;  /* clear version        */
        randomBytes[6]  |= 0x40;  /* set to version 4     */
        randomBytes[8]  &= 0x3f;  /* clear variant        */
        randomBytes[8]  |= 0x80;  /* set to IETF variant  */
        return BASE_16.encode(randomBytes,0,4) + "-" +
                BASE_16.encode(randomBytes, 4, 2) + "-" +
                BASE_16.encode(randomBytes, 6, 2) + "-" +
                BASE_16.encode(randomBytes, 8, 2) + "-" +
                BASE_16.encode(randomBytes, 10, 6);
    }
}
