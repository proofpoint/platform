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
import com.google.common.collect.Table;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.ApplicationNameModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.testing.SerialScheduledExecutorService;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static com.proofpoint.testing.Assertions.assertBetweenInclusive;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;

public class TestReportScheduler
{
    private static final ImmutableMap<String, String> EXPECTED_VERSION_TAGS = ImmutableMap.of("applicationVersion", "1.2", "platformVersion", "platform.1");

    @Mock
    private ReportCollector reportCollector;
    @Mock
    private ReportSink reportSink;
    private SerialScheduledExecutorService collectorExecutor;
    private ReportScheduler reportScheduler;

    @Captor
    ArgumentCaptor<Table<String, Map<String, String>, Object>> tableCaptor;

    @BeforeMethod
    public void setup()
    {
        initMocks(this);
        when(reportCollector.getVersionTags()).thenReturn(EXPECTED_VERSION_TAGS);
        collectorExecutor = new SerialScheduledExecutorService();
        reportScheduler = new ReportScheduler(reportCollector, reportSink, collectorExecutor);
    }

    @Test
    public void testReportingModule()
    {
        Injector injector = Guice.createInjector(
                new ApplicationNameModule("test-application"),
                new TestingNodeModule(),
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.of(
                        "service-client.reporting.uri", "https://reporting.invalid"
                ))),
                new JsonModule(),
                new ReportingModule(),
                new ReportingClientModule());
        injector.getInstance(ReportScheduler.class);
    }

    @Test
    public void testReportsStartup()
    {
        ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);

        long lowerBound = currentTimeMillis();
        reportScheduler.start();
        long upperBound = currentTimeMillis();

        verify(reportSink).report(longCaptor.capture(), tableCaptor.capture());
        verifyNoMoreInteractions(reportSink);

        assertBetweenInclusive(longCaptor.getValue(), lowerBound, upperBound);

        Table<String, Map<String, String>, Object> table = tableCaptor.getValue();
        assertEquals(table.cellSet(), ImmutableTable.<String, Map<String, String>, Object>builder()
                .put("ReportCollector.ServerStart", EXPECTED_VERSION_TAGS, 1)
                .build()
                .cellSet());

        collectorExecutor.elapseTimeNanosecondBefore(1, MINUTES);
        verifyNoMoreInteractions(reportSink);
    }

    @Test
    public void testScheduling()
    {
        testReportsStartup();
        verify(reportCollector, atLeast(0)).getVersionTags();
        verifyNoMoreInteractions(reportCollector);

        collectorExecutor.elapseTime(1, NANOSECONDS);
        verify(reportCollector).collectData();
        verifyNoMoreInteractions(reportCollector);

        collectorExecutor.elapseTimeNanosecondBefore(1, MINUTES);
        verify(reportCollector).collectData();

        collectorExecutor.elapseTime(1, NANOSECONDS);
        verify(reportCollector, times(2)).collectData();
        verifyNoMoreInteractions(reportCollector);
        verifyNoMoreInteractions(reportSink);
    }
}
