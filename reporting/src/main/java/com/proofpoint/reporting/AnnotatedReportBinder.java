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

public class AnnotatedReportBinder
    extends NamedReportBinder
{
    AnnotatedReportBinder(Mapping mapping)
    {
        super(mapping);
    }

    public NamedReportBinder annotatedWith(Annotation annotation)
    {
        StringBuilder builder = new StringBuilder().append(mapping.getKey().getTypeLiteral().getRawType().getSimpleName()).append(".");
        if (annotation instanceof Named named) {
            builder.append(named.value());
        }
        else {
            builder.append(annotation.annotationType().getSimpleName());
        }
        mapping.setNamePrefix(builder.toString());
        mapping.setKey(Key.get(mapping.getKey().getTypeLiteral(), annotation));
        return new NamedReportBinder(mapping);
    }

    public NamedReportBinder annotatedWith(Class<? extends Annotation> annotationClass)
    {
        mapping.setNamePrefix(mapping.getKey().getTypeLiteral().getRawType().getSimpleName() + "." + annotationClass.getSimpleName());
        mapping.setKey(Key.get(mapping.getKey().getTypeLiteral(), annotationClass));
        return new NamedReportBinder(mapping);
    }
}
