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

import com.google.inject.Injector;
import com.proofpoint.audit.TestFileAuditLogger.TestingRecord;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.json.JsonModule;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

public class TestAuditLogModule
{
    @Test
    public void testNotConfigured()
            throws Exception
    {
        Injector injector = Bootstrap.bootstrapTest()
                .withModules(
                        new AuditLogModule()
                )
                .initialize();
        AuditLoggerFactory auditLoggerFactory = injector.getInstance(AuditLoggerFactory.class);
        auditLoggerFactory.create(TestingRecord.class);
    }

    @Test
    public void testConfigured()
            throws Exception
    {
        File file = File.createTempFile(getClass().getName(), ".log");

        try {
            Injector injector = Bootstrap.bootstrapTest()
                    .withModules(
                            new AuditLogModule(),
                            new JsonModule()
                    )
                    .setRequiredConfigurationProperty("audit.log.path", file.getAbsolutePath())
                    .initialize();
            AuditLoggerFactory auditLoggerFactory = injector.getInstance(AuditLoggerFactory.class);
            auditLoggerFactory.create(TestingRecord.class);
        }
        finally {
            if (!file.delete()) {
                throw new IOException("Error deleting " + file.getAbsolutePath());
            }
        }
    }
}
