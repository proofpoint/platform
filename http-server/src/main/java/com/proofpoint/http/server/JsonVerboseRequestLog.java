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
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logging;
import com.proofpoint.units.Duration;

import static java.util.concurrent.TimeUnit.SECONDS;

class JsonVerboseRequestLog
        implements RequestLog
{
    private final Appender<VerboseHttpRequestEvent> appender;

    JsonVerboseRequestLog(HttpServerConfig config)
    {
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
    public void log(HttpRequestEvent event)
    {
        appender.doAppend(new VerboseHttpRequestEvent(event));
    }

    @Override
    public void stop()
    {
        appender.stop();
    }

    private static class EventEncoder extends EncoderBase<VerboseHttpRequestEvent>
    {
        private final JsonCodec<VerboseHttpRequestEvent> codec = JsonCodec.jsonCodec(VerboseHttpRequestEvent.class).withoutPretty();

        @Override
        public byte[] headerBytes()
        {
            return null;
        }

        @Override
        public byte[] encode(VerboseHttpRequestEvent event)
        {
            byte[] jsonBytes = codec.toJsonBytes(event);
            byte[] line = new byte[jsonBytes.length + 1];
            System.arraycopy(jsonBytes, 0, line, 0, jsonBytes.length);
            line[jsonBytes.length] = '\n';
            return line;
        }

        @Override
        public byte[] footerBytes()
        {
            return null;
        }
    }
}
