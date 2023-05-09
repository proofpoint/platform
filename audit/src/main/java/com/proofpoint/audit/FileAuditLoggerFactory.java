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
package com.proofpoint.audit;

import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.encoder.EncoderBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proofpoint.audit.FileAuditLogger.AuditWrapper;
import com.proofpoint.log.Logger;
import com.proofpoint.log.Logging;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import static java.util.Objects.requireNonNull;

public class FileAuditLoggerFactory
        implements AuditLoggerFactory
{
    private static final Logger log = Logger.get(FileAuditLoggerFactory.class);

    private final DateTimeSupplier dateTimeSupplier;
    private final Appender<AuditWrapper> fileAppender;

    @Inject
    public FileAuditLoggerFactory(AuditConfiguration config, DateTimeSupplier dateTimeSupplier, ObjectMapper mapper)
    {
        this.dateTimeSupplier = requireNonNull(dateTimeSupplier, "dateTimeSupplier is null");
        String logPath = config.getLogPath();
        log.info("Audit logging to %s", logPath);
        fileAppender = Logging.createFileAppender(logPath, config.getMaxHistory(), config.getMaxSegmentSize(), config.getMaxTotalSize(), new JsonEncoder(mapper), new ContextBase());
    }

    @Override
    public <T> AuditLogger<T> create(Class<T> recordClass)
    {
        return new FileAuditLogger<>(recordClass, dateTimeSupplier, fileAppender);
    }

    @PreDestroy
    public void close()
    {
        try {
            fileAppender.stop();
        }
        catch (Exception ignored) {
            // catch any exception to assure logging always works
        }
    }

    static final class JsonEncoder
            extends EncoderBase<AuditWrapper>
    {
        private final ObjectMapper mapper;

        private JsonEncoder(ObjectMapper mapper)
        {
            this.mapper = requireNonNull(mapper, "mapper is null");
        }

        @Override
        public byte[] headerBytes()
        {
            return null;
        }

        @Override
        public byte[] encode(AuditWrapper event)
        {
            try {
                byte[] jsonBytes = mapper.writeValueAsBytes(event);
                byte[] line = new byte[jsonBytes.length + 1];
                System.arraycopy(jsonBytes, 0, line, 0, jsonBytes.length);
                line[jsonBytes.length] = '\n';
                return line;
            }
            catch (JsonProcessingException e) {
                throw new IllegalArgumentException(String.format("%s could not be converted to json", event.getClass().getName()), e);
            }
        }

        @Override
        public byte[] footerBytes()
        {
            return null;
        }
    }
}
