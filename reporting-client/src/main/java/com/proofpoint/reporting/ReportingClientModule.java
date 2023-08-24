/*
 * Copyright 2013 Proofpoint, Inc.
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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy;
import java.util.concurrent.TimeUnit;

import static com.google.inject.Scopes.SINGLETON;
import static com.proofpoint.concurrent.Threads.daemonThreadsNamed;
import static com.proofpoint.configuration.ConfigBinder.bindConfig;
import static com.proofpoint.http.client.HttpClientBinder.httpClientBinder;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class ReportingClientModule
    implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(ReportScheduler.class).in(SINGLETON);
        binder.bind(ReportCollector.class).in(SINGLETON);
        binder.bind(ReportSink.class).to(ReportQueue.class).in(SINGLETON);
        binder.bind(ReportClient.class).in(SINGLETON);

        httpClientBinder(binder).bindBalancingHttpClient("reporting", ForReportClient.class, "reporting");
        bindConfig(binder).bind(ReportClientConfig.class);

        binder.install(new ReportingBaseMetricsModule());
    }

    @Provides
    @ForReportCollector
    private static ScheduledExecutorService createCollectionExecutorService()
    {
        return newSingleThreadScheduledExecutor(daemonThreadsNamed("reporting-collector-%s"));
    }

    @Provides
    @ForReportClient
    private static ExecutorService createClientExecutorService()
    {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.NANOSECONDS, new LinkedBlockingQueue<>(5),
                        daemonThreadsNamed("reporting-client-%s"),
                        new DiscardOldestPolicy());
    }
}
