/*
 * Copyright 2016 Proofpoint, Inc.
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

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;

public class NamedReportCollectionBinder<T>
{
    private final Binder binder;
    private final Class<T> iface;
    private final com.google.inject.Key<? extends T> key;

    NamedReportCollectionBinder(Binder binder, Class<T> iface, Key<? extends T> key)
    {
        this.binder = binder;
        this.iface = iface;
        this.key = key;
    }

    public void as(String name)
    {
        AnnotatedBindingBuilder<T> bindingBuilder = binder.bind(iface);

        LinkedBindingBuilder<T> annotatedWith;
        if (key.getAnnotation() == null) {
            annotatedWith = bindingBuilder.annotatedWith(key.getAnnotationType());
        }
        else {
            annotatedWith = bindingBuilder.annotatedWith(key.getAnnotation());
        }

        annotatedWith.toProvider(new ReportCollectionProvider<>(iface, name)).in(Scopes.SINGLETON);
    }
}
