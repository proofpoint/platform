/*
 * Copyright 2014 Proofpoint, Inc.
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
package com.proofpoint.reporting;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceSelector;
import com.proofpoint.discovery.client.ServiceType;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Request.Builder;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.reporting.HealthResult.Status;

import javax.annotation.PostConstruct;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Objects.firstNonNull;
import static com.proofpoint.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static com.proofpoint.http.client.Request.Builder.preparePost;
import static com.proofpoint.reporting.HealthReport.healthReport;
import static com.proofpoint.reporting.HealthResult.healthResult;
import static java.util.Objects.requireNonNull;

class HealthCollector
{
    private final Logger log = Logger.get(getClass());
    private final ScheduledExecutorService collectionExecutorService;
    private final HealthBeanRegistry healthBeanRegistry;
    private final HttpClient httpClient;
    private final ServiceSelector selector;
    private final NodeInfo nodeInfo;
    private final CurrentTimeSecsProvider currentTimeSecsProvider;
    private final JsonCodec<HealthReport> healthReportCodec;
    private final String servicePrefix;

    @Inject
    HealthCollector(NodeInfo nodeInfo,
            HealthBeanRegistry healthBeanRegistry,
            @ForHealthCollector HttpClient httpClient,
            @ServiceType("monitoring-acceptor") ServiceSelector selector,
            CurrentTimeSecsProvider currentTimeSecsProvider,
            @ForHealthCollector ScheduledExecutorService collectionExecutorService,
            JsonCodec<HealthReport> healthReportCodec)
    {
        this.nodeInfo = requireNonNull(nodeInfo, "nodeInfo is null");
        this.healthBeanRegistry = requireNonNull(healthBeanRegistry, "healthBeanRegistry is null");
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
        this.selector = requireNonNull(selector, "selector is null");
        this.currentTimeSecsProvider = requireNonNull(currentTimeSecsProvider, "currentTimeSecsProvider is null");
        this.collectionExecutorService = requireNonNull(collectionExecutorService, "collectionExecutorService is null");
        this.healthReportCodec = requireNonNull(healthReportCodec, "healthReportCodec is null");
        servicePrefix = nodeInfo.getApplication() + " ";
    }

    @PostConstruct
    public void start()
    {
        collectionExecutorService.scheduleAtFixedRate(this::collectData, 1, 1, TimeUnit.MINUTES);
    }

    private void collectData()
    {
        ImmutableList.Builder<HealthResult> builder = ImmutableList.builder();
        for (Entry<String, HealthBeanAttribute> healthBeanAttributeEntry : healthBeanRegistry.getHealthAttributes().entrySet()) {
            Status status = Status.CRITICAL;
            String message;

            try {
                message = healthBeanAttributeEntry.getValue().getValue();
            }
            catch (AttributeNotFoundException e) {
                status = Status.UNKNOWN;
                message = "Health check attribute not found";
            }
            catch (MBeanException | ReflectionException e) {
                status = Status.UNKNOWN;
                message = e.getCause().getMessage();
            }

            if (message == null) {
                status = Status.OK;
            }

            String service = servicePrefix + healthBeanAttributeEntry.getKey();
            builder.add(healthResult(service, status, message));
        }

        List<HealthResult> results = builder.build();
        if (results.isEmpty()) {
            return;
        }

        Builder requestBuilder = preparePost()
                .setHeader("Content-Type", "application/json")
                .setBodySource(jsonBodyGenerator(healthReportCodec,
                        healthReport(nodeInfo.getInternalHostname(), currentTimeSecsProvider.getCurrentTimeSecs(), results)
                ));

        sendReport(requestBuilder);
    }

    private void sendReport(Builder requestBuilder)
    {
        for (ServiceDescriptor descriptor : selector.selectAllServices()) {
            String uriBase = firstNonNull(descriptor.getProperties().get("https"), descriptor.getProperties().get("http"));
            if (uriBase == null) {
                continue;
            }

            URI uri;
            try {
                uri = new URI(uriBase);
            }
            catch (URISyntaxException e) {
                continue;
            }

            if (!uri.toString().endsWith("/")) {
                uri = URI.create(uri.toString() + '/');
            }
            uri = uri.resolve("v1/monitoring/service");

            Request subRequest = requestBuilder.setUri(uri).build();

            httpClient.executeAsync(subRequest, new ResponseHandler<Void, RuntimeException>()
            {
                @Override
                public Void handleException(Request request, Exception exception)
                {
                    log.warn("Sending health status to %s failed: %s", request.getUri(), exception.getMessage());
                    return null;
                }

                @Override
                public Void handle(Request request, Response response)
                {
                    if (response.getStatusCode() / 100 != 2) {
                        log.warn("Sending health status to %s failed: %s status code", request.getUri(), response.getStatusCode());
                    }
                    return null;
                }
            });
        }
    }
}
