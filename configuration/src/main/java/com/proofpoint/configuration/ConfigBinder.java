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

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.multibindings.Multibinder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static java.util.Objects.requireNonNull;

/**
 * Binds configuration classes.
 *
 * <h3>The ConfigBinder EDSL</h3>
 *
 * <pre>
 *     configBinder(binder).bind(FooConfig.class);</pre>
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
 *         .bind(FooConfig.class)
 *         .annotatedWith(Red.class);</pre>
 *
 * Binds the configuration class {@code Key.get(FooConfig.class, Red.class)}
 * to instances that are created by the configuration subsystem.
 *
 * <pre>
 *     configBinder(binder)
 *         .bind(FooConfig.class)
 *         .annotatedWith(Names.named("red"));</pre>
 *
 * Binds the configuration class {@code Key.get(FooConfig.class, Names.named("red"))}
 * to instances that are created by the configuration subsystem.
 *
 * <pre>
 *     configBinder(binder)
 *         .bind(FooConfig.class)
 *         .prefixedWith("prefix");</pre>
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

    private <T> void bindConfigDefaults(Key<T> key, ConfigDefaults<T> configDefaults)
    {
        createConfigDefaultsBinder(key).addBinding().toInstance(new ConfigDefaultsHolder<>(key, configDefaults));
    }

    private <T> Multibinder<ConfigDefaultsHolder<T>> createConfigDefaultsBinder(Key<T> key)
    {
        @SuppressWarnings("SerializableInnerClassWithNonSerializableOuterClass")
        Type type = new TypeToken<ConfigDefaultsHolder<T>>() {}
                .where(new TypeParameter<T>() {}, (TypeToken<T>) TypeToken.of(key.getTypeLiteral().getType()))
                .getType();

        TypeLiteral<ConfigDefaultsHolder<T>> typeLiteral = (TypeLiteral<ConfigDefaultsHolder<T>>) TypeLiteral.get(type);

        if (key.getAnnotation() == null) {
            return newSetBinder(binder, typeLiteral);
        }
        if (key.hasAttributes()) {
            return newSetBinder(binder, typeLiteral, key.getAnnotation());
        }
        return newSetBinder(binder, typeLiteral, key.getAnnotationType());
    }
}
