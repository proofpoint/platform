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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.proofpoint.reporting.Bucketed.BucketInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import static com.proofpoint.reporting.ReflectionUtils.getAttributeName;
import static com.proofpoint.reporting.ReflectionUtils.isGetter;
import static java.util.Objects.requireNonNull;

class ReportedBean
{
    private static final LoadingCache<Class<?>, Iterable<Entry<Method, Method>>> METHODS_CACHE = CacheBuilder.newBuilder()
            .build(CacheLoader.from(clazz -> AnnotationUtils.findAnnotatedMethods(clazz, ReportedAnnotation.class).entrySet()));

    static Method GET_PREVIOUS_BUCKET;

    private final Map<String, ReportedBeanAttribute> attributes;
    private final Map<String, PrometheusBeanAttribute> prometheusAttributes;

    static {
        try {
            Method getPreviousBucket = Bucketed.class.getDeclaredMethod("getPreviousBucket");
            getPreviousBucket.setAccessible(true);
            GET_PREVIOUS_BUCKET = getPreviousBucket;
        }
        catch (NoSuchMethodException ignored) {
            GET_PREVIOUS_BUCKET = null;
        }
    }

    private ReportedBean(Collection<ReportedBeanAttribute> attributes, Collection<PrometheusBeanAttribute> prometheusAttributes)
    {
        Map<String, ReportedBeanAttribute> attributesBuilder = new TreeMap<>();
        for (ReportedBeanAttribute attribute : attributes) {
            attributesBuilder.put(attribute.getName(), attribute);
        }
        this.attributes = Collections.unmodifiableMap(attributesBuilder);

        Map<String, PrometheusBeanAttribute> prometheusAttributesBuilder = new TreeMap<>();
        for (PrometheusBeanAttribute attribute : prometheusAttributes) {
            prometheusAttributesBuilder.put(attribute.getName(), attribute);
        }
        this.prometheusAttributes = Collections.unmodifiableMap(prometheusAttributesBuilder);
    }

    Collection<ReportedBeanAttribute> getAttributes()
    {
        return attributes.values();
    }

    Collection<PrometheusBeanAttribute> getPrometheusAttributes()
    {
        return prometheusAttributes.values();
    }

    static ReportedBean forTarget(Object target, BucketIdProvider bucketIdProvider)
    {
        requireNonNull(target, "target is null");

        List<ReportedBeanAttribute> attributes = new ArrayList<>();
        List<PrometheusBeanAttribute> prometheusAttributes = new ArrayList<>();

        if (target instanceof Bucketed<?> bucketed) {
            bucketed.setBucketIdProvider(bucketIdProvider);
            BucketInfo bucketInfo = null;
            try {
                bucketInfo = (BucketInfo) GET_PREVIOUS_BUCKET.invoke(target);
            }
            catch (Exception ignored) {
                // todo log me
            }
            if (bucketInfo != null) {
                ReportedBean reportedBean = ReportedBean.forTarget(bucketInfo.getBucket(), bucketIdProvider);
                for (ReportedBeanAttribute attribute : reportedBean.getAttributes()) {
                    attributes.add(new BucketedReportedBeanAttribute(target, attribute));
                }
                for (PrometheusBeanAttribute prometheusAttribute : reportedBean.getPrometheusAttributes()) {
                    prometheusAttributes.add(new BucketedPrometheusBeanAttribute(target, prometheusAttribute));
                }
            }
        }

        Map<String, ReportedMethodInfoBuilder> methodInfoBuilders = new TreeMap<>();

        for (Map.Entry<Method, Method> entry : METHODS_CACHE.getUnchecked(target.getClass())) {
            Method concreteMethod = entry.getKey();
            Method annotatedMethod = entry.getValue();

            if (!isGetter(concreteMethod)) {
                throw new RuntimeException("report annotation on non-getter " + annotatedMethod.toGenericString());
            }

            String attributeName = getAttributeName(concreteMethod);

            ReportedMethodInfoBuilder attributeBuilder = methodInfoBuilders.get(attributeName);
            if (attributeBuilder == null) {
                attributeBuilder = new ReportedMethodInfoBuilder(bucketIdProvider).named(attributeName).onInstance(target);
            }

            attributeBuilder = attributeBuilder
                    .withConcreteGetter(concreteMethod)
                    .withAnnotatedGetter(annotatedMethod);

            methodInfoBuilders.put(attributeName, attributeBuilder);
        }

        for (ReportedMethodInfoBuilder methodInfoBuilder : methodInfoBuilders.values()) {
            ReportedMethodInfo methodInfo = methodInfoBuilder.build();
            attributes.addAll(methodInfo.attributes());
            prometheusAttributes.addAll(methodInfo.prometheusAttributes());
        }

        return new ReportedBean(attributes, prometheusAttributes);
    }
}
