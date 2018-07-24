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
package com.proofpoint.discovery.client;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.multibindings.Multibinder;
import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement.ServiceAnnouncementBuilder;
import com.proofpoint.discovery.client.balancing.HttpServiceBalancerProvider;
import com.proofpoint.http.client.balancing.BalancingHttpClientBindingBuilder;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.http.client.balancing.HttpServiceBalancerConfig;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.annotation.Annotation;
import java.net.URI;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.inject.Scopes.SINGLETON;
import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.discovery.client.ServiceTypes.serviceType;
import static com.proofpoint.discovery.client.announce.ServiceAnnouncement.serviceAnnouncement;
import static com.proofpoint.discovery.client.announce.ServiceAnnouncement.serviceAnnouncementError;
import static com.proofpoint.http.client.HttpClientBinder.httpClientBinder;
import static java.util.Objects.requireNonNull;

public class DiscoveryBinder
{
    public static DiscoveryBinder discoveryBinder(Binder binder)
    {
        requireNonNull(binder, "binder is null");
        return new DiscoveryBinder(binder);
    }

    private final Multibinder<ServiceAnnouncement> serviceAnnouncementBinder;
    private final Binder binder;

    protected DiscoveryBinder(Binder binder)
    {
        requireNonNull(binder, "binder is null");
        this.binder = binder.skipSources(getClass());
        this.serviceAnnouncementBinder = Multibinder.newSetBinder(binder, ServiceAnnouncement.class);
    }

    public void bindSelector(String type)
    {
        requireNonNull(type, "type is null");
        bindSelector(serviceType(type));
    }

    public void bindSelector(ServiceType serviceType)
    {
        requireNonNull(serviceType, "serviceType is null");
        bindConfig(binder).annotatedWith(serviceType).prefixedWith("discovery." + serviceType.value()).to(ServiceSelectorConfig.class);
        binder.bind(ServiceSelector.class).annotatedWith(serviceType).toProvider(new ServiceSelectorProvider(serviceType.value())).in(SINGLETON);
    }

    public void bindHttpBalancer(String type)
    {
        requireNonNull(type, "type is null");
        bindHttpBalancer(serviceType(type));
    }

    public void bindHttpBalancer(ServiceType serviceType)
    {
        requireNonNull(serviceType, "serviceType is null");
        bindConfig(binder).annotatedWith(serviceType).prefixedWith("service-balancer." + serviceType.value()).to(HttpServiceBalancerConfig.class);
        binder.bind(HttpServiceBalancer.class).annotatedWith(serviceType).toProvider(new HttpServiceBalancerProvider(serviceType.value())).in(SINGLETON);
    }

    public void bindServiceAnnouncement(ServiceAnnouncement announcement)
    {
        requireNonNull(announcement, "announcement is null");
        serviceAnnouncementBinder.addBinding().toInstance(announcement);
    }

    public void bindServiceAnnouncement(Provider<ServiceAnnouncement> announcementProvider)
    {
        requireNonNull(announcementProvider, "announcementProvider is null");
        serviceAnnouncementBinder.addBinding().toProvider(announcementProvider).in(SINGLETON);
    }

    public <T extends ServiceAnnouncement> void bindServiceAnnouncement(Class<? extends Provider<T>> announcementProviderClass)
    {
        requireNonNull(announcementProviderClass, "announcementProviderClass is null");
        serviceAnnouncementBinder.addBinding().toProvider(announcementProviderClass).in(SINGLETON);
    }

    public ServiceAnnouncementBuilder bindHttpAnnouncement(String type)
    {
        ServiceAnnouncementBuilder serviceAnnouncementBuilder = serviceAnnouncement(type);
        bindServiceAnnouncement(new HttpAnnouncementProvider(serviceAnnouncementBuilder));
        return serviceAnnouncementBuilder;
    }

    /**
     * @deprecated Use {@link #bindDiscoveredHttpClient(String, Class)} to get a
     * {@link com.proofpoint.http.client.balancing.BalancingHttpClient} or use
     * {@link #bindHttpBalancer(String)} to get a
     * {@link com.proofpoint.http.client.balancing.HttpServiceBalancer}.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public void bindHttpSelector(String type)
    {
        requireNonNull(type, "type is null");
        bindHttpSelector(serviceType(type));
    }

    /**
     * @deprecated Use {@link #bindDiscoveredHttpClient(ServiceType, Class)} to get a
     * {@link com.proofpoint.http.client.balancing.BalancingHttpClient} or use
     * {@link #bindHttpBalancer(ServiceType)} to get a
     * {@link com.proofpoint.http.client.balancing.HttpServiceBalancer}.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public void bindHttpSelector(ServiceType serviceType)
    {
        requireNonNull(serviceType, "serviceType is null");
        bindSelector(serviceType);
        binder.bind(HttpServiceSelector.class).annotatedWith(serviceType).toProvider(new HttpServiceSelectorProvider(serviceType.value())).in(SINGLETON);
    }

    public BalancingHttpClientBindingBuilder bindDiscoveredHttpClient(String type)
    {
        return bindDiscoveredHttpClient(requireNonNull(type, "type is null"), serviceType(type));
    }

    public BalancingHttpClientBindingBuilder bindDiscoveredHttpClient(String name, ServiceType serviceType)
    {
        requireNonNull(name, "name is null");
        requireNonNull(serviceType, "serviceType is null");

        bindHttpBalancer(serviceType);
        String serviceName = LOWER_CAMEL.to(UPPER_CAMEL, serviceType.value());
        return httpClientBinder(binder).bindBalancingHttpClient(name, serviceType, serviceName, Key.get(HttpServiceBalancer.class, serviceType));
    }

    public BalancingHttpClientBindingBuilder bindDiscoveredHttpClient(String type, Class<? extends Annotation> annotation)
    {
        return bindDiscoveredHttpClient(serviceType(requireNonNull(type, "type is null")), annotation);
    }

    public BalancingHttpClientBindingBuilder bindDiscoveredHttpClient(ServiceType serviceType, Class<? extends Annotation> annotation)
    {
        return bindDiscoveredHttpClient(serviceType.value(), serviceType, annotation);
    }

    public BalancingHttpClientBindingBuilder bindDiscoveredHttpClient(String name, ServiceType serviceType, Class<? extends Annotation> annotation)
    {
        bindHttpBalancer(serviceType);
        return httpClientBinder(binder).bindBalancingHttpClient(name, annotation, Key.get(HttpServiceBalancer.class, serviceType));
    }

    static class HttpAnnouncementProvider implements Provider<ServiceAnnouncement>
    {
        private final ServiceAnnouncementBuilder builder;
        private AnnouncementHttpServerInfo httpServerInfo;

        HttpAnnouncementProvider(ServiceAnnouncementBuilder serviceAnnouncementBuilder)
        {
            builder = serviceAnnouncementBuilder;
        }

        @Inject
        public void setAnnouncementHttpServerInfo(AnnouncementHttpServerInfo httpServerInfo)
        {
            this.httpServerInfo = httpServerInfo;
        }

        @Override
        public ServiceAnnouncement get()
        {
            if (httpServerInfo.getHttpUri() != null) {
                builder.addProperty("http", httpServerInfo.getHttpUri().toString());
                builder.addProperty("http-external", httpServerInfo.getHttpExternalUri().toString());
            }
            URI httpsUri = httpServerInfo.getHttpsUri();
            if (httpsUri != null) {
                if (!httpsUri.getHost().contains(".")) {
                    return serviceAnnouncementError("HttpServer's HTTPS URI host \"" + httpsUri.getHost() + "\" must be a FQDN");
                }
                builder.addProperty("https", httpsUri.toString());
            }
            if (httpServerInfo.getAdminUri() != null) {
                builder.addProperty("admin", httpServerInfo.getAdminUri().toString());
            }
            return builder.build();
        }
    }
}
