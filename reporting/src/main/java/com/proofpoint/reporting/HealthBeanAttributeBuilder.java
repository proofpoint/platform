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
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.proofpoint.reporting.FieldHealthBeanAttribute.fieldHealthBeanAttribute;
import static com.proofpoint.reporting.MethodHealthBeanAttribute.methodHealthBeanAttribute;
import static com.proofpoint.reporting.ReflectionUtils.isValidGetter;
import static java.util.Objects.requireNonNull;

class HealthBeanAttributeBuilder
{
    private Object target;
    private Method concreteGetter;
    private Method annotatedGetter;
    private Field field;

    HealthBeanAttributeBuilder onInstance(Object target)
    {
        this.target = requireNonNull(target, "target is null");
        return this;
    }

    HealthBeanAttributeBuilder withConcreteGetter(Method concreteGetter)
    {
        requireNonNull(concreteGetter, "concreteGetter is null");
        checkArgument(isValidGetter(concreteGetter), "Method is not a valid getter: " + concreteGetter);
        this.concreteGetter = concreteGetter;
        return this;
    }

    HealthBeanAttributeBuilder withAnnotatedGetter(Method annotatedGetter)
    {
        requireNonNull(annotatedGetter, "annotatedGetter is null");
        checkArgument(isValidGetter(annotatedGetter), "Method is not a valid getter: " + annotatedGetter);
        this.annotatedGetter = annotatedGetter;
        return this;
    }

    HealthBeanAttributeBuilder withField(Field field)
    {
        requireNonNull(field, "field is null");
        checkArgument(AtomicReference.class.isAssignableFrom(field.getType()), "Field is not an AtomicReference: " + field);
        this.field = field;
        return this;
    }

    Collection<? extends HealthBeanAttribute> build()
    {
        checkArgument(target != null, "HealthBeanAttribute must have a target object");

        if (field != null) {
            String description;
            boolean isRemoveFromRotation;
            HealthCheckRemoveFromRotation removeFromRotation = field.getAnnotation(HealthCheckRemoveFromRotation.class);
            if (removeFromRotation != null) {
                if (field.getAnnotation(HealthCheck.class) != null) {
                    throw new RuntimeException("field " + field + " cannot have both @HealthCheck and @HealthCheckRemoveFromRotation annotations");
                }
                description = removeFromRotation.value();
                isRemoveFromRotation = true;
            }
            else {
                description = field.getAnnotation(HealthCheck.class).value();
                isRemoveFromRotation = false;
            }

            return ImmutableList.of(fieldHealthBeanAttribute(description, isRemoveFromRotation, target, field));
        }
        else if (AnnotationUtils.isFlatten(annotatedGetter) || AnnotationUtils.isNested(annotatedGetter)) {
            checkArgument(concreteGetter != null, "Nested/Flattened HealthBeanAttribute must have a concrete getter");

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
            checkArgument (concreteGetter != null, "HealthBeanAttribute must have a concrete getter");

            String description;
            boolean isRemoveFromRotation;
            HealthCheckRemoveFromRotation removeFromRotation = annotatedGetter.getAnnotation(HealthCheckRemoveFromRotation.class);
            if (removeFromRotation != null) {
                if (annotatedGetter.getAnnotation(HealthCheck.class) != null) {
                    throw new RuntimeException("Method " + annotatedGetter + " cannot have both @HealthCheck and @HealthCheckRemoveFromRotation annotations");
                }
                description = removeFromRotation.value();
                isRemoveFromRotation = true;
            }
            else {
                description = annotatedGetter.getAnnotation(HealthCheck.class).value();
                isRemoveFromRotation = false;
            }

            return ImmutableList.of(methodHealthBeanAttribute(description, isRemoveFromRotation, target, concreteGetter));
        }
    }
}
