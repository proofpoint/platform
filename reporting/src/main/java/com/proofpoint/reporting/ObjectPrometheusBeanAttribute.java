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

import javax.annotation.Nullable;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.lang.reflect.Method;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.proofpoint.reporting.PrometheusBeanAttribute.ValueAndTimestamp.valueAndTimestamp;
import static com.proofpoint.reporting.ReflectionUtils.invoke;
import static com.proofpoint.reporting.SimplePrometheusValue.simplePrometheusValue;
import static java.util.Objects.requireNonNull;

class ObjectPrometheusBeanAttribute implements PrometheusBeanAttribute
{
    private final Object target;
    private final String name;
    private final PrometheusType type;
    private final Method getter;

    ObjectPrometheusBeanAttribute(String name, Prometheus prometheus, Object target, Method getter)
    {
        this.type = requireNonNull(prometheus, "prometheus is null").type();
        if ("".equals(prometheus.name())) {
            this.name = requireNonNull(name, "name is null");
        }
        else {
            this.name = prometheus.name();
        }
        this.target = requireNonNull(target, "target is null");
        this.getter = requireNonNull(getter, "getter is null");
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getType()
    {
        return type.name().toLowerCase();
    }

    @Override
    public ValueAndTimestamp getValue(@Nullable Object target)
            throws MBeanException, ReflectionException
    {
        return valueAndTimestamp(simplePrometheusValue(invoke(firstNonNull(target, this.target), getter)), null);
    }
}
