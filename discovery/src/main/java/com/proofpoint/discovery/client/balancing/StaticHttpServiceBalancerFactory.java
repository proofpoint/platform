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

import com.google.common.collect.ImmutableMap;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.http.client.balancing.HttpServiceBalancerConfig;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.http.client.balancing.HttpServiceBalancerStats;
import com.proofpoint.http.client.balancing.HttpServiceBalancerUriConfig;
import com.proofpoint.reporting.ReportCollectionFactory;
import com.proofpoint.reporting.ReportExporter;

import javax.inject.Inject;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class StaticHttpServiceBalancerFactory
{
    private final ReportExporter reportExporter;
    private final ReportCollectionFactory reportCollectionFactory;

    @Inject
    public StaticHttpServiceBalancerFactory(
            ReportExporter reportExporter,
            ReportCollectionFactory reportCollectionFactory)
    {
        this.reportExporter = requireNonNull(reportExporter, "reportExporter is null");
        this.reportCollectionFactory = requireNonNull(reportCollectionFactory, "reportCollectionFactory is null");
    }

    HttpServiceBalancer createHttpServiceBalancer(String type, HttpServiceBalancerUriConfig httpServiceBalancerUriConfig, HttpServiceBalancerConfig balancerConfig)
    {
        requireNonNull(type, "type is null");
        requireNonNull(balancerConfig, "balancerConfig is null");

        Map<String, String> tags = ImmutableMap.of("serviceType", type);
        HttpServiceBalancerStats httpServiceBalancerStats = reportCollectionFactory.createReportCollection(HttpServiceBalancerStats.class, false, "ServiceClient", tags);
        HttpServiceBalancerImpl balancer = new HttpServiceBalancerImpl(format("type=[%s]", type), httpServiceBalancerStats, balancerConfig);
        balancer.updateHttpUris(httpServiceBalancerUriConfig.getUris());
        reportExporter.export(balancer, false, "ServiceClient", tags);

        return balancer;
    }
}
