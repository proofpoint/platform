/*
 * Copyright 2013 Proofpoint, Inc.
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

import com.google.inject.Key;
import com.google.inject.name.Named;

import java.lang.annotation.Annotation;

public class AnnotatedHealthBinder
    extends NamedHealthBinder
{
    protected final Class<?> clazz;

    AnnotatedHealthBinder(Class<?> clazz, HealthMapping mapping)
    {
        super(mapping);
        this.clazz = clazz;
    }

    /**
     * See the EDSL description at {@link HealthBinder}.
     */
    public NamedHealthBinder annotatedWith(Annotation annotation)
    {
        if (annotation instanceof Named named) {
            mapping.setNameSuffix(named.value());
        }
        else {
            mapping.setNameSuffix(annotation.annotationType().getSimpleName());
        }
        mapping.setKey(Key.get(clazz, annotation));
        return new NamedHealthBinder(mapping);
    }

    /**
     * See the EDSL description at {@link HealthBinder}.
     */
    public NamedHealthBinder annotatedWith(Class<? extends Annotation> annotationClass)
    {
        mapping.setNameSuffix(annotationClass.getSimpleName());
        mapping.setKey(Key.get(clazz, annotationClass));
        return new NamedHealthBinder(mapping);
    }
}
