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

import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.encoder.EncoderBase;
import com.proofpoint.log.Logging;
import com.proofpoint.units.Duration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.proofpoint.http.server.HttpRequestEvent.createHttpRequestEvent;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static java.util.concurrent.TimeUnit.SECONDS;

class DelimitedRequestLog
    implements RequestLog
{
    // Tab-separated
    // Time, ip, method, url, user, agent, response code, request length, response length, response time

    private static final DateTimeFormatter ISO_FORMATTER = ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    private final CurrentTimeMillisProvider currentTimeMillisProvider;
    private final Appender<HttpRequestEvent> appender;
    private final ClientAddressExtractor clientAddressExtractor;

    DelimitedRequestLog(HttpServerConfig config, CurrentTimeMillisProvider currentTimeMillisProvider, ClientAddressExtractor clientAddressExtractor)
    {
        this.currentTimeMillisProvider = currentTimeMillisProvider;
        this.clientAddressExtractor = clientAddressExtractor;

        appender = Logging.createFileAppender(
                config.getLogPath(),
                config.getLogMaxHistory(),
                config.getLogQueueSize(),
                new Duration(10, SECONDS),
                config.getLogMaxSegmentSize(),
                config.getLogMaxTotalSize(),
                new EventEncoder(),
                new ContextBase());
    }

    @Override
    public void log(
            Request request,
            Response response,
            long beginToDispatchMillis,
            long beginToEndMillis,
            long firstToLastContentTimeInMillis,
            DoubleSummaryStats responseContentInterarrivalStats)
    {
        HttpRequestEvent event = createHttpRequestEvent(
                request,
                response,
                currentTimeMillisProvider.getCurrentTimeMillis(),
                beginToDispatchMillis,
                beginToEndMillis,
                firstToLastContentTimeInMillis,
                responseContentInterarrivalStats,
                clientAddressExtractor
        );

        appender.doAppend(event);
    }

    @Override
    public void stop()
    {
        appender.stop();
    }

    private static class EventEncoder extends EncoderBase<HttpRequestEvent>
    {
        @Override
        public byte[] headerBytes()
        {
            return null;
        }

        @Override
        public byte[] encode(HttpRequestEvent event)
        {
            StringBuilder builder = new StringBuilder();
            builder.append(ISO_FORMATTER.format(event.getTimeStamp()))
                    .append('\t')
                    .append(event.getClientAddress())
                    .append('\t')
                    .append(event.getMethod())
                    .append('\t')
                    .append(event.getRequestUri()) // TODO: escape
                    .append('\t')
                    .append(event.getUser())
                    .append('\t')
                    .append(event.getAgent()) // TODO: escape
                    .append('\t')
                    .append(event.getResponseCode())
                    .append('\t')
                    .append(event.getRequestSize())
                    .append('\t')
                    .append(event.getResponseSize())
                    .append('\t')
                    .append(event.getTimeToLastByte())
                    .append('\t')
                    .append(event.getTraceToken())
                    .append('\n');
            return builder.toString().getBytes(UTF_8);
        }

        @Override
        public byte[] footerBytes()
        {
            return null;
        }
    }
}
