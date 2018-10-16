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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.proofpoint.reporting.Bucketed.BucketInfo;

import javax.annotation.Nullable;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.util.Collection;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.proofpoint.reporting.PrometheusBeanAttribute.ValueAndTimestamp.valueAndTimestamp;
import static com.proofpoint.reporting.ReflectionUtils.invoke;
import static com.proofpoint.reporting.ReportedBean.GET_PREVIOUS_BUCKET;
import static com.proofpoint.reporting.SummaryPrometheusValue.summaryPrometheusValue;
import static java.util.Objects.requireNonNull;

class SummaryPrometheusBeanAttribute implements PrometheusBeanAttribute
{
    private final Object holder;
    private final Collection<PrometheusBeanAttribute> delegates;
    private final String name;

    SummaryPrometheusBeanAttribute(Object holder, Collection<PrometheusBeanAttribute> delegates)
    {
        this.holder = requireNonNull(holder, "holder is null");
        this.delegates = ImmutableList.copyOf(delegates);
        String attributeName = delegates.stream().findFirst().get().getName();
        int lastDot = attributeName.lastIndexOf('.');
        if (lastDot == -1) {
            this.name = "";
        }
        else {
            this.name = attributeName.substring(0, lastDot);
        }
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getType()
    {
        return "summary";
    }

    @Override
    public ValueAndTimestamp getValue(@Nullable Object target)
            throws MBeanException, ReflectionException
    {
        BucketInfo bucketInfo = (BucketInfo) invoke(firstNonNull(target, holder), GET_PREVIOUS_BUCKET);
        Builder<String, PrometheusValue> builder = ImmutableMap.builder();
        for (PrometheusBeanAttribute delegate : delegates) {
            ValueAndTimestamp valueAndTimestamp = null;
            try {
                valueAndTimestamp = delegate.getValue(bucketInfo.getBucket());
            }
            catch (MBeanException | ReflectionException ignored) {
            }
            if (valueAndTimestamp != null) {
                builder.put(delegate.getName(), valueAndTimestamp.getValue());
            }
        }

        return valueAndTimestamp(summaryPrometheusValue(builder.build()), bucketInfo.getBucketId().getTimestamp());
    }
}
