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
package com.proofpoint.http.client;

import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.client.jetty.JettyIoPoolConfig;
import com.proofpoint.log.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.configuration.ConfigBinder.bindConfig;
import static com.proofpoint.reporting.ReportBinder.reportBinder;
import static java.util.Objects.requireNonNull;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

/**
 * @deprecated Will no longer be public.
 */
@Deprecated
public class HttpClientModule
        implements Module
{
    private static final Logger log = Logger.get(HttpClientModule.class);
    private final String name;
    private final Class<? extends Annotation> annotation;
    private final Binder rootBinder;
    protected Binder binder;

    /**
     * @deprecated Will be removed.
     */
    @Deprecated
    protected HttpClientModule(String name, Class<? extends Annotation> annotation)
    {
        this(name, annotation, null);
    }

    /**
     * @deprecated Will become package private.
     */
    @Deprecated
    protected HttpClientModule(String name, Class<? extends Annotation> annotation, Binder rootBinder)
    {
        this.name = requireNonNull(name, "name is null");
        this.annotation = requireNonNull(annotation, "annotation is null");
        this.rootBinder = rootBinder;
    }

    @Override
    public final void configure(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder is null");

        // bind the configuration
        bindConfig(binder).bind(HttpClientConfig.class).annotatedWith(annotation).prefixedWith(name);
        bindConfig(binder).bind(JettyIoPoolConfig.class).annotatedWith(annotation).prefixedWith(name);

        // Bind the deprecated shared thread pool config, which is not used,
        // so that it can consume the deprecated config properties.
        bindConfig(rootBinder).bind(JettyIoPoolConfig.class);

        binder.bind(JettyIoPoolManager.class)
                .annotatedWith(annotation)
                .toInstance(new JettyIoPoolManager(name, annotation));

        // bind the client
        this.binder.bind(HttpClient.class).annotatedWith(annotation).toProvider(new HttpClientProvider(name, annotation)).in(Scopes.SINGLETON);

        // kick off the binding for the filter set
        newSetBinder(binder, HttpRequestFilter.class, annotation);

        // export stats
        if (rootBinder == binder) {
            reportBinder(binder).export(HttpClient.class).annotatedWith(annotation);
            newExporter(binder).export(HttpClient.class).annotatedWith(annotation).withGeneratedName();
        }
    }

    /**
     * @deprecated Will no longer be public
     */
    @Deprecated
    public void addAlias(Class<? extends Annotation> alias)
    {
        binder.bind(HttpClient.class).annotatedWith(alias).to(Key.get(HttpClient.class, annotation));
    }

    private static class HttpClientProvider
            implements Provider<HttpClient>
    {
        private final String name;
        private final Class<? extends Annotation> annotation;
        private Injector injector;

        private HttpClientProvider(String name, Class<? extends Annotation> annotation)
        {
            this.name = name;
            this.annotation = annotation;
        }

        @Inject
        public void setInjector(Injector injector)
        {
            this.injector = injector;
        }

        @Override
        public HttpClient get()
        {
            HttpClientConfig config = injector.getInstance(Key.get(HttpClientConfig.class, annotation));
            Set<HttpRequestFilter> filters = new HashSet<>(injector.getInstance(Key.get(new TypeLiteral<Set<HttpRequestFilter>>() {}, annotation)));
            HttpClientBindOptions httpClientBindOptions = injector.getInstance(Key.get(HttpClientBindOptions.class, annotation));

            JettyIoPoolManager ioPoolProvider = injector.getInstance(Key.get(JettyIoPoolManager.class, annotation));

            if (httpClientBindOptions.isWithTracing()) {
                filters.add(new TraceTokenRequestFilter());
            }

            JettyHttpClient client = new JettyHttpClient(config, ioPoolProvider.get(), filters);
            ioPoolProvider.setClient(client);
            return client;
        }
    }

    private static class SharedJettyIoPoolManager
            extends JettyIoPoolManager
    {
        private SharedJettyIoPoolManager()
        {
            super("shared", null);
        }
    }
}
