/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.discovery.client.balancing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Sets;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.Message;
import com.proofpoint.configuration.ConfigurationAwareProvider;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.discovery.client.ServiceSelectorConfig;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.http.client.balancing.HttpServiceBalancerConfig;
import com.proofpoint.http.client.balancing.HttpServiceBalancerUriConfig;
import com.proofpoint.node.NodeInfo;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public final class HttpServiceBalancerProvider
        implements ConfigurationAwareProvider<HttpServiceBalancer>
{
    private static final Key<?> STATIC_HTTP_SERVICE_BALANCER_FACTORY_KEY = Key.get(StaticHttpServiceBalancerFactory.class);
    private static final Key<?> HTTP_SERVICE_BALANCER_FACTORY_KEY = Key.get(HttpServiceBalancerFactory.class);
    private static final Set<Key<?>> BALANCER_FACTORY_KEYS = Set.of(
            STATIC_HTTP_SERVICE_BALANCER_FACTORY_KEY,
            HTTP_SERVICE_BALANCER_FACTORY_KEY
    );

    private final String type;
    private ConfigurationFactory configurationFactory;
    private StaticHttpServiceBalancerFactory staticServiceBalancerFactory;
    private HttpServiceBalancerFactory serviceBalancerFactory;
    private NodeInfo nodeInfo;

    public HttpServiceBalancerProvider(String type)
    {
        requireNonNull(type, "type is null");
        this.type = type;
    }

    @Override
    @Inject
    public void setConfigurationFactory(ConfigurationFactory configurationFactory)
    {
        this.configurationFactory = configurationFactory;
    }

    @Override
    public void buildConfigObjects(Iterable<? extends Module> modules)
    {
        Builder<Key<?>> builder = ImmutableSet.builder();
        for (Element element : Elements.getElements(modules)) {
            Collection<Key<?>> factoryKey = element.acceptVisitor(new DefaultElementVisitor<Collection<Key<?>>>()
            {
                @Override
                public <T> Collection<Key<?>> visit(Binding<T> binding)
                {
                    Key<T> key = binding.getKey();
                    return Sets.filter(BALANCER_FACTORY_KEYS, k -> k.equals(key));
                }
            });
            builder.addAll(requireNonNullElse(factoryKey, List.of()));
        }
        Set<Key<?>> factoryKeys = builder.build();

        configurationFactory.build(HttpServiceBalancerConfig.class, "service-balancer." + type);

        if (factoryKeys.size() > 1) {
            throw new ConfigurationException(Set.of(new Message("Multiple HttpServiceBalancer factories bound: " + factoryKeys)));
        }

        if (factoryKeys.contains(STATIC_HTTP_SERVICE_BALANCER_FACTORY_KEY)) {
            configurationFactory.build(HttpServiceBalancerUriConfig.class, "service-balancer." + type);
        }
        else if (factoryKeys.contains(HTTP_SERVICE_BALANCER_FACTORY_KEY)) {
            Collection<URI> uris = configurationFactory.build((StaticHttpServiceConfig.class), "service-balancer." + type).getUris();
            if (uris == null) {
                configurationFactory.build(ServiceSelectorConfig.class, "discovery." + type);
            }
        }
        else {
            throw new ConfigurationException(Set.of(new Message("Could not find a factory for HttpServiceBalancer")));
        }
    }

    @Inject(optional = true)
    public void setStaticServiceBalancerFactory(StaticHttpServiceBalancerFactory staticServiceBalancerFactory)
    {
        requireNonNull(staticServiceBalancerFactory, "staticServiceBalancerFactory is null");
        this.staticServiceBalancerFactory = staticServiceBalancerFactory;
    }

    @Inject(optional = true)
    public void setServiceBalancerFactory(HttpServiceBalancerFactory serviceBalancerFactory, NodeInfo nodeInfo)
    {
        requireNonNull(serviceBalancerFactory, "serviceBalancerFactory is null");
        requireNonNull(nodeInfo, "nodeInfo is null");
        this.serviceBalancerFactory = serviceBalancerFactory;
        this.nodeInfo = nodeInfo;
    }

    @Override
    public HttpServiceBalancer get()
    {
        HttpServiceBalancerConfig balancerConfig = configurationFactory.build(HttpServiceBalancerConfig.class, "service-balancer." + type);

        if (staticServiceBalancerFactory != null) {
            HttpServiceBalancerUriConfig httpServiceBalancerUriConfig = configurationFactory.build(HttpServiceBalancerUriConfig.class, "service-balancer." + type);
            return staticServiceBalancerFactory.createHttpServiceBalancer(type, httpServiceBalancerUriConfig, balancerConfig);
        }

        requireNonNull(serviceBalancerFactory, "serviceBalancerFactory is null");

        Collection<URI> uris = configurationFactory.build(StaticHttpServiceConfig.class, "service-balancer." + type).getUris();
        if (uris != null) {
            return serviceBalancerFactory.createHttpServiceBalancer(type, balancerConfig, uris);
        }

        requireNonNull(nodeInfo, "nodeInfo is null");
        ServiceSelectorConfig selectorConfig = configurationFactory.build(ServiceSelectorConfig.class, "discovery." + type);

        return serviceBalancerFactory.createHttpServiceBalancer(type, selectorConfig, balancerConfig, nodeInfo);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HttpServiceBalancerProvider that = (HttpServiceBalancerProvider) o;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(type);
    }
}
