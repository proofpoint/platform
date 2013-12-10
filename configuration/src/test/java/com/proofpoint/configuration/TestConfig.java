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
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateBinder;
import com.google.inject.Scopes;
import com.google.inject.spi.Message;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.inject.name.Names.named;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

 public class TestConfig
{
    private ImmutableMap<String,String> properties;

    @Test
    public void testConfig()
    {
        Injector injector = createInjector(properties, createModule(Config1.class, null));
        verifyConfig(injector.getInstance(Config1.class));
    }

    @Test
    public void testPrefixConfigTypes()
    {
        Injector injector = createInjector(prefix("prefix", properties), createModule(Config1.class, "prefix"));
        verifyConfig(injector.getInstance(Config1.class));
    }

    @Test
    public void testConfigMapSimple()
    {
        Map<String,String> properties = ImmutableMap.<String, String>builder()
                    .put("map.key1", "value1")
                    .put("map.key2", "value2")
                    .build();
        Injector injector = createInjector(properties, createModule(ConfigMapSimple.class, null));
        final ConfigMapSimple mapSimple = injector.getInstance(ConfigMapSimple.class);
        assertEquals(mapSimple.getMap(), ImmutableMap.of("key1", "value1", "key2", "value2"));
    }

    @Test
    public void testConfigMapComplex()
    {
        final ImmutableSet<Integer> keys = ImmutableSet.of(1, 2, 3, 5, 8);
        final Builder<String, String> builder = ImmutableMap.builder();
        for (Integer key : keys) {
            for (Entry<String, String> entry : properties.entrySet()) {
                builder.put("map." + key + "." + entry.getKey(), entry.getValue());
            }
        }
        Injector injector = createInjector(builder.build(), createModule(ConfigMapComplex.class, null));
        final ConfigMapComplex mapComplex = injector.getInstance(ConfigMapComplex.class);
        assertEquals(mapComplex.getMap().keySet(), keys);
        for (Config1 config1 : mapComplex.getMap().values()) {
            verifyConfig(config1);
        }
    }

    @Test
    public void testPrivateBinder()
    {
        Module module = new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                PrivateBinder privateBinder = binder.newPrivateBinder();
                privateBinder.install(createModule(Config1.class, null));
                privateBinder.bind(ExposeConfig.class).in(Scopes.SINGLETON);
                privateBinder.expose(ExposeConfig.class);
            }
        };
        Injector injector = createInjector(properties, module);
        verifyConfig(injector.getInstance(ExposeConfig.class).config1);
    }

    @Test
    public void testPrivateBinderDifferentPrefix()
    {
        Module module = new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                PrivateBinder privateBinder = binder.newPrivateBinder();
                privateBinder.install(createModule(Config1.class, null));
                privateBinder.bind(ExposeConfig.class).annotatedWith(named("no-prefix")).to(ExposeConfig.class).in(Scopes.SINGLETON);
                privateBinder.expose(Key.get(ExposeConfig.class, named("no-prefix")));

                privateBinder = binder.newPrivateBinder();
                privateBinder.install(createModule(Config1.class, "prefix"));
                privateBinder.bind(ExposeConfig.class).annotatedWith(named("prefix")).to(ExposeConfig.class).in(Scopes.SINGLETON);
                privateBinder.expose(Key.get(ExposeConfig.class, named("prefix")));
            }

        };
        properties = ImmutableMap.<String, String>builder()
                .putAll(properties)
                .put("prefix.stringOption", "a prefix string")
                .build();
        Injector injector = createInjector(properties, module);
        verifyConfig(injector.getInstance(Key.get(ExposeConfig.class, named("no-prefix"))).config1);
        assertEquals(injector.getInstance(Key.get(ExposeConfig.class, named("prefix"))).config1.getStringOption(), "a prefix string");
    }

    private static void verifyConfig(Config1 config)
    {
        assertEquals("a string", config.getStringOption());
        assertEquals(true, config.getBooleanOption());
        assertEquals(Boolean.TRUE, config.getBoxedBooleanOption());
        assertEquals(Byte.MAX_VALUE, config.getByteOption());
        assertEquals(Byte.valueOf(Byte.MAX_VALUE), config.getBoxedByteOption());
        assertEquals(Short.MAX_VALUE, config.getShortOption());
        assertEquals(Short.valueOf(Short.MAX_VALUE), config.getBoxedShortOption());
        assertEquals(Integer.MAX_VALUE, config.getIntegerOption());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), config.getBoxedIntegerOption());
        assertEquals(Long.MAX_VALUE, config.getLongOption());
        assertEquals(Long.valueOf(Long.MAX_VALUE), config.getBoxedLongOption());
        assertEquals(Float.MAX_VALUE, config.getFloatOption(), 0);
        assertEquals(Float.MAX_VALUE, config.getBoxedFloatOption());
        assertEquals(Double.MAX_VALUE, config.getDoubleOption(), 0);
        assertEquals(Double.MAX_VALUE, config.getBoxedDoubleOption());
        assertEquals(MyEnum.FOO, config.getMyEnumOption());
        assertEquals(config.getValueClassOption().getValue(), "a value class");
    }

    @Test
    public void testDetectsNoConfigAnnotations()
    {
        try {
            Injector injector = createInjector(Collections.<String, String>emptyMap(), createModule(ConfigWithNoAnnotations.class, null));
            injector.getInstance(ConfigWithNoAnnotations.class);
            fail("Expected exception due to missing @Config annotations");
        }
        catch (CreationException e) {
            // do nothing
        }
    }

    private static Injector createInjector(Map<String, String> properties, Module module)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        List<Message> messages = new ConfigurationValidator(configurationFactory, null).validate(module);
        return Guice.createInjector(new ConfigurationModule(configurationFactory), module, new ValidationErrorModule(messages));
    }

    private static <T> Module createModule(final Class<T> configClass, final String prefix)
    {
        return new Module() {
            @Override
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).prefixedWith(prefix).to(configClass);
            }
        };
    }

    @BeforeMethod
    protected void setUp()
            throws Exception
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

    private Map<String, String> prefix(String prefix, Map<String, String> properties)
    {
        Builder<String, String> builder = ImmutableMap.builder();
        for (Entry<String, String> entry : properties.entrySet()) {
            builder.put(prefix + "." + entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private static class ExposeConfig
    {
        public final Config1 config1;

        @Inject
        private ExposeConfig(Config1 config1)
        {
            this.config1 = config1;
        }
    }
}