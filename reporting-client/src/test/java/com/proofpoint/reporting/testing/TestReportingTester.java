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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.inject.Injector;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.Gauge;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.stats.MaxGauge;
import org.testng.annotations.Test;
import org.weakref.jmx.Flatten;

import java.util.Map;

import static com.google.inject.Scopes.SINGLETON;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.reporting.ReportBinder.reportBinder;
import static org.testng.Assert.assertEquals;

public class TestReportingTester
{
    private static final Map<String, String> TESTING_TAGS = ImmutableMap.of("tag", "tagValue");

    @Test
    public void testTestingReportingClientModule()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        new TestingReportingModule()
                )
                .initialize();
        injector.getInstance(ReportingTester.class);
    }

    @Test
    public void testCollect()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        new TestingReportingModule(),
                        binder -> {
                            binder.bind(TestingMetric.class).in(SINGLETON);
                            reportBinder(binder).export(TestingMetric.class).withTags(TESTING_TAGS);
                        }
                )
                .initialize();
        ReportingTester reportingTester = injector.getInstance(ReportingTester.class);
        Table<String, Map<String, String>, Object> data = reportingTester.collectData();
        assertEquals(data, ImmutableTable.builder()
                .put("TestingMetric.Metric", TESTING_TAGS, 3)
                .put("ReportCollector.NumMetrics", ImmutableMap.of(), 1)
                .build()
        );
    }

    @Test
    public void testApplicationPrefix()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        new TestingReportingModule(),
                        binder -> {
                            binder.bind(TestingMetric.class).in(SINGLETON);
                            reportBinder(binder).export(TestingMetric.class).withApplicationPrefix().withTags(TESTING_TAGS);
                        }
                )
                .initialize();
        ReportingTester reportingTester = injector.getInstance(ReportingTester.class);
        Table<String, Map<String, String>, Object> data = reportingTester.collectData();
        assertEquals(data, ImmutableTable.builder()
                .put("TestApplication.TestingMetric.Metric", TESTING_TAGS, 3)
                .put("ReportCollector.NumMetrics", ImmutableMap.of(), 1)
                .build()
        );
    }

    @Test
    public void testCollectBucketed()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        new TestingReportingModule(),
                        binder -> {
                            binder.bind(TestingBucketedMetric.class).in(SINGLETON);
                            reportBinder(binder).export(TestingBucketedMetric.class).withTags(TESTING_TAGS);
                        }
                )
                .initialize();
        ReportingTester reportingTester = injector.getInstance(ReportingTester.class);
        injector.getInstance(TestingBucketedMetric.class).getMax().add(3);
        Table<String, Map<String, String>, Object> data = reportingTester.collectData();
        assertEquals(data, ImmutableTable.<String, Map<String, String>, Object>builder()
                .put("TestingBucketedMetric.Max", TESTING_TAGS, 3L)
                .put("ReportCollector.NumMetrics", ImmutableMap.of(), 1)
                .build()
        );
    }

    private static class TestingMetric
    {
        @Gauge
        public int getMetric()
        {
            return 3;
        }
    }

    private static class TestingBucketedMetric
    {
        private final MaxGauge maxGauge = new MaxGauge();

        @Flatten
        public MaxGauge getMax() {
            return maxGauge;
        }
    }
}
