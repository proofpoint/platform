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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentRequestToken;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

public class TestDelimitedRequestLog
        extends AbstractTestRequestLog
{
    private static final DateTimeFormatter ISO_FORMATTER = ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    @Override
    protected void setup(HttpServerConfig httpServerConfig)
    {
        logger = new DelimitedRequestLog(httpServerConfig, currentTimeMillisProvider, clientAddressExtractor);
    }

    @Override
    protected String getExpectedLogLine(long timestamp, String clientAddr, String method, String pathQuery, String user, String agent, int responseCode, long requestSize, long responseSize, long timeToLastByte)
    {
        return String.format("%s\t%s\t%s\t%s\t%s\t%s\t%d\t%d\t%d\t%d\t%s\n",
                ISO_FORMATTER.format(Instant.ofEpochMilli(timestamp)),
                clientAddr,
                method,
                pathQuery,
                user,
                agent,
                responseCode,
                requestSize,
                responseSize,
                timeToLastByte,
                getCurrentRequestToken());
    }
}
