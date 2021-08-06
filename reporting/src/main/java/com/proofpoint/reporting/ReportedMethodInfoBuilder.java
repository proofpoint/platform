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

import java.lang.reflect.Method;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.proofpoint.reporting.ReflectionUtils.isValidGetter;
import static com.proofpoint.reporting.ReportedMethodInfo.reportedMethodInfo;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

class ReportedMethodInfoBuilder
{
    private final BucketIdProvider bucketIdProvider;
    private Object target;
    private String name;
    private Method concreteGetter;
    private Method annotatedGetter;

    ReportedMethodInfoBuilder(BucketIdProvider bucketIdProvider)
    {
        this.bucketIdProvider = bucketIdProvider;
    }

    ReportedMethodInfoBuilder onInstance(Object target)
    {
        requireNonNull(target, "target is null");
        this.target = target;
        return this;
    }

    ReportedMethodInfoBuilder named(String name)
    {
        requireNonNull(name, "name is null");
        this.name = name;
        return this;
    }

    ReportedMethodInfoBuilder withConcreteGetter(Method concreteGetter)
    {
        requireNonNull(concreteGetter, "concreteGetter is null");
        checkArgument(isValidGetter(concreteGetter), "Method is not a valid getter: " + concreteGetter);
        this.concreteGetter = concreteGetter;
        return this;
    }

    ReportedMethodInfoBuilder withAnnotatedGetter(Method annotatedGetter)
    {
        requireNonNull(annotatedGetter, "annotatedGetter is null");
        checkArgument(isValidGetter(annotatedGetter), "Method is not a valid getter: " + annotatedGetter);
        this.annotatedGetter = annotatedGetter;
        return this;
    }

    ReportedMethodInfo build()
    {
        checkArgument(target != null, "JmxAttribute must have a target object");

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
                return reportedMethodInfo(List.of(), List.of());
            }

            ReportedBean reportedBean = ReportedBean.forTarget(value, bucketIdProvider);
            List<ReportedBeanAttribute> attributes = reportedBean.getAttributes().stream()
                    .map(attribute -> new FlattenReportedBeanAttribute(concreteGetter, attribute))
                    .collect(toList());
            List<PrometheusBeanAttribute> prometheusAttributes = reportedBean.getPrometheusAttributes().stream()
                    .map(attribute -> new FlattenPrometheusBeanAttribute(concreteGetter, attribute))
                    .collect(toList());
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
                return reportedMethodInfo(List.of(), List.of());
            }

            ReportedBean reportedBean = ReportedBean.forTarget(value, bucketIdProvider);
            List<ReportedBeanAttribute> attributes = reportedBean.getAttributes().stream()
                    .map(attribute -> new NestedReportedBeanAttribute(name, concreteGetter, attribute))
                    .collect(toList());
            List<PrometheusBeanAttribute> prometheusAttributes = reportedBean.getPrometheusAttributes().stream()
                    .map(attribute -> new NestedPrometheusBeanAttribute(name, concreteGetter, attribute))
                    .collect(toList());
            return reportedMethodInfo(attributes, prometheusAttributes);
        }
        else {
            checkArgument (concreteGetter != null, "JmxAttribute must have a concrete getter");

            Class<?> attributeType = concreteGetter.getReturnType();

            if (Boolean.class.isAssignableFrom(attributeType) || attributeType == boolean.class) {
                return reportedMethodInfo(
                        AnnotationUtils.isReported(annotatedGetter) ?
                                List.of(new BooleanReportedBeanAttribute(name, target, concreteGetter)) :
                                List.of(),
                        List.of(new BooleanPrometheusBeanAttribute(name, target, concreteGetter))
                );
            }

            return reportedMethodInfo(
                    AnnotationUtils.isReported(annotatedGetter) ?
                            List.of(new ObjectReportedBeanAttribute(name, target, concreteGetter)) :
                            List.of(),
                    List.of(new ObjectPrometheusBeanAttribute(name, target, concreteGetter))
            );
        }
    }
}
