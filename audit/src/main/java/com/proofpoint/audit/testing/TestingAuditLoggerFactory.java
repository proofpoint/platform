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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proofpoint.audit.AuditLogger;
import com.proofpoint.audit.AuditLoggerFactory;
import jakarta.inject.Inject;

import static java.util.Objects.requireNonNull;

public class TestingAuditLoggerFactory
        implements AuditLoggerFactory
{
    private final ObjectMapper objectMapper;
    private final TestingAuditLog auditLog;

    @Inject
    public TestingAuditLoggerFactory(ObjectMapper objectMapper, TestingAuditLog auditLog)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.auditLog = requireNonNull(auditLog, "auditLog is null");
    }

    @Override
    public <T> AuditLogger<T> create(Class<T> recordClass)
    {
        return new TestingAuditLogger<>(recordClass, objectMapper, auditLog);
    }
}
