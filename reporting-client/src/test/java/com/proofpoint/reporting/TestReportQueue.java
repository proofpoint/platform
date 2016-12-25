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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.proofpoint.testing.SerialScheduledExecutorService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class TestReportQueue
{
    private static final ImmutableTable<String, Map<String, String>, Object> TESTING_METRIC_DATA = ImmutableTable.of("row", ImmutableMap.of("tag", "tagValue"), 3.14);

    private ExecutorService clientExecutorService;
    private ReportClient reportClient;

    @BeforeMethod
    public void setup()
    {
        clientExecutorService = spy(new SerialScheduledExecutorService());
        reportClient = mock(ReportClient.class);
    }

    @Test
    public void testSubmit()
    {
        ReportQueue reportQueue = new ReportQueue(new ReportClientConfig().setEnabled(true), clientExecutorService, reportClient);

        reportQueue.report(100, TESTING_METRIC_DATA);

        verify(clientExecutorService).submit(any(Runnable.class));
        verify(reportClient).report(100, TESTING_METRIC_DATA);
        verifyNoMoreInteractions(reportClient);
    }

    @Test
    public void testDisabled()
    {
        ReportQueue reportQueue = new ReportQueue(new ReportClientConfig().setEnabled(false), clientExecutorService, reportClient);

        reportQueue.report(100, TESTING_METRIC_DATA);

        verifyNoMoreInteractions(clientExecutorService);
        verifyNoMoreInteractions(reportClient);
    }
}
