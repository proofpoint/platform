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

import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableMultiset.Builder;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceDescriptorsListener;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import static java.lang.Double.parseDouble;

public class HttpServiceBalancerListenerAdapter
        implements ServiceDescriptorsListener
{

    private final HttpServiceBalancerImpl balancer;

    public HttpServiceBalancerListenerAdapter(HttpServiceBalancerImpl balancer)
    {
        this.balancer = balancer;
    }

    @Override
    public void updateServiceDescriptors(Iterable<ServiceDescriptor> newDescriptors)
    {
        Builder<URI> builder = ImmutableMultiset.builder();

        for (ServiceDescriptor serviceDescriptor : newDescriptors) {
            Map<String, String> properties = serviceDescriptor.getProperties();
            int weight = 1;
            String weightString = properties.get("weight");
            if (weightString != null) {
                try {
                    weight = (int) parseDouble(weightString);
                    if (weight < 0) {
                        weight = 1;
                    }
                }
                catch (NumberFormatException ignored) {
                }
            }

            String https = properties.get("https");
            URI uri = null;
            if (https != null) {
                try {
                    uri = new URI(https);
                }
                catch (URISyntaxException ignored) {
                }
            }

            String http = properties.get("http");
            if (uri == null && http != null) {
                try {
                    uri = new URI(http);
                }
                catch (URISyntaxException ignored) {
                }
            }

            if (uri != null) {
                for (int i = 0; i < weight; i++) {
                    builder.add(uri);
                }
            }
        }

        balancer.updateHttpUris(builder.build());
    }
}
