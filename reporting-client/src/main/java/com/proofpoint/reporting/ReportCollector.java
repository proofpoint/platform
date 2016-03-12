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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.inject.Inject;
import com.proofpoint.reporting.ReportedBeanRegistry.RegistrationInfo;

import javax.annotation.PostConstruct;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.System.currentTimeMillis;

class ReportCollector
{
    private final ScheduledExecutorService collectionExecutorService;
    private final MinuteBucketIdProvider bucketIdProvider;
    private final ReportedBeanRegistry reportedBeanRegistry;
    private final ExecutorService clientExecutorService;
    private final ReportClient reportClient;

    @Inject
    ReportCollector(
            MinuteBucketIdProvider bucketIdProvider,
            ReportedBeanRegistry reportedBeanRegistry,
            ReportClient reportClient,
            @ForReportCollector ScheduledExecutorService collectionExecutorService,
            @ForReportClient ExecutorService clientExecutorService)
    {
        this.bucketIdProvider = checkNotNull(bucketIdProvider, "bucketIdProvider is null");
        this.reportedBeanRegistry = checkNotNull(reportedBeanRegistry, "reportedBeanRegistry is null");
        this.reportClient = checkNotNull(reportClient, "reportClient is null");
        this.collectionExecutorService = checkNotNull(collectionExecutorService, "collectionExecutorService is null");
        this.clientExecutorService = checkNotNull(clientExecutorService, "clientExecutorService is null");
    }

    @PostConstruct
    public void start()
    {
        collectionExecutorService.scheduleAtFixedRate((Runnable) this::collectData, 1, 1, TimeUnit.MINUTES);

        clientExecutorService.submit((Runnable) () -> reportClient.report(currentTimeMillis(), ImmutableTable.of("ReportCollector.ServerStart", ImmutableMap.of(), (Object) 1)));
    }

    private void collectData()
    {
        final long lastSystemTimeMillis = bucketIdProvider.getLastSystemTimeMillis();
        ImmutableTable.Builder<String, Map<String, String>, Object> builder = ImmutableTable.builder();
        int numAttributes = 0;
        for (RegistrationInfo registrationInfo : reportedBeanRegistry.getReportedBeans()) {
            for (ReportedBeanAttribute attribute : registrationInfo.getReportedBean().getAttributes()) {
                Object value = null;

                try {
                    value = attribute.getValue(null);
                }
                catch (AttributeNotFoundException | MBeanException | ReflectionException ignored) {
                }

                if (value != null && isReportable(value)) {
                    if (!(value instanceof Number)) {
                        value = value.toString();
                    }

                    ++numAttributes;
                    builder.put(registrationInfo.getNamePrefix() + '.' + attribute.getName(), registrationInfo.getTags(), value);
                }
            }
        }
        builder.put("ReportCollector.NumMetrics", ImmutableMap.of(), numAttributes);
        final Table<String, Map<String, String>, Object> collectedData = builder.build();
        clientExecutorService.submit((Runnable) () -> reportClient.report(lastSystemTimeMillis, collectedData));
    }

    private static boolean isReportable(Object value)
    {
        if (value instanceof Double) {
            return !(((Double) value).isNaN() || ((Double) value).isInfinite());
        }
        if (value instanceof Float) {
            return !(((Float) value).isNaN() || ((Float) value).isInfinite());
        }
        if (value instanceof Long) {
            return !(value.equals(Long.MAX_VALUE) || value.equals(Long.MIN_VALUE));
        }
        if (value instanceof Integer) {
            return !(value.equals(Integer.MAX_VALUE) || value.equals(Integer.MIN_VALUE));
        }
        if (value instanceof Short) {
            return !(value.equals(Short.MAX_VALUE) || value.equals(Short.MIN_VALUE));
        }
        return true;
    }
}
