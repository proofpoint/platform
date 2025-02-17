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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.io.Files;
import com.proofpoint.json.ObjectMapperProvider;
import com.proofpoint.tracetoken.TraceTokenManager;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.DataSize.Unit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static java.util.Objects.requireNonNull;
import static org.testng.Assert.assertEquals;

public class TestFileAuditLogger
{
    private File file;
    private TestingDateTimeSupplier dateTimeSupplier;
    private FileAuditLoggerFactory factory;
    private AuditLogger<TestingRecord> handler;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        file = File.createTempFile(getClass().getName(), ".log");
        AuditConfiguration auditConfiguration = new AuditConfiguration()
                .setLogPath(file.getAbsolutePath())
                .setMaxHistory(1)
                .setMaxSegmentSize(new DataSize(1, Unit.MEGABYTE))
                .setMaxTotalSize(new DataSize(1, Unit.GIGABYTE));
        dateTimeSupplier = new TestingDateTimeSupplier();
        factory = new FileAuditLoggerFactory(auditConfiguration, dateTimeSupplier, new ObjectMapperProvider().get());
        handler = factory.create(TestingRecord.class);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
            throws IOException
    {
        factory.close();
        if (!file.delete()) {
            throw new IOException("Error deleting " + file.getAbsolutePath());
        }
    }

    @Test
    public void testLog()
            throws IOException
    {
        TraceTokenManager.clearRequestToken();
        handler.audit(new TestingRecord("foovalue"));
        factory.close();
        String actual = Files.asCharSource(file, UTF_8).read();
        assertEquals(actual, "{\"time\":\"" + ISO_INSTANT.format(dateTimeSupplier.get()) + "\"," +
                "\"type\":\"com.proofpoint.audit.TestFileAuditLogger.TestingRecord\",\"foo\":\"foovalue\"}\n");
    }

    @Test
    public void testTraceToken()
            throws IOException
    {
        try {
            TraceTokenManager.createAndRegisterNewRequestToken("property", "value");
            handler.audit(new TestingRecord("foovalue"));
            factory.close();
            String actual = Files.asCharSource(file, UTF_8).read();
            assertEquals(actual, "{\"time\":\"" + ISO_INSTANT.format(dateTimeSupplier.get()) + "\"," +
                    "\"type\":\"com.proofpoint.audit.TestFileAuditLogger.TestingRecord\"," +
                    "\"traceToken\":{\"id\":\"" + TraceTokenManager.getCurrentTraceToken().get("id") + "\",\"property\":\"value\"}," +
                    "\"foo\":\"foovalue\"}\n");
        }
        finally {
            TraceTokenManager.clearRequestToken();
        }
    }

    record TestingRecord(@JsonProperty String foo)
    {
        TestingRecord
        {
            requireNonNull(foo, "foo is null");
        }
    }
}
