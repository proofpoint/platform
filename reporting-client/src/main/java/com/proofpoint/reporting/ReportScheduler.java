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

import com.google.common.collect.ImmutableTable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.System.currentTimeMillis;
import static java.util.Objects.requireNonNull;

class ReportScheduler
{
    private final ScheduledExecutorService collectionExecutorService;
    private final ReportCollector reportCollector;
    private final ReportSink reportSink;

    @Inject
    ReportScheduler(
            ReportCollector reportCollector,
            ReportSink reportSink,
            @ForReportCollector ScheduledExecutorService collectionExecutorService)
    {
        this.reportCollector = requireNonNull(reportCollector, "reportCollector is null");
        this.reportSink = requireNonNull(reportSink, "reportQueue is null");
        this.collectionExecutorService = requireNonNull(collectionExecutorService, "collectionExecutorService is null");
    }

    @PostConstruct
    public void start()
    {
        collectionExecutorService.scheduleAtFixedRate(reportCollector::collectData, 1, 1, TimeUnit.MINUTES);

        reportSink.report(currentTimeMillis(), ImmutableTable.of("ReportCollector.ServerStart", reportCollector.getVersionTags(), 1));
    }
}
