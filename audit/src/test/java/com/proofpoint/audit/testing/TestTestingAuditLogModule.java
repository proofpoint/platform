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
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.proofpoint.audit.AuditLogger;
import com.proofpoint.json.JsonModule;
import com.proofpoint.tracetoken.TraceTokenManager;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static com.proofpoint.audit.AuditLoggerBinder.auditLoggerBinder;
import static java.util.Objects.requireNonNull;
import static org.testng.Assert.assertEquals;

public class TestTestingAuditLogModule
{
    private AuditLogger<TestingRecord> logger;
    private TestingAuditLog auditLog;
    private final TestingRecord record1 = new TestingRecord("record1");
    private final TestingRecord record2 = new TestingRecord("record2");
    private final TestingRecord record3 = new TestingRecord("record3");

    @BeforeMethod
    public void setup()
    {
        TraceTokenManager.clearRequestToken();
        Injector injector = Guice.createInjector(
                new TestingAuditLogModule(),
                new JsonModule(),
                binder -> auditLoggerBinder(binder).bind(TestingRecord.class)
        );
        logger = injector.getInstance(Key.get(new TypeLiteral<AuditLogger<TestingRecord>>()
        {
        }));
        auditLog = injector.getInstance(TestingAuditLog.class);
    }

    @Test
    public void testPostSingle()
    {
        logger.audit(record1);

        assertEquals(auditLog.getRecords(), List.of(ImmutableMap.of(
                "type", "com.proofpoint.audit.testing.TestTestingAuditLogModule.TestingRecord",
                "value", "record1"))
        );
    }

    @Test
    public void testPostMultiple()
    {
        logger.audit(record1);
        TraceTokenManager.registerRequestToken("token1");
        logger.audit(record2);
        TraceTokenManager.clearRequestToken();
        logger.audit(record3);

        assertEquals(auditLog.getRecords(), List.of(
                ImmutableMap.of("type", "com.proofpoint.audit.testing.TestTestingAuditLogModule.TestingRecord",
                        "value", "record1"),
                ImmutableMap.of("type", "com.proofpoint.audit.testing.TestTestingAuditLogModule.TestingRecord",
                        "traceToken", ImmutableMap.of("id", "token1"),
                        "value", "record2"),
                ImmutableMap.of("type", "com.proofpoint.audit.testing.TestTestingAuditLogModule.TestingRecord",
                        "value", "record3")
        ));
    }

    record TestingRecord(@JsonProperty String value)
    {
        TestingRecord
        {
            requireNonNull(value, "value is null");
        }

    }
}
