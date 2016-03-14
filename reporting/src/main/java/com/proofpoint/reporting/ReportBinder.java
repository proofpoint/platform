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
import com.google.inject.Key;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class ReportBinder
{
    private final Binder binder;
    private final Multibinder<Mapping> multibinder;

    private ReportBinder(Binder binder)
    {
        this.binder = checkNotNull(binder, "binder is null");
        this.multibinder = newSetBinder(binder, Mapping.class);
    }

    public static ReportBinder reportBinder(Binder binder) {
        return new ReportBinder(binder);
    }

    public AnnotatedReportBinder export(Class<?> clazz)
    {
        Mapping mapping = createMapping(Key.get(clazz));
        return new AnnotatedReportBinder(mapping);
    }

    public NamedReportBinder export(Key<?> key)
    {
        Mapping mapping = createMapping(key);
        return new NamedReportBinder(mapping);
    }

    public <T> ReportCollectionBinder<T> bindReportCollection(Class<T> iface) {
        return new ReportCollectionBinder<>(binder, iface);
    }

    private Mapping createMapping(Key<?> key)
    {
        String namePrefix;
        if (key.getAnnotation() != null) {
            if (key.getAnnotation() instanceof Named) {
                namePrefix = key.getTypeLiteral().getRawType().getSimpleName() + "." + ((Named) key.getAnnotation()).value();
            }
            else {
                namePrefix = key.getTypeLiteral().getRawType().getSimpleName() + "." + key.getAnnotation().annotationType().getSimpleName();
            }
        }
        else if (key.getAnnotationType() != null) {
            namePrefix = key.getTypeLiteral().getRawType().getSimpleName() + "." + key.getAnnotationType().getSimpleName();
        }
        else {
            namePrefix = key.getTypeLiteral().getRawType().getSimpleName();
        }
        Mapping mapping = new Mapping(key, namePrefix);
        multibinder.addBinding().toInstance(mapping);
        return mapping;
    }
}
