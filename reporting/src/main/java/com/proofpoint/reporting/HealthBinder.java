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

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static java.util.Objects.requireNonNull;

/**
 * Exports Guice-bound objects to the health subsystem.
 *
 * <h2>The Health Binding EDSL</h2>
 *
 * <pre>
 *     healthBinder(binder).export(Service.class);</pre>
 *
 * Exports the implementation that {@code Service} is bound to.
 *
 * <pre>
 *     healthBinder(binder).export(Service.class)
 *         .annotatedWith(Red.class);</pre>
 *
 * Exports the implementation that {@code Key.get(Service.class, Red.class)}
 * is bound to using the name suffix {@code Red}.
 *
 * <pre>
 *     healthBinder(binder).export(Service.class)
 *         .annotatedWith(Names.named("red"));</pre>
 *
 * Exports the implementation that {@code Key.get(Service.class, Names.named("red"))}
 * is bound to using the name suffix {@code red}.
 * The semantic of using the value of the annotation is specific to
 * {@link Named}; other annotation types use the annotation type in the
 * name suffix.
 *
 * <pre>
 *     healthBinder(binder).export(Key.get(Service.class, Names.named("red"));</pre>
 *
 * Exports the implementation that {@code Key.get(Service.class, Names.named("red"))}
 * is bound to using the name suffix {@code red}.
 * The semantic of using the value of the annotation is specific to
 * {@link Named}; other annotation types use the annotation type in the
 * name suffix.
 *
 * <pre>
 *     helthBinder(binder).export(Service.class)
 *         .withNameSuffix("suffix");</pre>
 *
 * Exports the implementation that {@code Service} is bound to using the
 * name suffix {@code suffix}.
 *
 * {@code .annotatedWith(...)} may be used before this.
 *
 */
public class HealthBinder
{
    private final Multibinder<HealthMapping> healthBinder;

    private HealthBinder(Binder binder)
    {
        binder = requireNonNull(binder, "binder is null").skipSources(getClass());
        this.healthBinder = newSetBinder(binder, HealthMapping.class);
    }

    /**
     * Creates a new {@link HealthBinder}. See the EDSL examples at {@link HealthBinder}.
     *
     * @param binder The Guice {@link Binder} to use.
     */
    public static HealthBinder healthBinder(Binder binder) {
        return new HealthBinder(binder);
    }

    /**
     * See the EDSL description at {@link HealthBinder}.
     */
    public AnnotatedHealthBinder export(Class<?> clazz)
    {
        HealthMapping mapping = new HealthMapping(null, Key.get(clazz));
        healthBinder.addBinding().toInstance(mapping);
        return new AnnotatedHealthBinder(clazz, mapping);
    }

    /**
     * See the EDSL description at {@link HealthBinder}.
     */
    public NamedHealthBinder export(Key<?> key)
    {
        HealthMapping mapping;
        if (key.getAnnotation() != null) {
            if (key.getAnnotation() instanceof Named) {
                mapping = new HealthMapping(((Named) key.getAnnotation()).value(), key);
            }
            else {
                mapping = new HealthMapping(key.getAnnotation().annotationType().getSimpleName(), key);
            }
        }
        else if (key.getAnnotationType() != null) {
            mapping = new HealthMapping(key.getAnnotationType().getSimpleName(), key);
        }
        else {
            mapping = new HealthMapping(null, key);
        }
        healthBinder.addBinding().toInstance(mapping);
        return new NamedHealthBinder(mapping);
    }
}
