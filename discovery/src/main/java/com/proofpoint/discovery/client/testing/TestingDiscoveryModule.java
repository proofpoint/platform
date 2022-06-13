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
package com.proofpoint.discovery.client.testing;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.discovery.client.DiscoveryExecutorProvider;
import com.proofpoint.discovery.client.DiscoveryLookupClient;
import com.proofpoint.discovery.client.ForDiscoveryClient;
import com.proofpoint.discovery.client.ServiceSelectorFactory;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient;
import com.proofpoint.discovery.client.announce.NullAnnouncer;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement;
import com.proofpoint.discovery.client.balancing.HttpServiceBalancerFactory;

import java.util.concurrent.ScheduledExecutorService;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class TestingDiscoveryModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();
        binder.requireExplicitBindings();

        // bind discovery client and dependencies
        binder.bind(InMemoryDiscoveryClient.class).in(Scopes.SINGLETON);
        binder.bind(DiscoveryAnnouncementClient.class).to(Key.get(InMemoryDiscoveryClient.class)).in(Scopes.SINGLETON);
        binder.bind(DiscoveryLookupClient.class).to(Key.get(InMemoryDiscoveryClient.class)).in(Scopes.SINGLETON);

        // bind announcer
        binder.bind(Announcer.class).to(NullAnnouncer.class).in(Scopes.SINGLETON);
        // Must create a multibinder for service announcements or construction will fail if no
        // service announcements are bound, which is legal for processes that don't have public services
        newSetBinder(binder, ServiceAnnouncement.class);

        binder.bind(ServiceSelectorFactory.class).to(SimpleServiceSelectorFactory.class).in(Scopes.SINGLETON);
        binder.bind(HttpServiceBalancerFactory.class).in(Scopes.SINGLETON);
        binder.bind(ScheduledExecutorService.class)
                .annotatedWith(ForDiscoveryClient.class)
                .toProvider(DiscoveryExecutorProvider.class)
                .in(Scopes.SINGLETON);
    }
}
