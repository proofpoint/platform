/*
 * Copyright 2010 Proofpoint, Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateBinder;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.google.inject.spi.Message;
import com.proofpoint.configuration.ConfigBinder.AnnotatedConfigBindingBuilder;
import com.proofpoint.configuration.ConfigBinder.PrefixConfigBindingBuilder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.inject.Inject;
import javax.inject.Qualifier;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.inject.name.Names.named;
import static com.proofpoint.configuration.ConfigBinder.bindConfig;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class TestConfig
{
    private ImmutableMap<String,String> properties;

    @BeforeMethod
    protected void setUp()
    {
        properties = ImmutableMap.<String, String>builder()
            .put("stringOption", "a string")
            .put("booleanOption", "true")
            .put("boxedBooleanOption", "true")
            .put("byteOption", Byte.toString(Byte.MAX_VALUE))
            .put("boxedByteOption", Byte.toString(Byte.MAX_VALUE))
            .put("shortOption", Short.toString(Short.MAX_VALUE))
            .put("boxedShortOption", Short.toString(Short.MAX_VALUE))
            .put("integerOption", Integer.toString(Integer.MAX_VALUE))
            .put("boxedIntegerOption", Integer.toString(Integer.MAX_VALUE))
            .put("longOption", Long.toString(Long.MAX_VALUE))
            .put("boxedLongOption", Long.toString(Long.MAX_VALUE))
            .put("floatOption", Float.toString(Float.MAX_VALUE))
            .put("boxedFloatOption", Float.toString(Float.MAX_VALUE))
            .put("doubleOption", Double.toString(Double.MAX_VALUE))
            .put("boxedDoubleOption", Double.toString(Double.MAX_VALUE))
            .put("myEnumOption", MyEnum.FOO.toString())
            .put("valueClassOption", "a value class")
            .build();
    }

    @Test(dataProvider = "bindArguments")
    public void testConfig(String prefix, Class<? extends Annotation> annotationClass, Annotation annotation)
    {
        Injector injector = createInjector(prefix == null ? properties : prefix(properties), createModule(Config1.class, prefix, annotationClass, annotation));
        verifyConfig(injector.getInstance(getKey(Config1.class, annotationClass, annotation)));
    }

    @Test(dataProvider = "bindArguments")
    public void testConfigMapSimple(String prefix, Class<? extends Annotation> annotationClass, Annotation annotation)
    {
        Map<String,String> properties = ImmutableMap.<String, String>builder()
                    .put("map.key1", "value1")
                    .put("map.key2", "value2")
                    .build();
        Injector injector = createInjector(prefix == null ? properties : prefix(properties), createModule(ConfigMapSimple.class, prefix, annotationClass, annotation));
        ConfigMapSimple mapSimple = injector.getInstance(getKey(ConfigMapSimple.class, annotationClass, annotation));
        assertThat(mapSimple.getMap()).isEqualTo(ImmutableMap.of("key1", "value1", "key2", "value2"));
    }

    @Test(dataProvider = "bindArguments")
    public void testConfigMapComplex(String prefix, Class<? extends Annotation> annotationClass, Annotation annotation)
    {
        ImmutableSet<Integer> keys = ImmutableSet.of(1, 2, 3, 5, 8);
        Builder<String, String> builder = ImmutableMap.builder();
        for (Integer key : keys) {
            for (Entry<String, String> entry : properties.entrySet()) {
                builder.put("map." + key + "." + entry.getKey(), entry.getValue());
            }
        }
        ImmutableMap<String, String> properties = builder.build();
        Injector injector = createInjector(prefix == null ? properties : prefix(properties), createModule(ConfigMapComplex.class, prefix, annotationClass, annotation));
        ConfigMapComplex mapComplex = injector.getInstance(getKey(ConfigMapComplex.class, annotationClass, annotation));
        assertThat(mapComplex.getMap().keySet()).isEqualTo(keys);
        for (Config1 config1 : mapComplex.getMap().values()) {
            verifyConfig(config1);
        }
    }

    @Test
    public void testPrivateBinder()
    {
        Module module = binder -> {
            PrivateBinder privateBinder = binder.newPrivateBinder();
            privateBinder.install(createModule(Config1.class, null, null, null));
            privateBinder.bind(ExposeConfig.class).in(Scopes.SINGLETON);
            privateBinder.expose(ExposeConfig.class);
        };
        Injector injector = createInjector(properties, module);
        verifyConfig(injector.getInstance(ExposeConfig.class).config1);
    }

    @Test
    public void testPrivateBinderDifferentPrefix()
    {
        Module module = binder -> {
            PrivateBinder privateBinder = binder.newPrivateBinder();
            privateBinder.install(createModule(Config1.class, null, null, null));
            privateBinder.bind(ExposeConfig.class).annotatedWith(named("no-prefix")).to(ExposeConfig.class).in(Scopes.SINGLETON);
            privateBinder.expose(Key.get(ExposeConfig.class, named("no-prefix")));

            privateBinder = binder.newPrivateBinder();
            privateBinder.install(createModule(Config1.class, "prefix", null, null));
            privateBinder.bind(ExposeConfig.class).annotatedWith(named("prefix")).to(ExposeConfig.class).in(Scopes.SINGLETON);
            privateBinder.expose(Key.get(ExposeConfig.class, named("prefix")));
        };
        properties = ImmutableMap.<String, String>builder()
                .putAll(properties)
                .put("prefix.stringOption", "a prefix string")
                .build();
        Injector injector = createInjector(properties, module);
        verifyConfig(injector.getInstance(Key.get(ExposeConfig.class, named("no-prefix"))).config1);
        assertThat(injector.getInstance(Key.get(ExposeConfig.class, named("prefix"))).config1.getStringOption()).isEqualTo("a prefix string");
    }

    private static void verifyConfig(Config1 config)
    {
        assertThat(config.getStringOption()).isEqualTo("a string");
        assertThat(config.getBooleanOption()).isTrue();
        assertThat(config.getBoxedBooleanOption()).isTrue();
        assertThat(config.getByteOption()).isEqualTo(Byte.MAX_VALUE);
        assertThat(config.getBoxedByteOption()).isEqualTo(Byte.valueOf(Byte.MAX_VALUE));
        assertThat(config.getShortOption()).isEqualTo(Short.MAX_VALUE);
        assertThat(config.getBoxedShortOption()).isEqualTo(Short.valueOf(Short.MAX_VALUE));
        assertThat(config.getIntegerOption()).isEqualTo(Integer.MAX_VALUE);
        assertThat(config.getBoxedIntegerOption()).isEqualTo(Integer.valueOf(Integer.MAX_VALUE));
        assertThat(config.getLongOption()).isEqualTo(Long.MAX_VALUE);
        assertThat(config.getBoxedLongOption()).isEqualTo(Long.valueOf(Long.MAX_VALUE));
        assertThat(config.getFloatOption()).isEqualTo(Float.MAX_VALUE);
        assertThat(config.getBoxedFloatOption()).isEqualTo(Float.MAX_VALUE);
        assertThat(config.getDoubleOption()).isEqualTo(Double.MAX_VALUE);
        assertThat(config.getBoxedDoubleOption()).isEqualTo(Double.MAX_VALUE);
        assertThat(config.getMyEnumOption()).isEqualTo(MyEnum.FOO);
        assertThat(config.getValueClassOption().getValue()).isEqualTo("a value class");
    }

    @Test(expectedExceptions = CreationException.class)
    public void testDetectsNoConfigAnnotations()
    {
        Injector injector = createInjector(Collections.emptyMap(), createModule(ConfigWithNoAnnotations.class, null, null, null));
        injector.getInstance(ConfigWithNoAnnotations.class);
    }

    @DataProvider(name = "bindArguments")
    @SuppressWarnings("unchecked")
    public Object[][] bindArguments()
    {
        return Sets.cartesianProduct(
                Sets.newHashSet(asList(new String[] {null}), asList("prefix")),
                Sets.newHashSet(
                        asList(null, null),
                        asList(ForConfigTest.class, null),
                        asList(null, Names.named("ConfigTest"))
                )
        )
                .stream()
                .map(l -> l.stream().flatMap(Collection::stream).toArray())
                .toArray(Object[][]::new);
    }

    private static Injector createInjector(Map<String, String> properties, Module module)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        List<Message> messages = new ConfigurationValidator(configurationFactory).validate(module);
        return Guice.createInjector(new ConfigurationModule(configurationFactory), module, new ValidationErrorModule(messages));
    }

    private static <T> Module createModule(Class<T> configClass, String prefix, Class<? extends Annotation> annotationClass, Annotation annotation)
    {
        return binder -> {
            AnnotatedConfigBindingBuilder<T> annotatedBuilder = bindConfig(binder).bind(configClass);
            PrefixConfigBindingBuilder builder;
            if (annotationClass != null) {
                builder = annotatedBuilder.annotatedWith(annotationClass);
            }
            else if (annotation != null) {
                builder = annotatedBuilder.annotatedWith(annotation);
            }
            else {
                builder = annotatedBuilder;
            }
            if (prefix != null) {
                builder.prefixedWith(prefix);
            }
        };
    }

    private static <T> Key<T> getKey(Class<T> type, Class<? extends Annotation> annotationClass, Annotation annotation)
    {
        Key<T> key;
        if (annotationClass != null) {
            key = Key.get(type, annotationClass);
        }
        else if (annotation != null) {
            key = Key.get(type, annotation);
        }
        else {
            key = Key.get(type);
        }
        return key;
    }

    private static Map<String, String> prefix(Map<String, String> properties)
    {
        Builder<String, String> builder = ImmutableMap.builder();
        for (Entry<String, String> entry : properties.entrySet()) {
            builder.put("prefix." + entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private static class ExposeConfig
    {
        final Config1 config1;

        @Inject
        private ExposeConfig(Config1 config1)
        {
            this.config1 = config1;
        }
    }

    @Retention(RUNTIME)
    @Target(PARAMETER)
    @Qualifier
    @interface ForConfigTest
    {
    }
}