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
package com.proofpoint.audit.testing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.value.AutoValue;
import com.proofpoint.audit.AuditLogger;
import com.proofpoint.tracetoken.TraceToken;
import jakarta.annotation.Nullable;

import java.io.IOException;

import static com.proofpoint.audit.testing.TestingAuditLogger.AuditWrapper.auditWrapper;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static java.util.Objects.requireNonNull;

public class TestingAuditLogger<T>
        implements AuditLogger<T>
{
    private final String type;
    private final ObjectMapper objectMapper;
    private final TestingAuditLog auditLog;

    TestingAuditLogger(Class<T> recordClass, ObjectMapper objectMapper, TestingAuditLog auditLog)
    {
        type = recordClass.getCanonicalName();
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.auditLog = requireNonNull(auditLog, "auditLog is null");
    }

    @Override
    public void audit(T record)
    {
        Object value;
        try {
            String json = objectMapper.writeValueAsString(auditWrapper(type, record));
            value = objectMapper.readValue(json, Object.class);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        auditLog.add(value);
    }

    @AutoValue
    abstract static class AuditWrapper
    {
        // "time" is intentionally omitted

        @JsonProperty
        public abstract String getType();

        @Nullable
        @JsonProperty
        public abstract TraceToken getTraceToken();

        @JsonUnwrapped
        public abstract Object getObject();

        public static TestingAuditLogger.AuditWrapper auditWrapper(String type, Object object) {
            return new AutoValue_TestingAuditLogger_AuditWrapper(type, getCurrentTraceToken(), object);
        }
    }
}
