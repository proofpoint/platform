/*
 * Copyright 2018 Proofpoint, Inc.
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
package com.proofpoint.configuration;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.binder.AnnotatedBindingBuilder;

import java.lang.annotation.Annotation;

import static java.util.Objects.requireNonNull;

/**
 * Binds configuration classes.
 *
 * <h2>The ConfigBinder EDSL</h2>
 *
 * <pre>
 *     configBinder(binder).bind(FooConfig.class);</pre>
 * <p>
 * Binds the configuration class {@code FooConfig} to instances that are
 * created by the configuration subsystem (specifically its
 * {@link ConfigurationFactory}).
 * <p>
 * As configuration classes are mutable, the binding is with default scope
 * so each injection point gets a separate instance.
 * <p>
 * Does not work with private binders.
 *
 * <pre>
 *     configBinder(binder)
 *         .bind(FooConfig.class)
 *         .annotatedWith(Red.class);</pre>
 * <p>
 * Binds the configuration class {@code Key.get(FooConfig.class, Red.class)}
 * to instances that are created by the configuration subsystem.
 *
 * <pre>
 *     configBinder(binder)
 *         .bind(FooConfig.class)
 *         .annotatedWith(Names.named("red"));</pre>
 * <p>
 * Binds the configuration class {@code Key.get(FooConfig.class, Names.named("red"))}
 * to instances that are created by the configuration subsystem.
 *
 * <pre>
 *     configBinder(binder)
 *         .bind(FooConfig.class)
 *         .prefixedWith("prefix");</pre>
 * <p>
 * The {@code .prefixedWith()} method causes the configuration properties that
 * the bound configuration instances consume from to be prefixed by the
 * specified parameter, followed by a ".". For example, if an attribute of the
 * configuration class consumes the "size" configuration property, that
 * attribute of the bound instance will instead consume the "prefix.size"
 * configuration property.
 * <p>
 * May be combined with {@code .annotatedWith()}.
 */
public final class ConfigBinder
{
    private final Binder binder;

    private ConfigBinder(Binder binder)
    {
        this.binder = binder.skipSources(ConfigBinder.class);
    }

    /**
     * See the EDSL description at {@link ConfigBinder}.
     *
     * @param binder The Guice {@link Binder} to use.
     */
    public static ConfigBinder bindConfig(Binder binder)
    {
        return new ConfigBinder(binder);
    }

    /**
     * See the EDSL description at {@link ConfigBinder}.
     */
    public <T> AnnotatedConfigBindingBuilder<T> bind(Class<T> configClass)
    {
        ConfigurationProvider<T> configurationProvider = new ConfigurationProvider<>(configClass);
        AnnotatedBindingBuilder<T> builder = binder.bind(configClass);
        builder.toProvider(configurationProvider);
        return new AnnotatedConfigBindingBuilder<>(builder, configurationProvider);
    }

    public final static class AnnotatedConfigBindingBuilder<T>
            extends PrefixConfigBindingBuilder<T>
    {
        private final AnnotatedBindingBuilder<T> builder;

        private AnnotatedConfigBindingBuilder(AnnotatedBindingBuilder<T> builder, ConfigurationProvider<T> configurationProvider)
        {
            super(configurationProvider);
            this.builder = builder;
        }

        /**
         * See the EDSL description at {@link ConfigBinder}.
         */
        public PrefixConfigBindingBuilder annotatedWith(Class<? extends Annotation> annotationType)
        {
            requireNonNull(annotationType, "annotationType is null");
            builder.annotatedWith(annotationType);
            configurationProvider.setKey(Key.get(configurationProvider.getConfigClass(), annotationType));
            return this;
        }

        /**
         * See the EDSL description at {@link ConfigBinder}.
         */
        public PrefixConfigBindingBuilder annotatedWith(Annotation annotation)
        {
            requireNonNull(annotation, "annotation is null");
            builder.annotatedWith(annotation);
            configurationProvider.setKey(Key.get(configurationProvider.getConfigClass(), annotation));
            return this;
        }
    }

    public static class PrefixConfigBindingBuilder<T>
    {
        final ConfigurationProvider<T> configurationProvider;

        private PrefixConfigBindingBuilder(ConfigurationProvider<T> configurationProvider)
        {
            this.configurationProvider = configurationProvider;
        }

        /**
         * See the EDSL description at {@link ConfigBinder}.
         */
        public void prefixedWith(String prefix)
        {
            requireNonNull(prefix, "prefix is null");
            configurationProvider.setPrefix(prefix);
        }
    }
}
