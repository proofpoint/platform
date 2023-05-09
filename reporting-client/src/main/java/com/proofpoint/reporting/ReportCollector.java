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
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.reporting.ReportedBeanRegistry.RegistrationInfo;
import jakarta.inject.Inject;

import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.util.Map;

import static com.google.common.base.CaseFormat.LOWER_HYPHEN;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.proofpoint.reporting.ReportUtils.isReportable;
import static java.util.Objects.requireNonNull;

public class ReportCollector
{
    private static final Logger log = Logger.get(ReportCollector.class);
    private final String applicationPrefix;
    private final MinuteBucketIdProvider bucketIdProvider;
    private final ReportedBeanRegistry reportedBeanRegistry;
    private final ReportSink reportSink;
    private final Map<String, String> versionTags;

    @Inject
    ReportCollector(
            NodeInfo nodeInfo,
            MinuteBucketIdProvider bucketIdProvider,
            ReportedBeanRegistry reportedBeanRegistry,
            ReportSink reportSink)
    {
        applicationPrefix = LOWER_HYPHEN.to(UPPER_CAMEL, nodeInfo.getApplication()) + ".";
        this.bucketIdProvider = requireNonNull(bucketIdProvider, "bucketIdProvider is null");
        this.reportedBeanRegistry = requireNonNull(reportedBeanRegistry, "reportedBeanRegistry is null");
        this.reportSink = requireNonNull(reportSink, "reportSink is null");

        ImmutableMap.Builder<String, String> versionTagsBuilder = ImmutableMap.builder();
        if (!nodeInfo.getApplicationVersion().isEmpty()) {
            versionTagsBuilder.put("applicationVersion", nodeInfo.getApplicationVersion());
        }
        if (!nodeInfo.getPlatformVersion().isEmpty()) {
            versionTagsBuilder.put("platformVersion", nodeInfo.getPlatformVersion());
        }
        this.versionTags = versionTagsBuilder.build();
    }

    public void collectData()
    {
        try {
            long lastSystemTimeMillis = bucketIdProvider.getLastSystemTimeMillis();
            ImmutableTable.Builder<String, Map<String, String>, Object> builder = ImmutableTable.builder();
            int numAttributes = 0;
            for (RegistrationInfo registrationInfo : reportedBeanRegistry.getReportedBeans()) {
                for (ReportedBeanAttribute attribute : registrationInfo.getReportedBean().getAttributes()) {
                    Object value = null;

                    try {
                        value = attribute.getValue(null);
                    }
                    catch (MBeanException | ReflectionException ignored) {
                    }

                    if (value != null && isReportable(value)) {
                        if (!(value instanceof Number)) {
                            value = value.toString();
                        }

                        ++numAttributes;
                        StringBuilder stringBuilder = new StringBuilder();
                        if (registrationInfo.isApplicationPrefix()) {
                            stringBuilder.append(applicationPrefix);
                        }
                        String name = stringBuilder
                                .append(registrationInfo.getNamePrefix())
                                .append('.')
                                .append(attribute.getName())
                                .toString();
                        builder.put(name, registrationInfo.getTags(), value);
                    }
                }
            }
            builder.put("ReportCollector.NumMetrics", versionTags, numAttributes);
            reportSink.report(lastSystemTimeMillis, builder.build());
        }
        catch (Throwable e) {
            log.error(e, "Unexpected exception from report collection");
        }
    }

    Map<String, String> getVersionTags()
    {
        return versionTags;
    }
}
