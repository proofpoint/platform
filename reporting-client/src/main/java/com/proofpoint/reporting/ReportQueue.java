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

import com.google.common.collect.Table;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import static java.util.Objects.requireNonNull;

class ReportQueue implements ReportSink
{
    private final boolean enabled;
    private final ExecutorService clientExecutorService;
    private final ReportClient reportClient;

    @Inject
    ReportQueue(ReportClientConfig reportClientConfig, @ForReportClient ExecutorService clientExecutorService, ReportClient reportClient)
    {
        enabled = reportClientConfig.isEnabled();
        this.clientExecutorService = requireNonNull(clientExecutorService, "clientExecutorService is null");
        this.reportClient = reportClient;
    }

    @Override
    public void report(long systemTimeMillis, Table<String, Map<String, String>, Object> collectedData)
    {
        if (!enabled) {
            return;
        }

        clientExecutorService.submit(() -> reportClient.report(systemTimeMillis, collectedData));
    }
}
