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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.proofpoint.configuration.AbstractConfigurationAwareModule;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigurationDefaultingModule;
import com.proofpoint.discovery.client.announce.Announcement;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.announce.AnnouncerImpl;
import com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient;
import com.proofpoint.discovery.client.announce.HttpDiscoveryAnnouncementClient;
import com.proofpoint.discovery.client.announce.NullAnnouncer;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement;
import com.proofpoint.discovery.client.balancing.HttpServiceBalancerFactory;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.http.client.balancing.HttpServiceBalancerConfig;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.http.client.balancing.HttpServiceBalancerStats;
import com.proofpoint.reporting.ReportCollectionFactory;
import com.proofpoint.reporting.ReportExporter;
import com.proofpoint.units.Duration;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.configuration.ConfigBinder.bindConfig;
import static com.proofpoint.http.client.HttpClientBinder.httpClientBinder;
import static com.proofpoint.http.client.ServiceTypes.serviceType;
import static com.proofpoint.json.JsonCodecBinder.jsonCodecBinder;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class DiscoveryModule
        extends AbstractConfigurationAwareModule
        implements ConfigurationDefaultingModule
{
    @Override
    public Map<String, String> getConfigurationDefaults()
    {
        return ImmutableMap.of("discovery.http-client.idle-timeout", "5s");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setup(Binder binder)
    {
        if (buildConfigObject(DiscoveryClientConfig.class).isDiscoveryStatic()) {
            binder.install(new StaticDiscoveryModule());
            binder.bind(Announcer.class).to(NullAnnouncer.class).in(Scopes.SINGLETON);
            bindConfig(binder).bind(ConsumeIdleTimeout.class);
            return;
        }

        binder.install(new NonstaticDiscoveryModule());
    }

    private static class NonstaticDiscoveryModule
        implements Module
    {
        private HttpServiceBalancerImpl discoveryBalancer = null;

        @Override
        public void configure(Binder binder)
        {
            // bind service inventory
            binder.bind(ServiceInventory.class).asEagerSingleton();
            bindConfig(binder).bind(ServiceInventoryConfig.class);
            binder.bind(DiscoveryAddressLookup.class).in(Scopes.SINGLETON);

            bindConfig(binder).bind(HttpServiceBalancerConfig.class).annotatedWith(ForDiscoveryClient.class).prefixedWith("service-balancer.discovery");

            // for legacy configurations
            bindConfig(binder).bind(DiscoveryClientConfig.class);

            // bind discovery client and dependencies
            binder.bind(DiscoveryLookupClient.class).to(HttpDiscoveryLookupClient.class).in(Scopes.SINGLETON);
            binder.bind(DiscoveryAnnouncementClient.class).to(HttpDiscoveryAnnouncementClient.class).in(Scopes.SINGLETON);
            jsonCodecBinder(binder).bindJsonCodec(ServiceDescriptorsRepresentation.class);
            jsonCodecBinder(binder).bindJsonCodec(Announcement.class);

            // bind the http client
            httpClientBinder(binder).bindBalancingHttpClient("discovery", ForDiscoveryClient.class, Key.get(HttpServiceBalancer.class, serviceType("discovery")));

            // bind announcer
            binder.bind(Announcer.class).to(AnnouncerImpl.class).in(Scopes.SINGLETON);

            // Must create a multibinder for service announcements or construction will fail if no
            // service announcements are bound, which is legal for processes that don't have public services
            newSetBinder(binder, ServiceAnnouncement.class);

            binder.bind(ServiceSelectorFactory.class).to(CachingServiceSelectorFactory.class).in(Scopes.SINGLETON);
            binder.bind(HttpServiceBalancerFactory.class).in(Scopes.SINGLETON);

            binder.bind(ScheduledExecutorService.class)
                    .annotatedWith(ForDiscoveryClient.class)
                    .toProvider(DiscoveryExecutorProvider.class)
                    .in(Scopes.SINGLETON);

            newExporter(binder).export(ServiceInventory.class).withGeneratedName();
        }

        @Provides
        @com.proofpoint.http.client.ServiceType("discovery")
        public HttpServiceBalancer createHttpServiceBalancer(
                ReportExporter reportExporter,
                ReportCollectionFactory reportCollectionFactory,
                @ForDiscoveryClient HttpServiceBalancerConfig config)
        {
            return getHttpServiceBalancerImpl(reportExporter, reportCollectionFactory, config);
        }

        @Provides
        @com.proofpoint.http.client.ServiceType("discovery")
        public synchronized HttpServiceBalancerImpl getHttpServiceBalancerImpl(
                ReportExporter reportExporter,
                ReportCollectionFactory reportCollectionFactory,
                @ForDiscoveryClient HttpServiceBalancerConfig config)
        {
            if (discoveryBalancer == null) {
                Map<String, String> tags = ImmutableMap.of("serviceType", "discovery");
                discoveryBalancer = new HttpServiceBalancerImpl(
                        "discovery",
                        reportCollectionFactory.createReportCollection(HttpServiceBalancerStats.class, false, "ServiceClient", tags),
                        config
                );
                reportExporter.export(discoveryBalancer, false, "ServiceClient", tags);
            }
            return discoveryBalancer;
        }

        @Provides
        @ServiceType("discovery")
        public HttpServiceBalancer createLegacyHttpServiceBalancer(
                ReportExporter reportExporter,
                ReportCollectionFactory reportCollectionFactory,
                @ForDiscoveryClient HttpServiceBalancerConfig config)
        {
            return getHttpServiceBalancerImpl(reportExporter, reportCollectionFactory, config);
        }

        @Provides
        @ServiceType("discovery")
        public HttpServiceBalancerImpl createLegacyHttpServiceBalancerImpl(
                ReportExporter reportExporter,
                ReportCollectionFactory reportCollectionFactory,
                @ForDiscoveryClient HttpServiceBalancerConfig config)
        {
            return getHttpServiceBalancerImpl(reportExporter, reportCollectionFactory, config);
        }
    }

    // Workaround needed to consume the configuration default when in static mode
    private static class ConsumeIdleTimeout
    {
        public Duration getIdleTimeout()
        {
            return null;
        }

        @Config("discovery.http-client.idle-timeout")
        public void setIdleTimeout(Duration timeout) {
        }
    }
}
