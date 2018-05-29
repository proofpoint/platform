/*
 *  Copyright 2010 Dain Sundstrom
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.proofpoint.reporting;

import com.google.common.collect.ImmutableList;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.proofpoint.reporting.PrometheusType.GAUGE;
import static com.proofpoint.reporting.PrometheusType.SUPPRESSED;
import static com.proofpoint.reporting.ReflectionUtils.isValidGetter;
import static com.proofpoint.reporting.ReportedMethodInfo.reportedMethodInfo;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

class ReportedMethodInfoBuilder
{
    private static final Prometheus DEFAULT_PROMETHEUS = new Prometheus()
    {
        @Override
        public String name()
        {
            return "";
        }

        @Override
        public PrometheusType type()
        {
            return GAUGE;
        }

        @Override
        public Class<? extends Annotation> annotationType()
        {
            return Prometheus.class;
        }
    };
    private Object target;
    private String name;
    private Method concreteGetter;
    private Method annotatedGetter;

    public ReportedMethodInfoBuilder onInstance(Object target)
    {
        requireNonNull(target, "target is null");
        this.target = target;
        return this;
    }

    public ReportedMethodInfoBuilder named(String name)
    {
        requireNonNull(name, "name is null");
        this.name = name;
        return this;
    }

    public ReportedMethodInfoBuilder withConcreteGetter(Method concreteGetter)
    {
        requireNonNull(concreteGetter, "concreteGetter is null");
        checkArgument(isValidGetter(concreteGetter), "Method is not a valid getter: " + concreteGetter);
        this.concreteGetter = concreteGetter;
        return this;
    }

    public ReportedMethodInfoBuilder withAnnotatedGetter(Method annotatedGetter)
    {
        requireNonNull(annotatedGetter, "annotatedGetter is null");
        checkArgument(isValidGetter(annotatedGetter), "Method is not a valid getter: " + annotatedGetter);
        this.annotatedGetter = annotatedGetter;
        return this;
    }

    public ReportedMethodInfo build()
    {
        checkArgument(target != null, "JmxAttribute must have a target object");
        Prometheus prometheus = firstNonNull(annotatedGetter.getAnnotation(Prometheus.class), DEFAULT_PROMETHEUS);

        if (AnnotationUtils.isFlatten(annotatedGetter)) {
            checkArgument(concreteGetter != null, "Flattened JmxAttribute must have a concrete getter");

            Object value = null;
            try {
                value = concreteGetter.invoke(target);
            }
            catch (Exception ignored) {
                // todo log me
            }
            if (value == null) {
                return reportedMethodInfo(ImmutableList.of(), ImmutableList.of());
            }

            ReportedBean reportedBean = ReportedBean.forTarget(value);
            List<ReportedBeanAttribute> attributes = reportedBean.getAttributes().stream()
                    .map(attribute -> new FlattenReportedBeanAttribute(concreteGetter, attribute))
                    .collect(toList());
            List<PrometheusBeanAttribute> prometheusAttributes;
            if (prometheus.type() == SUPPRESSED) {
                prometheusAttributes = ImmutableList.of();
            }
            else {
                prometheusAttributes = reportedBean.getPrometheusAttributes().stream()
                        .map(attribute -> new FlattenPrometheusBeanAttribute(concreteGetter, attribute))
                        .collect(toList());
            }
            return reportedMethodInfo(attributes, prometheusAttributes);
        }
        else if (AnnotationUtils.isNested(annotatedGetter)) {
            checkArgument(concreteGetter != null, "Nested JmxAttribute must have a concrete getter");

            Object value = null;
            try {
                value = concreteGetter.invoke(target);
            }
            catch (Exception ignored) {
                // todo log me
            }
            if (value == null) {
                return reportedMethodInfo(ImmutableList.of(), ImmutableList.of());
            }

            ReportedBean reportedBean = ReportedBean.forTarget(value);
            List<ReportedBeanAttribute> attributes = reportedBean.getAttributes().stream()
                    .map(attribute -> new NestedReportedBeanAttribute(name, concreteGetter, attribute))
                    .collect(toList());
            List<PrometheusBeanAttribute> prometheusAttributes;
            if (prometheus.type() == SUPPRESSED) {
                prometheusAttributes = ImmutableList.of();
            }
            else {
                prometheusAttributes = reportedBean.getPrometheusAttributes().stream()
                        .map(attribute -> new NestedPrometheusBeanAttribute(name, concreteGetter, attribute))
                        .collect(toList());
            }
            return reportedMethodInfo(attributes, prometheusAttributes);
        }
        else {
            checkArgument (concreteGetter != null, "JmxAttribute must have a concrete getter");

            Class<?> attributeType = concreteGetter.getReturnType();

            if (Boolean.class.isAssignableFrom(attributeType) || attributeType == boolean.class) {
                return reportedMethodInfo(
                        AnnotationUtils.isReported(annotatedGetter) ?
                                ImmutableList.of(new BooleanReportedBeanAttribute(name, target, concreteGetter)) :
                                ImmutableList.of(),
                        prometheus.type() != SUPPRESSED ?
                                ImmutableList.of(new BooleanPrometheusBeanAttribute(name, target, concreteGetter)) :
                                ImmutableList.of()
                );
            }

            return reportedMethodInfo(
                    AnnotationUtils.isReported(annotatedGetter) ?
                            ImmutableList.of(new ObjectReportedBeanAttribute(name, target, concreteGetter)) :
                            ImmutableList.of(),
                    prometheus.type() != SUPPRESSED ?
                            ImmutableList.of(new ObjectPrometheusBeanAttribute(name, prometheus, target, concreteGetter)) :
                            ImmutableList.of()
            );
        }
    }
}
