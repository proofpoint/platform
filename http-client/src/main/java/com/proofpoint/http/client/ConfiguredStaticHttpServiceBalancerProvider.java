/*
 * Copyright 2021 Proofpoint, Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.http.client.balancing.HttpServiceBalancerConfig;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.http.client.balancing.HttpServiceBalancerStats;
import com.proofpoint.http.client.balancing.HttpServiceBalancerUriConfig;
import com.proofpoint.reporting.ReportCollectionFactory;
import com.proofpoint.reporting.ReportExporter;

import javax.inject.Inject;
import javax.inject.Provider;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

class ConfiguredStaticHttpServiceBalancerProvider implements Provider<HttpServiceBalancer>
{
    private final String type;
    private final Key<HttpServiceBalancerConfig> balancerConfigKey;
    private final Key<HttpServiceBalancerUriConfig> uriConfigKey;
    private ReportExporter reportExporter;
    private ReportCollectionFactory reportCollectionFactory;
    private Injector injector;

    ConfiguredStaticHttpServiceBalancerProvider(String type, Key<HttpServiceBalancerConfig> balancerConfigKey, Key<HttpServiceBalancerUriConfig> uriConfigKey)
    {
        this.type = requireNonNull(type, "type is null");
        this.balancerConfigKey = requireNonNull(balancerConfigKey, "balancerConfigKey is null");
        this.uriConfigKey = requireNonNull(uriConfigKey, "uriConfigKey is null");
    }

    @Inject
    public void setReportExporter(ReportExporter reportExporter)
    {
        requireNonNull(reportExporter, "reportExporter is null");
        this.reportExporter = reportExporter;
    }

    @Inject
    public void setReportCollectionFactory(ReportCollectionFactory reportCollectionFactory)
    {
        requireNonNull(reportCollectionFactory, "reportCollectionFactory is null");
        this.reportCollectionFactory = reportCollectionFactory;
    }

    @Inject
    public void setInjector(Injector injector)
    {
        requireNonNull(injector, "injector is null");
        this.injector = injector;
    }

    @Override
    public HttpServiceBalancer get()
    {
        requireNonNull(type, "type is null");

        Map<String, String> tags = ImmutableMap.of("serviceType", type);
        HttpServiceBalancerStats httpServiceBalancerStats = reportCollectionFactory.createReportCollection(HttpServiceBalancerStats.class, false, "ServiceClient", tags);
        HttpServiceBalancerImpl balancer = new HttpServiceBalancerImpl(format("type=[%s], static", type), httpServiceBalancerStats, injector.getInstance(balancerConfigKey));
        reportExporter.export(balancer, false, "ServiceClient", tags);
        balancer.updateHttpUris(injector.getInstance(uriConfigKey).getUris());
        return balancer;
    }
}
