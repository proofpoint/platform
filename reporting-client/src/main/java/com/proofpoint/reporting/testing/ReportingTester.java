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

import com.google.common.collect.Table;
import com.proofpoint.reporting.ReportCollector;
import com.proofpoint.reporting.ReportSink;
import jakarta.inject.Inject;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public class ReportingTester
{
    private final TestingBucketIdProvider bucketIdProvider;
    private final ReportCollector reportCollector;
    private final TestingSink testingSink;

    @Inject
    public ReportingTester(TestingBucketIdProvider bucketIdProvider, ReportCollector reportCollector, TestingSink testingSink)
    {
        this.bucketIdProvider = requireNonNull(bucketIdProvider, "bucketIdProvider is null");
        this.reportCollector = requireNonNull(reportCollector, "reportCollector is null");
        this.testingSink = requireNonNull(testingSink, "testingSink is null");
    }

    /**
     * Collect and return all reported metrics. Intended for use in unit tests.
     *
     * @return The collected metrics as a table of metric names, tags, and values.
     */
    public Table<String, Map<String, String>, Object> collectData()
    {
        bucketIdProvider.incrementBucket();
        reportCollector.collectData();
        return testingSink.getCollectedData();
    }

    static class TestingSink
        implements ReportSink
    {
        private Table<String, Map<String, String>, Object> collectedData;

        @Override
        public void report(long systemTimeMillis, Table<String, Map<String, String>, Object> collectedData)
        {
            this.collectedData = collectedData;
        }

        Table<String, Map<String, String>, Object> getCollectedData()
        {
            return collectedData;
        }
    }
}
