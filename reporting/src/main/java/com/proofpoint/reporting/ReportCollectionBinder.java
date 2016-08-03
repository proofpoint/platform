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

import com.google.inject.Binder;
import com.google.inject.binder.AnnotatedBindingBuilder;

import java.lang.annotation.Annotation;

import static com.google.inject.Scopes.SINGLETON;
import static java.util.Objects.requireNonNull;

public class ReportCollectionBinder<T>
    extends NamedReportCollectionBinder<T>
{
    private final AnnotatedBindingBuilder<T> bindingBuilder;

    ReportCollectionBinder(Binder binder, Class<T> iface)
    {
        super(new ReportCollectionProvider<>(iface));
        binder = requireNonNull(binder, "binder is null").skipSources(getClass());
        bindingBuilder = binder.bind(iface);
        bindingBuilder.toProvider(provider).in(SINGLETON);
    }

    /**
     * @deprecated No longer necessary.
     */
    @Deprecated
    public void withGeneratedName()
    {
    }

    public NamedReportCollectionBinder<T> annotatedWith(Annotation annotation)
    {
        bindingBuilder.annotatedWith(annotation);
        return new NamedReportCollectionBinder<>(provider);
    }

    public NamedReportCollectionBinder<T> annotatedWith(Class<? extends Annotation> annotationClass)
    {
        bindingBuilder.annotatedWith(annotationClass);
        return new NamedReportCollectionBinder<>(provider);
    }
}
