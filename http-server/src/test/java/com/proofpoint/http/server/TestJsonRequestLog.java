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
package com.proofpoint.http.server;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.json.JsonCodec;

import java.util.Map;

import static com.proofpoint.json.JsonCodec.mapJsonCodec;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;

public class TestJsonRequestLog extends AbstractTestRequestLog
{
    private static final JsonCodec<Map<String, String>> MAP_JSON_CODEC = mapJsonCodec(String.class, String.class).withoutPretty();

    @Override
    protected void setup(HttpServerConfig httpServerConfig)
    {
        logger = new JsonRequestLog(httpServerConfig);
    }

    @Override
    protected String getExpectedLogLine(
            long timestamp,
            String clientAddr,
            String method,
            String pathQuery,
            String user,
            String agent,
            int responseCode,
            long requestSize,
            long responseSize,
            String protocolVersion,
            String tlsProtocolVersion,
            String tlsCipherSuite,
            long timeToDispatch,
            long timeToRequestEnd,
            long timeResponseContent,
            long responseContentChunkCount, long responseContentChunkMax, long timeToLastByte)
    {
        return String.format("{\"t\":\"%s\",\"tt\":%s,\"ip\":\"%s\"," +
                        "\"m\":\"%s\",\"u\":\"%s\",\"user\":\"%s\"," +
                        "\"c\":%d,\"qs\":%d,\"rs\":%d," +
                        "\"td\":\"%d.00ms\",\"tq\":\"%d.00ms\"," +
                        "\"tr\":\"%d.00ms\",\"rc\":{\"count\":%d,\"max\":\"%d.00ms\"}," +
                        "\"tl\":\"%d.00ms\"}\n",
                "2018-09-29T03:41:11.000Z",
                MAP_JSON_CODEC.toJson(ImmutableMap.copyOf(getCurrentTraceToken())),
                clientAddr,
                method,
                pathQuery,
                user,
                responseCode,
                requestSize,
                responseSize,
                timeToDispatch,
                timeToRequestEnd,
                timeResponseContent,
                responseContentChunkCount,
                responseContentChunkMax,
                timeToLastByte);
    }
}
