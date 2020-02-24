/*
 * Copyright 2012 Proofpoint, Inc.
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
package com.proofpoint.jaxrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.proofpoint.configuration.AbstractConfigurationAwareModule;
import com.proofpoint.http.server.TheAdminServlet;
import com.proofpoint.http.server.TheServlet;
import com.proofpoint.reporting.InRotationResource;
import com.proofpoint.reporting.LivenessResource;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerLifecycleListener;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;

import javax.servlet.Servlet;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.MapBinder.newMapBinder;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static com.proofpoint.configuration.ConfigBinder.bindConfig;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.reporting.HealthBinder.healthBinder;

public class JaxrsModule
        extends AbstractConfigurationAwareModule
{
    private final CommonJaxrsModule commonJaxrsModule = new CommonJaxrsModule();
    private boolean enableOptions = false;

    public static JaxrsModule explicitJaxrsModule()
    {
        return new JaxrsModule();
    }

    public static Module adminOnlyJaxrsModule()
    {
        return new AdminOnlyJaxrsModule();
    }

    @Override
    public void setup(Binder binder)
    {
        binder.disableCircularProxies();

        binder.install(commonJaxrsModule);

        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(Key.get(ServletContainer.class));
        jaxrsBinder(binder).bind(JsonMapper.class);
        jaxrsBinder(binder).bind(SmileMapper.class);
        jaxrsBinder(binder).bind(ParsingExceptionMapper.class);
        jaxrsBinder(binder).bind(QueryParamExceptionMapper.class);
        newOptionalBinder(binder, Key.get(Ticker.class, JaxrsTicker.class))
                .setDefault().toInstance(Ticker.systemTicker());
        JaxrsConfig config = buildConfigObject(JaxrsConfig.class);
        if (config.isOverrideMethodFilter()) {
            jaxrsBinder(binder).bind(OverrideMethodFilter.class);
        }
        jaxrsBinder(binder).bind(TimingResourceDynamicFeature.class);
        if (!enableOptions) {
            jaxrsBinder(binder).bind(DisallowOptionsModelProcessor.class);
        }
        jaxrsBinder(binder).bind(InRotationResource.class);
        jaxrsBinder(binder).bind(LivenessResource.class);
        jaxrsBinder(binder).bind(HstsResponseFilter.class);

        jaxrsBinder(binder).bindAdmin(OpenApiResource.class);
        jaxrsBinder(binder).bindAdmin(OpenApiAdminResource.class);

        bindConfig(binder).bind(JaxrsConfig.class);

        binder.bind(ShutdownMonitor.class).in(SINGLETON);
        healthBinder(binder).export(ShutdownMonitor.class);
    }

    @Provides
    ResourceConfig createResourceConfig(
            Application application,
            @JaxrsInjectionProvider final Map<Class<?>, Supplier<?>> supplierMap,
            JaxrsConfig jaxrsConfig)
    {
        return commonJaxrsModule.createResourceConfig(application, supplierMap, jaxrsConfig);
    }

    @Provides
    @TheServlet
    static Map<String, String> createTheServletParams()
    {
        return new HashMap<>();
    }

    private static boolean isJaxRsBinding(Key<?> key)
    {
        Type type = key.getTypeLiteral().getType();
        if (!(type instanceof Class)) {
            return false;
        }
        return isJaxRsType((Class<?>) type);
    }

    private static boolean isJaxRsType(Class<?> type)
    {
        if (type == null) {
            return false;
        }

        if (type.isAnnotationPresent(Provider.class)) {
            return true;
        }
        else if (type.isAnnotationPresent(Path.class)) {
            return true;
        }
        if (isJaxRsType(type.getSuperclass())) {
            return true;
        }
        for (Class<?> typeInterface : type.getInterfaces()) {
            if (isJaxRsType(typeInterface)) {
                return true;
            }
        }

        return false;
    }

    public JaxrsModule withOptionsEnabled()
    {
        enableOptions = true;
        return this;
    }

    private static class AdminOnlyJaxrsModule
            implements Module
    {
        private final CommonJaxrsModule commonJaxrsModule = new CommonJaxrsModule();

        @Override
        public void configure(Binder binder)
        {
            binder.install(commonJaxrsModule);
        }

        @Provides
        ResourceConfig createResourceConfig(Application application, @JaxrsInjectionProvider final Map<Class<?>, Supplier<?>> supplierMap)
        {
            return commonJaxrsModule.createResourceConfig(application, supplierMap, new JaxrsConfig());
        }
    }

    private static class CommonJaxrsModule
            implements Module
    {
        private final AtomicReference<InjectionManager> injectionManagerReference = new AtomicReference<>();

        @Override
        public void configure(Binder binder)
        {
            binder.disableCircularProxies();

            jaxrsBinder(binder).bindInjectionProvider(ClientInfo.class).to(ClientInfoSupplier.class);
            jaxrsBinder(binder).bindAdmin(ParsingExceptionMapper.class);
            jaxrsBinder(binder).bindAdmin(QueryParamExceptionMapper.class);
            jaxrsBinder(binder).bindAdmin(ThreadDumpResource.class);

            newSetBinder(binder, Object.class, JaxrsResource.class).permitDuplicates();
            newSetBinder(binder, JaxrsBinding.class, JaxrsResource.class).permitDuplicates();
            newMapBinder(binder, new TypeLiteral<Class<?>>()
            {
            }, new TypeLiteral<Supplier<?>>()
            {
            }, JaxrsInjectionProvider.class);
        }

        @Provides
        static ServletContainer createServletContainer(ResourceConfig resourceConfig)
        {
            return new ServletContainer(resourceConfig);
        }

        ResourceConfig createResourceConfig(
                Application application,
                @JaxrsInjectionProvider Map<Class<?>, Supplier<?>> supplierMap,
                JaxrsConfig jaxrsConfig)
        {
            ResourceConfig config = ResourceConfig.forApplication(application);
            config.setProperties(ImmutableMap.<String, String>builder()
                    .put(ServerProperties.WADL_FEATURE_DISABLE, "true")
                    .put(ServerProperties.LOCATION_HEADER_RELATIVE_URI_RESOLUTION_DISABLED, "true")
                    .put(ServletProperties.QUERY_PARAMS_AS_FORM_PARAMS_DISABLED, "true")
                    .build());

            config.register(MultiPartFeature.class);

            config.register(new ContainerLifecycleListener()
            {
                @Override
                public void onStartup(Container container)
                {
                    InjectionManager injectionManager = container.getApplicationHandler().getInjectionManager();
                    injectionManagerReference.set(injectionManager);
                }

                @Override
                public void onReload(Container container)
                {
                }

                @Override
                public void onShutdown(Container container)
                {
                }
            });

            config.register(new AbstractBinder()
            {
                @Override
                protected void configure()
                {
                    for (final Entry<Class<?>, Supplier<?>> entry : supplierMap.entrySet()) {
                        bindSupplier(entry.getKey(), entry.getValue());
                    }
                }

                @SuppressWarnings("unchecked")
                private <T> void bindSupplier(Class<T> type, Supplier<?> supplier)
                {
                    bindFactory(new InjectionProviderFactory<>(type, (Supplier<T>) supplier, injectionManagerReference)).to(type);
                }
            });

            return config;
        }

        @Provides
        Application createJaxRsApplication(@JaxrsResource Set<Object> jaxRsSingletons, @JaxrsResource Set<JaxrsBinding> jaxrsBinding, Injector injector)
        {
            // detect jax-rs services that are bound into Guice, but not explicitly exported
            Set<Key<?>> missingBindings = new HashSet<>();
            ImmutableSet.Builder<Object> singletons = ImmutableSet.builder();
            jaxRsSingletons.stream()
                    .map(TimingWrapper::wrapIfAnnotatedResource)
                    .forEach(singletons::add);
            while (injector != null) {
                for (Entry<Key<?>, Binding<?>> entry : injector.getBindings().entrySet()) {
                    Key<?> key = entry.getKey();
                    if (isJaxRsBinding(key) && !jaxrsBinding.contains(new JaxrsBinding(key))) {
                        missingBindings.add(key);
                    }
                }
                injector = injector.getParent();
            }
            checkState(missingBindings.isEmpty(), "Jax-rs services must be explicitly bound using the JaxRsBinder: ", missingBindings);

            return new JaxRsApplication(singletons.build());
        }

        @Provides
        @TheAdminServlet
        static Servlet createTheAdminServlet(@AdminJaxrsResource Set<Object> adminJaxRsSingletons, ObjectMapper objectMapper)
        {
            // The admin servlet needs its own JsonMapper object so that it references
            // the admin port's UriInfo
            ImmutableSet.Builder<Object> singletons = ImmutableSet.builder();
            singletons.addAll(adminJaxRsSingletons);
            singletons.add(new JsonMapper(objectMapper));

            Application application = new JaxRsApplication(singletons.build());
            return new ServletContainer(ResourceConfig.forApplication(application));
        }

        @Provides
        @TheAdminServlet
        static Map<String, String> createTheAdminServletParams()
        {
            return new HashMap<>();
        }

        private static class JaxRsApplication
                extends Application
        {
            private final Set<Object> jaxRsSingletons;

            JaxRsApplication(Set<Object> jaxRsSingletons)
            {
                this.jaxRsSingletons = ImmutableSet.copyOf(jaxRsSingletons);
            }

            @Override
            public Set<Object> getSingletons()
            {
                return jaxRsSingletons;
            }
        }

        private static class InjectionProviderFactory<T> implements Factory<T>
        {
            private final Supplier<? extends T> supplier;
            private final AtomicReference<InjectionManager> injectionManagerReference;

            InjectionProviderFactory(Class<T> type, Supplier<? extends T> supplier, AtomicReference<InjectionManager> injectionManagerReference)
            {
                this.supplier = supplier;
                this.injectionManagerReference = injectionManagerReference;
            }

            @Override
            public T provide()
            {
                T object = supplier.get();
                InjectionManager injectionManager = injectionManagerReference.get();
                injectionManager.inject(object);
                // Cannot postconstruct. https://github.com/eclipse-ee4j/jersey/issues/3924
                return object;
            }

            @Override
            public void dispose(T o)
            {
                injectionManagerReference.get().preDestroy(o);
            }
        }
    }
}
