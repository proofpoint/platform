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
package com.proofpoint.reporting.testing;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.proofpoint.reporting.BucketIdProvider;
import com.proofpoint.reporting.ReportCollector;
import com.proofpoint.reporting.ReportSink;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.reporting.testing.ReportingTester.TestingSink;

import static com.google.inject.Scopes.SINGLETON;

/**
 * A replacement for {@link ReportingModule} and {@link com.proofpoint.reporting.ReportingClientModule}
 * for use by unit tests that need to test what metrics get reported.
 *
 * Unit tests should obtain the {@link ReportingTester} from the {@link com.google.inject.Injector}
 * and use it to collect and return the reported metrics.
 */
public class TestingReportingModule
    implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.install(Modules.override(new ReportingModule())
                .with(binder1 -> binder1.bind(BucketIdProvider.class).to(TestingBucketIdProvider.class).in(SINGLETON)));

        binder.bind(ReportingTester.class).in(SINGLETON);
        binder.bind(TestingBucketIdProvider.class).in(SINGLETON);
        binder.bind(ReportSink.class).to(TestingSink.class).in(SINGLETON);
        binder.bind(TestingSink.class).in(SINGLETON);
        binder.bind(ReportCollector.class).in(SINGLETON);
    }
}
