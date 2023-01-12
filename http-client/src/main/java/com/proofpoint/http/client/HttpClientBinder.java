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

import com.google.common.collect.Streams;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.PrivateBinder;
import com.google.inject.Scopes;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import com.proofpoint.http.client.balancing.BalancingHttpClient;
import com.proofpoint.http.client.balancing.BalancingHttpClientBindingBuilder;
import com.proofpoint.http.client.balancing.BalancingHttpClientConfig;
import com.proofpoint.http.client.balancing.ForBalancingHttpClient;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.http.client.balancing.HttpServiceBalancerConfig;
import com.proofpoint.http.client.balancing.HttpServiceBalancerUriConfig;
import org.weakref.jmx.ObjectNameBuilder;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.configuration.ConfigBinder.bindConfig;
import static com.proofpoint.reporting.ReportBinder.reportBinder;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

/**
 * Binds {@link HttpClient} implementations.
 *
 * <h3>The HttpClient Binding EDSL</h3>
 *
 * <pre>
 *     httpClientBinder(binder).bindHttpClient("foo", FooClient.class);</pre>
 *
 * Binds an {@link HttpClient} annotated with the {@code @FooClient}
 * annotation to an implementation that takes absolute {@link URI}s in
 * requests. The string {@code "foo"} specifies the prefix for configuration.
 *
 * <pre>
 *     httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
 *         .withFilter(SomeFilter.class);</pre>
 *
 * Adds an {@link HttpRequestFilter} for modifying requests submitted to the
 * client. Multiple filters may be added.
 *
 * <pre>
 *     httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
 *         .addFilterBinding().toProvider(SomeFilterProvider.class);</pre>
 *
 * Adds an {@link HttpRequestFilter} using the Guice binding EDSL.
 *
 * <pre>
 *     httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
 *         .withoutTracing();</pre>
 *
 * Suppresses the forwarding of trace tokens in the request headers.
 *
 * <pre>
 *     httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
 *         .withPrivateIoThreadPool();</pre>
 *
 * Specifies that the {@link HttpClient} should have its own IO thread pool
 * instead of using the shared pool. This is appropriate for clients that have
 * a high request rate.
 *
 * <pre>
 *     httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
 *         .withAlias(BarClient.class);</pre>
 *
 * Additionally binds the same {@link HttpClient} annotated with the
 * {@code @BarClient} annotation.
 *
 * <pre>
 *     httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
 *         .withoutCertificateVerification();</pre>
 *
 * Specifies that the {@link HttpClient} should disable TLS certificate verification.
 *
 * <pre>
 *     httpClientBinder(binder).bindBalancingHttpClient("foo");</pre>
 *
 * Binds an {@link HttpClient} annotated with the {@code @ServiceType("foo")}
 * annotation to an implementation that takes relative {@link URI}s in
 * requests, interpreting them relative to URIs specified in configuration.
 * The string {@code "foo"} also specifies the prefix for configuration.
 *
 * <pre>
 *     httpClientBinder(binder).bindBalancingHttpClient("foo", "https://foo.example.com");</pre>
 *
 * Binds an {@link HttpClient} annotated with the {@code @ServiceType("foo")}
 * annotation to an implementation that takes relative {@link URI}s in
 * requests, interpreting them relative to "https://foo.example.com".
 * The string {@code "foo"} also specifies the prefix for configuration.
 *
 * <pre>
 *     httpClientBinder(binder).bindKubernetesServiceHttpClient("foo", "bar");</pre>
 *
 * Binds an {@link HttpClient} annotated with the {@code @ServiceType("foo")}
 * annotation to an implementation that takes relative {@link URI}s in
 * requests, interpreting them relative to the Kubernetes service "foo" in
 * Kubernetes namespace "bar".
 * The string {@code "foo"} also specifies the prefix for configuration.
 *
 */
public class HttpClientBinder
{
    private final Binder binder;
    private final Binder rootBinder;
    private final boolean isPrivate;

    private HttpClientBinder(Binder binder, Binder rootBinder, boolean isPrivate)
    {
        this.binder = requireNonNull(binder, "binder is null").skipSources(getClass());
        this.rootBinder = requireNonNull(rootBinder, "rootBinder is null");
        this.isPrivate = isPrivate;
    }

    /**
     * Creates a new {@link HttpClientBinder}. See the EDSL examples at {@link HttpClientBinder}.
     *
     * @param binder The Guice {@link Binder} to use.
     */
    public static HttpClientBinder httpClientBinder(Binder binder)
    {
        return new HttpClientBinder(binder, binder, false);
    }

    /**
     * Creates a new {@link HttpClientBinder} for making a private binding of
     * an {@link HttpClient}. The configuration and metrics will be bound
     * globally, as is required by those subsystems.
     *
     * See the EDSL examples at {@link HttpClientBinder}.
     *
     * @param privateBinder The Guice private {@link Binder} to use.
     * @param rootBinder The Guice parent {@link Binder} to use for bindings
     * that cannot be done privately.
     */
    public static HttpClientBinder httpClientPrivateBinder(Binder privateBinder, Binder rootBinder)
    {
        return new HttpClientBinder(privateBinder, rootBinder, true);
    }

    /**
     * Binds an {@link HttpClient} to an implementation that takes absolute
     * {@link URI}s. See the EDSL examples at {@link HttpClientBinder}.
     *
     * @param name The configuration prefix. Should be lowercase hyphen-separated.
     * @param annotation The binding annotation.
     */
    public HttpClientBindingBuilder bindHttpClient(String name, Class<? extends Annotation> annotation)
    {
        HttpClientModule module = new HttpClientModule(name, annotation, rootBinder, isPrivate);
        binder.install(module);
        HttpClientBindOptions options = new HttpClientBindOptions();
        binder.bind(HttpClientBindOptions.class).annotatedWith(annotation).toInstance(options);
        return new HttpClientBindingBuilder(module, newSetBinder(binder, HttpRequestFilter.class, annotation), options);
    }

    /**
     * Binds an {@link HttpClient} annotated with {@code @ServiceType(type)}
     * to an implementation that takes relative
     * {@link URI}s. The requests are balanced against a static set of prefixes
     * taken from configuration.
     *
     * See the EDSL examples at {@link HttpClientBinder}.
     *
     * @param type The service type.
     */
    public BalancingHttpClientBindingBuilder bindBalancingHttpClient(String type)
    {
        requireNonNull(type, "type is null");

        return bindBalancingHttpClient(type, ServiceTypes.serviceType(type), type);
    }

    /**
     * Binds an {@link HttpClient} annotated with {@code @ServiceType(type)}
     * to an implementation that takes relative
     * {@link URI}s. The requests are balanced against the provided static set
     * of prefixes.
     *
     * See the EDSL examples at {@link HttpClientBinder}.
     *
     * @param type The service type.
     * @param moreBaseUris The {@link URI} prefixes to balance across.
     */
    public BalancingHttpClientBindingBuilder bindBalancingHttpClient(String type, String baseUri, String... moreBaseUris)
    {
        requireNonNull(type, "type is null");
        requireNonNull(baseUri, "baseUri is null");
        requireNonNull(moreBaseUris, "moreBaseUris is null");

        return bindBalancingHttpClient(type, ServiceTypes.serviceType(type), type, Streams.concat(Stream.of(baseUri), Arrays.stream(moreBaseUris)).map(URI::create).collect(toList()));
    }

    /**
     * Binds an {@link HttpClient} to an implementation that takes relative
     * {@link URI}s. The requests are balanced against the set of prefixes
     * specified in configuration.
     *
     * See the EDSL examples at {@link HttpClientBinder}.
     *
     * @param name The configuration prefix. Should be lowercase hyphen-separated.
     * @param annotation The binding annotation.
     */
    public BalancingHttpClientBindingBuilder bindBalancingHttpClient(String name, Class<? extends Annotation> annotation)
    {
        requireNonNull(name, "name is null");
        requireNonNull(annotation, "annotation is null");

        bindConfig(binder).bind(HttpServiceBalancerConfig.class).annotatedWith(annotation).prefixedWith("service-client." + annotation.getSimpleName());
        bindConfig(binder).bind(HttpServiceBalancerUriConfig.class).annotatedWith(annotation).prefixedWith("service-client." + annotation.getSimpleName());
        PrivateBinder privateBinder = binder.newPrivateBinder();
        privateBinder.bind(HttpServiceBalancer.class).annotatedWith(ForBalancingHttpClient.class)
                .toProvider(new ConfiguredStaticHttpServiceBalancerProvider(annotation.getSimpleName(),
                        Key.get(HttpServiceBalancerConfig.class, annotation),
                        Key.get(HttpServiceBalancerUriConfig.class, annotation)));
        return createBalancingHttpClientBindingBuilder(privateBinder, name, annotation);
    }

    /**
     * Binds an {@link HttpClient} to an implementation that takes relative
     * {@link URI}s. The requests are balanced against the provided static set
     * of prefixes.
     *
     * See the EDSL examples at {@link HttpClientBinder}.
     *
     * @param name The configuration prefix. Should be lowercase hyphen-separated.
     * @param annotation The binding annotation.
     * @param baseUris The {@link URI} prefixes to balance across.
     */
    public BalancingHttpClientBindingBuilder bindBalancingHttpClient(String name, Class<? extends Annotation> annotation, Collection<URI> baseUris)
    {
        requireNonNull(name, "name is null");
        requireNonNull(annotation, "annotation is null");
        requireNonNull(baseUris, "baseUris is null");
        checkArgument(!baseUris.isEmpty(), "baseUris is empty");

        bindConfig(binder).bind(HttpServiceBalancerConfig.class).annotatedWith(annotation).prefixedWith("service-client." + annotation.getSimpleName());
        PrivateBinder privateBinder = binder.newPrivateBinder();
        privateBinder.bind(HttpServiceBalancer.class).annotatedWith(ForBalancingHttpClient.class)
                .toProvider(new StaticHttpServiceBalancerProvider(annotation.getSimpleName(), baseUris, Key.get(HttpServiceBalancerConfig.class, annotation)));
        return createBalancingHttpClientBindingBuilder(privateBinder, name, annotation);
    }

    /**
     * Binds an {@link HttpClient} to an implementation that takes relative
     * {@link URI}s. The requests are balanced against an
     * {@link HttpServiceBalancer} obtained from Guice.
     *
     * See the EDSL examples at {@link HttpClientBinder}.
     *
     * @param name The configuration prefix. Should be lowercase hyphen-separated.
     * @param annotation The binding annotation.
     * @param balancerKey The {@link Key} specifying the {@link HttpServiceBalancer} to use.
     */
    public BalancingHttpClientBindingBuilder bindBalancingHttpClient(String name, Class<? extends Annotation> annotation, Key<? extends HttpServiceBalancer> balancerKey)
    {
        requireNonNull(name, "name is null");
        requireNonNull(annotation, "annotation is null");
        requireNonNull(balancerKey, "balancerKey is null");

        PrivateBinder privateBinder = binder.newPrivateBinder();
        privateBinder.bind(HttpServiceBalancer.class).annotatedWith(ForBalancingHttpClient.class).to(balancerKey);
        return createBalancingHttpClientBindingBuilder(privateBinder, name, annotation);
    }

    /**
     * Binds an {@link HttpClient} to an implementation that takes relative
     * {@link URI}s. The requests are balanced against the set of prefixes
     * specified in configuration.
     *
     * See the EDSL examples at {@link HttpClientBinder}.
     *
     * @param name The configuration prefix for the HttpClient. Should be lowercase hyphen-separated.
     * @param annotation The binding annotation.
     * @param serviceName The name of the service being balanced.
     * Used in metrics and in the configuration prefix for the service balancer.
     * Ordinarily this is the value of the binding annotation.
     */
    public BalancingHttpClientBindingBuilder bindBalancingHttpClient(String name, Annotation annotation, String serviceName)
    {
        requireNonNull(name, "name is null");
        requireNonNull(annotation, "annotation is null");

        bindConfig(binder).bind(HttpServiceBalancerConfig.class).annotatedWith(annotation).prefixedWith("service-client." + serviceName);
        bindConfig(binder).bind(HttpServiceBalancerUriConfig.class).annotatedWith(annotation).prefixedWith("service-client." + serviceName);
        PrivateBinder privateBinder = binder.newPrivateBinder();
        privateBinder.bind(HttpServiceBalancer.class).annotatedWith(ForBalancingHttpClient.class)
                .toProvider(new ConfiguredStaticHttpServiceBalancerProvider(serviceName,
                        Key.get(HttpServiceBalancerConfig.class, annotation),
                        Key.get(HttpServiceBalancerUriConfig.class, annotation)));
        return createBalancingHttpClientBindingBuilder(privateBinder, name, annotation, serviceName);
    }

    private BalancingHttpClientBindingBuilder createBalancingHttpClientBindingBuilder(PrivateBinder privateBinder, String name, Class<? extends Annotation> annotation)
    {
        HttpClientBindingBuilder delegateBindingBuilder = httpClientPrivateBinder(privateBinder, binder).bindHttpClient(name, ForBalancingHttpClient.class);
        bindConfig(privateBinder).bind(BalancingHttpClientConfig.class).prefixedWith(name);
        privateBinder.bind(HttpClient.class).annotatedWith(annotation).to(BalancingHttpClient.class).in(Scopes.SINGLETON);
        privateBinder.expose(HttpClient.class).annotatedWith(annotation);
        reportBinder(binder).export(HttpClient.class).annotatedWith(annotation);
        newExporter(binder).export(HttpClient.class).annotatedWith(annotation).withGeneratedName();
        binder.bind(ScheduledExecutorService.class).annotatedWith(ForBalancingHttpClient.class).toProvider(RetryExecutorProvider.class);

        return new BalancingHttpClientBindingBuilder(binder, annotation, delegateBindingBuilder);
    }

    /**
     * Binds an {@link HttpClient} to an implementation that takes relative
     * {@link URI}s. The requests are balanced against the provided static set
     * of prefixes.
     *
     * See the EDSL examples at {@link HttpClientBinder}.
     *
     * @param name The configuration prefix for the HttpClient. Should be lowercase hyphen-separated.
     * @param annotation The binding annotation.
     * @param serviceName The name of the service being balanced.
     * Used in metrics and in the configuration prefix for the service balancer.
     * Ordinarily this is the value of the binding annotation.
     * @param baseUris The {@link URI} prefixes to balance across.
     */
    public BalancingHttpClientBindingBuilder bindBalancingHttpClient(String name, Annotation annotation, String serviceName, Collection<URI> baseUris)
    {
        requireNonNull(name, "name is null");
        requireNonNull(annotation, "annotation is null");
        requireNonNull(baseUris, "baseUris is null");
        checkArgument(!baseUris.isEmpty(), "baseUris is empty");

        bindConfig(binder).bind(HttpServiceBalancerConfig.class).annotatedWith(annotation).prefixedWith("service-client." + serviceName);
        PrivateBinder privateBinder = binder.newPrivateBinder();
        privateBinder.bind(HttpServiceBalancer.class).annotatedWith(ForBalancingHttpClient.class)
                .toProvider(new StaticHttpServiceBalancerProvider(serviceName, baseUris, Key.get(HttpServiceBalancerConfig.class, annotation)));
        return createBalancingHttpClientBindingBuilder(privateBinder, name, annotation, serviceName);
    }

    /**
     * Binds an {@link HttpClient} to an implementation that takes relative
     * {@link URI}s. The requests are balanced against an
     * {@link HttpServiceBalancer} obtained from Guice.
     *
     * See the EDSL examples at {@link HttpClientBinder}.
     *
     * @param name The configuration prefix. Should be lowercase hyphen-separated.
     * @param annotation The binding annotation.
     * @param serviceName The name of the service being balanced. Used in metrics. Ordinarily this is the value of the binding annotation.
     * @param balancerKey The {@link Key} specifying the {@link HttpServiceBalancer} to use.
     */
    public BalancingHttpClientBindingBuilder bindBalancingHttpClient(String name, Annotation annotation, String serviceName, Key<? extends HttpServiceBalancer> balancerKey)
    {
        requireNonNull(name, "name is null");
        requireNonNull(annotation, "annotation is null");
        requireNonNull(balancerKey, "balancerKey is null");

        PrivateBinder privateBinder = binder.newPrivateBinder();
        privateBinder.bind(HttpServiceBalancer.class).annotatedWith(ForBalancingHttpClient.class).to(balancerKey);
        return createBalancingHttpClientBindingBuilder(privateBinder, name, annotation, serviceName);
    }

    /**
     * Binds an {@link HttpClient} annotated with @ServiceType(type)
     * to an implementation that uses a Kubernetes service.
     *
     * See the EDSL examples at {@link HttpClientBinder}.
     *
     * @param type The Kubernetes service type.
     * @param namespace The Kubernetes namespace serving the service.
     */
    public BalancingHttpClientBindingBuilder bindKubernetesServiceHttpClient(String type, String namespace)
    {
        requireNonNull(type, "type is null");
        checkArgument(!type.equals(""), "type is empty");
        requireNonNull(namespace, "namespace is null");
        checkArgument(!namespace.equals(""), "namespace is empty");

        return bindBalancingHttpClient(type, "https://" + type + "." + namespace + ".svc.cluster.local");
    }

    private BalancingHttpClientBindingBuilder createBalancingHttpClientBindingBuilder(PrivateBinder privateBinder, String name, Annotation annotation, String serviceName)
    {
        HttpClientBindingBuilder delegateBindingBuilder = httpClientPrivateBinder(privateBinder, binder).bindHttpClient(name, ForBalancingHttpClient.class);
        bindConfig(privateBinder).bind(BalancingHttpClientConfig.class).prefixedWith(name);
        privateBinder.bind(HttpClient.class).annotatedWith(annotation).to(BalancingHttpClient.class).in(Scopes.SINGLETON);
        privateBinder.expose(HttpClient.class).annotatedWith(annotation);
        reportBinder(binder).export(HttpClient.class).annotatedWith(annotation).withNamePrefix("HttpClient." + serviceName);
        newExporter(binder).export(HttpClient.class).annotatedWith(annotation).as(new ObjectNameBuilder(HttpClient.class.getPackage().getName())
                .withProperty("type", "HttpClient")
                .withProperty("name", serviceName)
                .build()
        );
        binder.bind(ScheduledExecutorService.class).annotatedWith(ForBalancingHttpClient.class).toProvider(RetryExecutorProvider.class);

        return new BalancingHttpClientBindingBuilder(binder, annotation, delegateBindingBuilder);
    }

    public static class HttpClientBindingBuilder
    {
        private final HttpClientModule module;
        private final Multibinder<HttpRequestFilter> multibinder;
        private final HttpClientBindOptions options;

        private HttpClientBindingBuilder(HttpClientModule module, Multibinder<HttpRequestFilter> multibinder, HttpClientBindOptions options)
        {
            this.module = module;
            this.multibinder = multibinder;
            this.options = options;
        }

        /**
         * See the EDSL examples at {@link HttpClientBinder}.
         */
        public HttpClientBindingBuilder withAlias(Class<? extends Annotation> alias)
        {
            module.addAlias(alias);
            return this;
        }

        /**
         * See the EDSL examples at {@link HttpClientBinder}.
         */
        public HttpClientBindingBuilder withAliases(Collection<Class<? extends Annotation>> aliases)
        {
            aliases.forEach(module::addAlias);
            return this;
        }

        /**
         * See the EDSL examples at {@link HttpClientBinder}.
         */
        public LinkedBindingBuilder<HttpRequestFilter> addFilterBinding()
        {
            return multibinder.addBinding();
        }

        /**
         * See the EDSL examples at {@link HttpClientBinder}.
         */
        public HttpClientBindingBuilder withFilter(Class<? extends HttpRequestFilter> filterClass)
        {
            multibinder.addBinding().to(filterClass);
            return this;
        }

        /**
         * @deprecated No longer necessary.
         */
        @Deprecated
        public HttpClientBindingBuilder withTracing()
        {
            return this;
        }

        /**
         * See the EDSL examples at {@link HttpClientBinder}.
         */
        public HttpClientBindingBuilder withoutTracing()
        {
            options.setWithTracing(false);
            return this;
        }

        /**
         * @deprecated No longer necessary.
         */
        @Deprecated
        public HttpClientBindingBuilder withPrivateIoThreadPool()
        {
            return this;
        }

        /**
         * See the EDSL examples at {@link HttpClientBinder}.
         */
        public HttpClientBindingBuilder withoutCertificateVerification() {
            options.setWithoutCertificateVerification();
            return this;
        }
    }
}
