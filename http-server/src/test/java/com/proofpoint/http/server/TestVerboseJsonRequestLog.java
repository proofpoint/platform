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

import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentRequestToken;

public class TestVerboseJsonRequestLog extends AbstractTestRequestLog
{
    @Override
    protected void setup(HttpServerConfig httpServerConfig)
    {
        logger = new JsonVerboseRequestLog(httpServerConfig);
    }

    @Override
    @SuppressWarnings("deprecation")
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
        return String.format("{\"time\":\"%s\",\"traceToken\":\"%s\",\"sourceIp\":\"%s\"," +
                        "\"method\":\"%s\",\"requestUri\":\"%s\",\"username\":\"%s\",\"userAgent\":\"%s\"," +
                        "\"responseCode\":%d,\"requestSize\":%d,\"responseSize\":%d,\"protocolVersion\":\"%s\"," +
                        "\"tlsProtocolVersion\":\"%s\",\"tlsCipherSuite\":\"%s\"," +
                        "\"timeToDispatch\":\"%d.00ms\",\"timeToRequestEnd\":\"%d.00ms\"," +
                        "\"timeResponseContent\":\"%d.00ms\",\"responseContentChunk\":{\"count\":%d,\"max\":\"%d.00ms\"}," +
                        "\"timeToLastByte\":\"%d.00ms\"}\n",
                "2018-09-29T03:41:11.000Z",
                getCurrentRequestToken(),
                clientAddr,
                method,
                pathQuery,
                user,
                agent,
                responseCode,
                requestSize,
                responseSize,
                protocolVersion,
                tlsProtocolVersion,
                tlsCipherSuite,
                timeToDispatch,
                timeToRequestEnd,
                timeResponseContent,
                responseContentChunkCount,
                responseContentChunkMax,
                timeToLastByte);
    }
}
