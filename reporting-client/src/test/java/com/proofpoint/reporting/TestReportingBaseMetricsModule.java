/*
 * Copyright 2018 Proofpoint, Inc.
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
package com.proofpoint.reporting;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.log.Logger;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.testing.ReportingTester;
import com.proofpoint.reporting.testing.TestingReportingModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static org.assertj.guava.api.Assertions.assertThat;

public class TestReportingBaseMetricsModule
{
    private static final Logger log = Logger.get(TestReportingBaseMetricsModule.class);
    private LifeCycleManager lifeCycleManager;

    @BeforeMethod
    public void setup()
    {
        lifeCycleManager = null;
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
            throws Exception
    {
        if (lifeCycleManager != null) {
            lifeCycleManager.stop();
        }
    }

    @Test
    public void testLogCounter()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new ReportingBaseMetricsModule(),
                        new TestingReportingModule(),
                        new TestingNodeModule()
                )
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        log.error("testing error log line");
        log.warn("testing warn log line");

        assertThat(injector.getInstance(ReportingTester.class).collectData())
                .containsCell("ReportCollector.LogErrors.Count", ImmutableMap.of(), 1.0);
    }
}
