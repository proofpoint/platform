/*
 * Copyright 2016 Proofpoint, Inc.
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

import com.google.inject.Injector;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.annotations.Test;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;

public class TestReportingClientModule
{
    @Test
    public void testCreateInjector()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new ReportingModule(),
                        new JsonModule(),
                        new TestingNodeModule(),
                        new ReportingClientModule()
                )
                .setRequiredConfigurationProperty("service-client.reporting.uri", "https://reporting.invalid")
                .initialize();

        injector.getInstance(LifeCycleManager.class).stop();
    }
}
