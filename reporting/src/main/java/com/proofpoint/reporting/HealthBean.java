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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.reporting.ReflectionUtils.isValidGetter;

class HealthBean
{
    private final List<HealthBeanAttribute> attributes;

    public HealthBean(Collection<HealthBeanAttribute> attributes)
    {
        this.attributes = ImmutableList.copyOf(attributes);
    }

    public Collection<HealthBeanAttribute> getAttributes()
    {
        return attributes;
    }

    public static HealthBean forTarget(Object target)
    {
        checkNotNull(target, "target is null");

        List<HealthBeanAttribute> attributes = new ArrayList<>();

        for (Map.Entry<Method, Method> entry : AnnotationUtils.findAnnotatedMethods(target.getClass(), HealthCheck.class).entrySet()) {
            Method concreteMethod = entry.getKey();
            Method annotatedMethod = entry.getValue();

            if (!isValidGetter(concreteMethod)) {
                throw new RuntimeException("healthcheck annotation on non-getter " + annotatedMethod.toGenericString());
            }

            attributes.addAll(new HealthBeanAttributeBuilder()
                    .onInstance(target)
                    .withConcreteGetter(concreteMethod)
                    .withAnnotatedGetter(annotatedMethod)
                    .build());
        }

        for (Field field : AnnotationUtils.findAnnotatedFields(target.getClass(), HealthCheck.class)) {

        if (!AtomicReference.class.isAssignableFrom(field.getType())) {
            throw new RuntimeException("healthcheck annotation on non-AtomicReference field " + field.toGenericString());
        }

        attributes.addAll(new HealthBeanAttributeBuilder()
                .onInstance(target)
                .withField(field)
                .build());
        }

        return new HealthBean(attributes);
    }
}
