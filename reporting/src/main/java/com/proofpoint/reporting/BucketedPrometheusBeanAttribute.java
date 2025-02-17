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

import com.proofpoint.reporting.Bucketed.BucketInfo;

import javax.management.MBeanException;
import javax.management.ReflectionException;

import static com.proofpoint.reporting.PrometheusBeanAttribute.ValueAndTimestamp.valueAndTimestamp;
import static com.proofpoint.reporting.ReflectionUtils.invoke;
import static com.proofpoint.reporting.ReportedBean.GET_PREVIOUS_BUCKET;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

class BucketedPrometheusBeanAttribute implements PrometheusBeanAttribute
{
    private final PrometheusBeanAttribute delegate;
    private final String name;
    private final Object holder;

    BucketedPrometheusBeanAttribute(Object holder, PrometheusBeanAttribute delegate)
    {
        this.holder = requireNonNull(holder, "holder is null");
        this.delegate = delegate;
        name = delegate.getName();
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public ValueAndTimestamp getValue(Object target)
            throws MBeanException, ReflectionException
    {
        BucketInfo bucketInfo = (BucketInfo) invoke(requireNonNullElse(target, holder), GET_PREVIOUS_BUCKET);
        ValueAndTimestamp valueAndTimestamp = delegate.getValue(bucketInfo.getBucket());
        if (valueAndTimestamp == null) {
            return null;
        }
        return valueAndTimestamp(valueAndTimestamp.value(), bucketInfo.getBucketId().getTimestamp());
    }
}
