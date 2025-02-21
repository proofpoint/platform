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
 * Exports Guice-bound objects to the reporting subsystem.
 *
 * <h2>The Report Binding EDSL</h2>
 *
 * <pre>
 *     reportBinder(binder).export(Service.class);</pre>
 *
 * Exports the implementation that {@code Service} is bound to using the
 * metric name prefix {@code Service} and no tags.
 *
 * <pre>
 *     reportBinder(binder).export(Service.class)
 *         .annotatedWith(Red.class);</pre>
 *
 * Exports the implementation that {@code Key.get(Service.class, Red.class)}
 * is bound to using the metric name prefix {@code Service.Red}.
 *
 * <pre>
 *     reportBinder(binder).export(Service.class)
 *         .annotatedWith(Names.named("red"));</pre>
 *
 * Exports the implementation that {@code Key.get(Service.class, Names.named("red"))}
 * is bound to using the metric name prefix {@code Service.Red}.
 * The semantic of using the value of the annotation is specific to
 * {@link Named}; other annotation types use the annotation type in the metric
 * name prefix.
 *
 * <pre>
 *     reportBinder(binder).export(Key.get(Service.class, Names.named("red"));</pre>
 *
 * Exports the implementation that {@code Key.get(Service.class, Names.named("red"))}
 * is bound to using the metric name prefix {@code Service.Red}.
 * The semantic of using the value of the annotation is specific to
 * {@link Named}; other annotation types use the annotation type in the metric
 * name prefix.
 *
 * <pre>
 *     reportBinder(binder).export(Service.class)
 *         .withApplicationPrefix();</pre>
 *
 * Given the application is named {@code application-name}, exports the
 * implementation that {@code Service} is bound to using the
 * metric name prefix {@code ApplicationName.Service}.
 *
 * {@code .annotatedWith(...)} may be used before this and subsequent methods.
 *
 * <pre>
 *     reportBinder(binder).export(Service.class)
 *         .withNamePrefix("Name");</pre>
 *
 * Exports the implementation that {@code Service} is bound to using the
 * metric name prefix {@code Name}.
 *
 * <pre>
 *     reportBinder(binder).export(Service.class)
 *         .withApplicationPrefix()
 *         .withNamePrefix("Name");</pre>
 *
 * .withApplicationPrefix() may be used before .withNamePrefix(String); this
 * exports using the metric name prefix {@code ApplicationName.Name}.
 *
 * <pre>
 *     reportBinder(binder).export(Service.class)
 *         .withTags(ImmutableMap.of("tag", "value"));</pre>
 *
 * Adds tags and their corresponding values to the metric being reported.
 * The previous methods may be used before this.
 *
 * <pre>
 *     reportBinder(binder).bindReportCollection(StatsInterface.class);</pre>
 *
 * Binds the report collection interface {@code StatsInterface.class} to an
 * implementation that reports metrics using the
 * metric name prefix {@code StatsInterface} and no tags.
 *
 * <pre>
 *     reportBinder(binder).bindReportCollection(StatsInterface.class)
 *         .annotatedWith(Red.class);</pre>
 *
 * Binds the report collection interface
 * {@code Key.get(StatsInterface.class, Red.class)}
 * to an implementation that reports metrics using the
 * metric name prefix {@code StatsInterface} and no tags.
 *
 * <pre>
 *     reportBinder(binder).bindReportCollection(StatsInterface.class)
 *         .annotatedWith(Names.named("red"));</pre>
 *
 * Binds the report collection interface
 * {@code Key.get(StatsInterface.class, Names.named("red"))}
 * to an implementation that reports metrics using the
 * metric name prefix {@code StatsInterface} and no tags.
 *
 * <pre>
 *     reportBinder(binder).bindReportCollection(StatsInterface.class)
 *         .withApplicationPrefix();</pre>
 *
 * Given the application is named {@code application-name}, binds the report
 * collection interface {@code StatsInterface.class} to an implementation that
 * reports metrics using the metric name prefix
 * {@code ApplicationName.Service}.
 *
 * {@code .annotatedWith(...)} may be used before this and subsequent methods.
 *
 * <pre>
 *     reportBinder(binder).bindReportCollection(StatsInterface.class)
 *         .withNamePrefix("Name");</pre>
 *
 * Binds the report collection interface {@code StatsInterface.class} to an
 * implementation that reports metrics using the
 * metric name prefix {@code Name}.
 *
 * <pre>
 *     reportBinder(binder).bindReportCollection(StatsInterface.class)
 *         .withApplicationPrefix()
 *         .withNamePrefix("Name");</pre>
 *
 * .withApplicationPrefix() may be used before .withNamePrefix(String); this
 * reports using the metric name prefix @code{ApplicationName.Name}.
 *
 * <pre>
 *     reportBinder(binder).bindReportCollection(StatsInterface.class)
 *         .withTags(ImmutableMap.of("tag", "value"));</pre>
 *
 * Adds tags and their corresponding values to the metrics being reported.
 * The previous methods may be used before this.
 *
 */
public class ReportBinder
{
    private final Binder binder;
    private final Multibinder<Mapping> multibinder;

    private ReportBinder(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder is null").skipSources(getClass());
        this.multibinder = newSetBinder(binder, Mapping.class);
    }

    /**
     * Creates a new {@link ReportBinder}. See the EDSL examples at {@link ReportBinder}.
     *
     * @param binder The Guice {@link Binder} to use.
     */
    public static ReportBinder reportBinder(Binder binder) {
        return new ReportBinder(binder);
    }

    /**
     * See the EDSL description at {@link ReportBinder}.
     */
    public AnnotatedReportBinder export(Class<?> clazz)
    {
        Mapping mapping = createMapping(Key.get(clazz));
        return new AnnotatedReportBinder(mapping);
    }

    /**
     * See the EDSL description at {@link ReportBinder}.
     */
    public NamedReportBinder export(Key<?> key)
    {
        Mapping mapping = createMapping(key);
        return new NamedReportBinder(mapping);
    }

    /**
     * See the EDSL description at {@link ReportBinder}.
     */
    public <T> ReportCollectionBinder<T> bindReportCollection(Class<T> iface) {
        return new ReportCollectionBinder<>(binder, iface);
    }

    private Mapping createMapping(Key<?> key)
    {
        String namePrefix;
        if (key.getAnnotation() != null) {
            if (key.getAnnotation() instanceof Named named) {
                namePrefix = key.getTypeLiteral().getRawType().getSimpleName() + "." + named.value();
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
