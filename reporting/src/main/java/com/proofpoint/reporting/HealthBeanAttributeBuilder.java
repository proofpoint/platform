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

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.reporting.ReflectionUtils.isValidGetter;

class HealthBeanAttributeBuilder
{
    private Object target;
    private Method concreteGetter;
    private Method annotatedGetter;

    public HealthBeanAttributeBuilder onInstance(Object target)
    {
        checkNotNull(target, "target is null");
        this.target = target;
        return this;
    }

    public HealthBeanAttributeBuilder withConcreteGetter(Method concreteGetter)
    {
        checkNotNull(concreteGetter, "concreteGetter is null");
        checkArgument(isValidGetter(concreteGetter), "Method is not a valid getter: " + concreteGetter);
        this.concreteGetter = concreteGetter;
        return this;
    }

    public HealthBeanAttributeBuilder withAnnotatedGetter(Method annotatedGetter)
    {
        checkNotNull(annotatedGetter, "annotatedGetter is null");
        checkArgument(isValidGetter(annotatedGetter), "Method is not a valid getter: " + annotatedGetter);
        this.annotatedGetter = annotatedGetter;
        return this;
    }

    public Collection<? extends HealthBeanAttribute> build()
    {
        checkArgument(target != null, "JmxAttribute must have a target object");

        if (AnnotationUtils.isFlatten(annotatedGetter) || AnnotationUtils.isNested(annotatedGetter)) {
            checkArgument(concreteGetter != null, "Nested/Flattened JmxAttribute must have a concrete getter");

            Object value = null;
            try {
                value = concreteGetter.invoke(target);
            }
            catch (Exception ignored) {
                // todo log me
            }
            if (value == null) {
                return Collections.emptySet();
            }

            return HealthBean.forTarget(value).getAttributes();
        }
        else {
            checkArgument (concreteGetter != null, "JmxAttribute must have a concrete getter");

            String description = annotatedGetter.getAnnotation(HealthCheck.class).value();

            return ImmutableList.of(new MethodHealthBeanAttribute(description, target, concreteGetter));
        }
    }
}
