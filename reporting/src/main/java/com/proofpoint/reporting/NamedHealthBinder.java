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
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;

import static com.proofpoint.reporting.HealthMapping.healthMapping;

public class NamedHealthBinder
{
    protected final Multibinder<HealthMapping> binder;
    protected final Key<?> key;

    NamedHealthBinder(Multibinder<HealthMapping> binder, Key<?> key)
    {
        this.binder = binder;
        this.key = key;
    }

    /**
     * Names the health check according to the annotation, if any.
     */
    public void withGeneratedName()
    {
        if (key.getAnnotation() != null) {
            if (key.getAnnotation() instanceof Named) {
                as(((Named) key.getAnnotation()).value());
            }
            else {
                as(key.getAnnotation().annotationType().getSimpleName());
            }
        }
        else if (key.getAnnotationType() != null) {
            as(key.getAnnotationType().getSimpleName());
        }
        else {
            binder.addBinding().toInstance(healthMapping(null, key));
        }
    }

    public void as(String name)
    {
        binder.addBinding().toInstance(healthMapping(name, key));
    }
}
