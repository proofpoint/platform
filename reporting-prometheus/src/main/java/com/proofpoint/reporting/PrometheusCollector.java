/*
 * Copyright 2018 Proofpoint, Inc.
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
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.reporting.PrometheusBeanAttribute.ValueAndTimestamp;
import com.proofpoint.reporting.ReportedBeanRegistry.RegistrationInfo;
import jakarta.inject.Inject;

import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.google.common.base.CaseFormat.LOWER_HYPHEN;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.proofpoint.reporting.PrometheusBeanAttribute.ValueAndTimestamp.valueAndTimestamp;
import static com.proofpoint.reporting.SimplePrometheusValue.simplePrometheusValue;
import static com.proofpoint.reporting.TaggedValue.taggedValue;
import static java.util.Objects.requireNonNull;

class PrometheusCollector
{
    private static final Pattern NAME_NOT_ACCEPTED_CHARACTER_PATTERN = Pattern.compile("[^A-Za-z0-9_:]");
    private static final Pattern INITIAL_DIGIT_PATTERN = Pattern.compile("[0-9]");
    private final String applicationPrefix;
    private final ReportedBeanRegistry reportedBeanRegistry;
    private final Map<String, String> versionTags;
    private final BucketIdProvider bucketIdProvider;
    private final long startupTimestamp;

    @Inject
    PrometheusCollector(
            NodeInfo nodeInfo,
            ReportedBeanRegistry reportedBeanRegistry,
            BucketIdProvider bucketIdProvider)
    {
        applicationPrefix = sanitizeMetricName(LOWER_HYPHEN.to(UPPER_CAMEL, nodeInfo.getApplication())) + "_";
        this.reportedBeanRegistry = requireNonNull(reportedBeanRegistry, "reportedBeanRegistry is null");

        ImmutableMap.Builder<String, String> versionTagsBuilder = ImmutableMap.builder();
        if (!nodeInfo.getApplicationVersion().isEmpty()) {
            versionTagsBuilder.put("applicationVersion", nodeInfo.getApplicationVersion());
        }
        if (!nodeInfo.getPlatformVersion().isEmpty()) {
            versionTagsBuilder.put("platformVersion", nodeInfo.getPlatformVersion());
        }
        this.versionTags = versionTagsBuilder.build();
        this.bucketIdProvider = bucketIdProvider;
        startupTimestamp = bucketIdProvider.get().getTimestamp();
    }

    private static String sanitizeMetricName(String name)
    {
        return NAME_NOT_ACCEPTED_CHARACTER_PATTERN.matcher(name).replaceAll("_");
    }

    Multimap<String, TaggedValue> collectData()
    {
        Multimap<String, TaggedValue> valuesByMetric = MultimapBuilder.treeKeys().treeSetValues().build();

        for (RegistrationInfo registrationInfo : reportedBeanRegistry.getReportedBeans()) {
            StringBuilder nameBuilder = new StringBuilder();
            if (registrationInfo.applicationPrefix()) {
                nameBuilder.append(applicationPrefix);
            }
            nameBuilder.append(sanitizeMetricName(registrationInfo.namePrefix()));

            for (PrometheusBeanAttribute attribute : registrationInfo.reportedBean().getPrometheusAttributes()) {
                String metricName = sanitizeMetricName(attribute.getName());
                String name;
                if ("".equals(metricName)) {
                    name = nameBuilder.toString();
                }
                else {
                    name = nameBuilder + "_" + metricName;
                }
                if (INITIAL_DIGIT_PATTERN.matcher(name).lookingAt()) {
                    name = "_" + name;
                }
                ValueAndTimestamp valueAndTimestamp = null;

                try {
                    valueAndTimestamp = attribute.getValue(null);
                }
                catch (MBeanException | ReflectionException ignored) {
                }

                if (valueAndTimestamp != null) {
                    valuesByMetric.put(name, taggedValue(registrationInfo.tags(), valueAndTimestamp));
                }
            }
        }
        valuesByMetric.put("ReportCollector_NumMetrics", taggedValue(versionTags, valueAndTimestamp(simplePrometheusValue(valuesByMetric.size()), null)));
        if (bucketIdProvider.get().getTimestamp() < startupTimestamp + TimeUnit.MINUTES.toMillis(10)) {
            valuesByMetric.put("ReportCollector_ServerStart", taggedValue(versionTags, valueAndTimestamp(simplePrometheusValue(1), startupTimestamp)));
        }
        return valuesByMetric;
    }
}
