/*
 * Copyright 2017 Proofpoint, Inc.
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

import com.proofpoint.event.client.EventField.EventFieldMapping;
import com.proofpoint.tracetoken.TraceToken;
import org.joda.time.DateTime;

import java.util.UUID;

@EventType("FixedDummy")
public class FixedTokenEventClass
{
    private final String host;
    private final DateTime timestamp;
    private final UUID uuid;
    private final TraceToken traceToken;
    private int intValue;
    private final String stringValue;

    public FixedTokenEventClass(String host, DateTime timestamp, UUID uuid, TraceToken traceToken, int intValue, String stringValue)
    {
        this.host = host;
        this.timestamp = timestamp;
        this.uuid = uuid;
        this.intValue = intValue;
        this.traceToken = traceToken;
        this.stringValue = stringValue;
    }

    @EventField(fieldMapping = EventFieldMapping.HOST)
    public String getHost()
    {
        return host;
    }

    @EventField(fieldMapping = EventFieldMapping.TIMESTAMP)
    public DateTime getTimestamp()
    {
        return timestamp;
    }

    @EventField(fieldMapping = EventFieldMapping.UUID)
    public UUID getUuid()
    {
        return uuid;
    }

    @EventField(fieldMapping = EventFieldMapping.TRACE_TOKEN)
    public TraceToken getTraceToken()
    {
        return traceToken;
    }

    @EventField
    public int getIntValue()
    {
        return intValue;
    }

    @EventField
    public String getStringValue()
    {
        return stringValue;
    }

    @EventField
    public String getNullString()
    {
        return null;
    }
}
