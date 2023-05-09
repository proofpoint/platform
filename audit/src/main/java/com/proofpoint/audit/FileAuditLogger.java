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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.auto.value.AutoValue;
import com.proofpoint.tracetoken.TraceToken;
import jakarta.annotation.Nullable;

import java.time.Instant;

import static com.proofpoint.audit.FileAuditLogger.AuditWrapper.auditWrapper;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static java.util.Objects.requireNonNull;

final class FileAuditLogger<T> implements AuditLogger<T>
{
    private final String type;
    private final DateTimeSupplier dateTimeSupplier;
    private final Appender<AuditWrapper> fileAppender;

    FileAuditLogger(Class<T> recordClass, DateTimeSupplier dateTimeSupplier, Appender<AuditWrapper> fileAppender)
    {
        type = recordClass.getCanonicalName();
        this.dateTimeSupplier = requireNonNull(dateTimeSupplier, "dateTimeSupplier is null");
        this.fileAppender = requireNonNull(fileAppender, "fileAppender is null");
    }

    @Override
    public void audit(T record)
    {
        try {
            fileAppender.doAppend(auditWrapper(dateTimeSupplier.get(), type, record));
        }
        catch (Exception ignored) {
            // catch any exception to assure logging always works
        }
    }

    @JsonPropertyOrder({"time", "type", "traceToken", "object"})
    @AutoValue
    abstract static class AuditWrapper
    {
        @JsonProperty
        public abstract Instant getTime();

        @JsonProperty
        public abstract String getType();

        @Nullable
        @JsonProperty
        public abstract TraceToken getTraceToken();

        @JsonUnwrapped
        public abstract Object getObject();

        public static AuditWrapper auditWrapper(Instant time, String type, Object object) {
            return new AutoValue_FileAuditLogger_AuditWrapper(time, type, getCurrentTraceToken(), object);
        }
    }
}
