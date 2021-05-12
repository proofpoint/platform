/*
 * Copyright 2017 Proofpoint, Inc.
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
import com.proofpoint.http.client.balancing.HttpServiceAttempt;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.http.client.balancing.HttpServiceBalancerConfig;
import com.proofpoint.http.client.balancing.HttpServiceBalancerUriConfig;
import com.proofpoint.http.client.balancing.HttpServiceBalancerUriConfig.UriMultiset;
import com.proofpoint.reporting.ReportCollectionFactory;
import com.proofpoint.reporting.ReportExporter;
import com.proofpoint.stats.SparseTimeStat;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

import java.net.URI;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertEquals;

public class TestStaticHttpServiceBalancerFactory
{
    @Test
    public void testCreateBalancer()
    {
        ReportExporter reportExporter = mock(ReportExporter.class);
        ReportCollectionFactory reportCollectionFactory = new ReportCollectionFactory(reportExporter);
        StaticHttpServiceBalancerFactory factory = new StaticHttpServiceBalancerFactory(reportExporter, reportCollectionFactory);

        HttpServiceBalancer balancer = factory.createHttpServiceBalancer(
                "foo",
                new HttpServiceBalancerUriConfig().setUris(UriMultiset.valueOf("http://invalid.invalid")),
                new HttpServiceBalancerConfig());

        HttpServiceAttempt attempt = balancer.createAttempt();
        assertEquals(attempt.getUri(), URI.create("http://invalid.invalid"));

        attempt.markGood();
        verify(reportExporter).export(balancer, false, "ServiceClient", ImmutableMap.of("serviceType", "foo"));
        ArgumentCaptor<SparseTimeStat> statsCaptor = ArgumentCaptor.forClass(SparseTimeStat.class);
        verify(reportExporter).export(statsCaptor.capture(), eq(false), eq("ServiceClient.RequestTime"),
                eq(ImmutableMap.of("serviceType", "foo", "targetUri", "http://invalid.invalid", "status", "success")));
        verifyNoMoreInteractions(reportExporter);
    }
}
