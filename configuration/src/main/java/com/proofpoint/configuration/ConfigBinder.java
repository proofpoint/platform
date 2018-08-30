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

import java.lang.annotation.Annotation;

/**
 * Binds configuration classes.
 *
 * <h3>The ConfigBinder EDSL</h3>
 *
 * <pre>
 *     configBinder(binder).to(FooConfig.class);</pre>
 *
 * Binds the configuration class {@code FooConfig} to instances that are
 * created by the configuration subsystem (specifically its
 * {@link ConfigurationFactory}).
 *
 * As configuration classes are mutable, the binding is with default scope
 * so each injection point gets a separate instance.
 *
 * Does not work with private binders.
 *
 * <pre>
 *     configBinder(binder)
 *         .annotatedWith(Red.class)
 *         .to(FooConfig.class);</pre>
 *
 * Binds the configuration class {@code Key.get(FooConfig.class, Red.class)}
 * to instances that are created by the configuration subsystem.
 *
 * <pre>
 *     configBinder(binder)
 *         .annotatedWith(Names.named("red"))
 *         .to(FooConfig.class);</pre>
 *
 * Binds the configuration class {@code Key.get(FooConfig.class, Names.named("red"))}
 * to instances that are created by the configuration subsystem.
 *
 * <pre>
 *     configBinder(binder)
 *         .prefixedWith("prefix")
 *         .to(FooConfig.class);</pre>
 *
 * The {@code .prefixedWith()} method causes the configuration properties that
 * the bound configuration instances consume from to be prefixed by the
 * specified parameter, followed by a ".". For example, if an attribute of the
 * configuration class consumes the "size" configuration property, that
 * attribute of the bound instance will instead consume the "prefix.size"
 * configuration property.
 *
 * May be combined with {@code .annotatedWith()}.
 *
 */
public class ConfigBinder
{
    private ConfigBinder()
    {
    }

    /**
     * See the EDSL description at {@link ConfigBinder}.
     *
     * @param binder The Guice {@link Binder} to use.
     */
    public static AnnotatedBindingBuilder bindConfig(Binder binder)
    {
        return new AnnotatedBindingBuilder(binder.skipSources(ConfigBinder.class));
    }

    public static class AnnotatedBindingBuilder extends PrefixBindingBuilder
    {
        private AnnotatedBindingBuilder(Binder binder)
        {
            super(binder, null, null);
        }

        /**
         * See the EDSL description at {@link ConfigBinder}.
         */
        public PrefixBindingBuilder annotatedWith(Class<? extends Annotation> annotationType)
        {
            return new PrefixBindingBuilder(binder, annotationType, null);
        }

        /**
         * See the EDSL description at {@link ConfigBinder}.
         */
        public PrefixBindingBuilder annotatedWith(Annotation annotation)
        {
            return new PrefixBindingBuilder(binder, null, annotation);
        }
    }

    public static class PrefixBindingBuilder extends ConfigBindingBuilder
    {
        PrefixBindingBuilder(Binder binder, Class<? extends Annotation> annotationType, Annotation annotation)
        {
            super(binder, annotationType, annotation, null);
        }

        /**
         * See the EDSL description at {@link ConfigBinder}.
         */
        public ConfigBindingBuilder prefixedWith(String prefix)
        {
            return new ConfigBindingBuilder(binder, annotationType, annotation, prefix);
        }
    }

    public static class ConfigBindingBuilder
    {
        protected final Binder binder;
        protected final Class<? extends Annotation> annotationType;
        protected final Annotation annotation;
        protected final String prefix;

        ConfigBindingBuilder(Binder binder, Class<? extends Annotation> annotationType, Annotation annotation, String prefix)
        {
            this.binder = binder;
            this.annotationType = annotationType;
            this.annotation = annotation;
            this.prefix = prefix;
        }

        /**
         * See the EDSL description at {@link ConfigBinder}.
         */
        public <T> void to(Class<T> configClass) {
            Key<T> key;
            if (annotationType != null) {
                key = Key.get(configClass, annotationType);
            } else if(annotation != null) {
                key = Key.get(configClass, annotation);
            } else {
                key = Key.get(configClass);
            }

            ConfigurationProvider<T> configurationProvider = new ConfigurationProvider<>(key, configClass, prefix);
            binder.bind(key).toProvider(configurationProvider);
        }
    }
}
