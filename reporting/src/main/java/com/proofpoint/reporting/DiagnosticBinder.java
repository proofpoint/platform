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

import com.google.common.annotations.Beta;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static java.util.Objects.requireNonNull;

/**
 * Exports Guice-bound objects to the diagnostic subsystem.
 *
 * <h3>The Diagnostic Binding EDSL</h3>
 *
 * <pre>
 *     diagnosticBinder(binder).export(Service.class);</pre>
 *
 * Exports the implementation that {@code Service} is bound to using the
 * metric name prefix {@code Service} and no tags.
 *
 * <pre>
 *     diagnosticBinder(binder).export(Service.class)
 *         .annotatedWith(Red.class);</pre>
 *
 * Exports the implementation that {@code Key.get(Service.class, Red.class)}
 * is bound to using the metric name prefix {@code Service.Red}.
 *
 * <pre>
 *     diagnosticBinder(binder).export(Service.class)
 *         .annotatedWith(Names.named("red"));</pre>
 *
 * Exports the implementation that {@code Key.get(Service.class, Names.named("red"))}
 * is bound to using the metric name prefix {@code Service.Red}.
 * The semantic of using the value of the annotation is specific to
 * {@link Named}; other annotation types use the annotation type in the metric
 * name prefix.
 *
 * <pre>
 *     diagnosticBinder(binder).export(Key.get(Service.class, Names.named("red"));</pre>
 *
 * Exports the implementation that {@code Key.get(Service.class, Names.named("red"))}
 * is bound to using the metric name prefix {@code Service.Red}.
 * The semantic of using the value of the annotation is specific to
 * {@link Named}; other annotation types use the annotation type in the metric
 * name prefix.
 *
 * <pre>
 *     diagnosticBinder(binder).export(Service.class)
 *         .withNamePrefix("Name");</pre>
 *
 * Exports the implementation that {@code Service} is bound to using the
 * metric name prefix {@code Name}.
 *
 * {@code .annotatedWith(...)} may be used before this
 *
 */
@Beta
public class DiagnosticBinder
{
    private final Multibinder<DiagnosticMapping> multibinder;

    private DiagnosticBinder(Binder binder)
    {
        this.multibinder = newSetBinder(binder, DiagnosticMapping.class);
    }

    /**
     * Creates a new {@link DiagnosticBinder}. See the EDSL examples at {@link DiagnosticBinder}.
     *
     * @param binder The Guice {@link Binder} to use.
     */
    public static DiagnosticBinder diagnosticBinder(Binder binder) {
        return new DiagnosticBinder(binder);
    }

    /**
     * See the EDSL description at {@link DiagnosticBinder}.
     */
    public AnnotatedDiagnosticBinder export(Class<?> clazz)
    {
        DiagnosticMapping mapping = createMapping(Key.get(clazz));
        return new AnnotatedDiagnosticBinder(mapping);
    }

    /**
     * See the EDSL description at {@link DiagnosticBinder}.
     */
    public NamedDiagnosticBinder export(Key<?> key)
    {
        DiagnosticMapping mapping = createMapping(key);
        return new NamedDiagnosticBinder(mapping);
    }

    private DiagnosticMapping createMapping(Key<?> key)
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
        DiagnosticMapping mapping = new DiagnosticMapping(key, namePrefix);
        multibinder.addBinding().toInstance(mapping);
        return mapping;
    }
}
