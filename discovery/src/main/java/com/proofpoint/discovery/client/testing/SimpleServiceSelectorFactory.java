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

import com.google.inject.Inject;
import com.proofpoint.discovery.client.DiscoveryLookupClient;
import com.proofpoint.discovery.client.ServiceSelector;
import com.proofpoint.discovery.client.ServiceSelectorConfig;
import com.proofpoint.discovery.client.ServiceSelectorFactory;
import com.proofpoint.node.NodeInfo;

import static java.util.Objects.requireNonNull;

public class SimpleServiceSelectorFactory implements ServiceSelectorFactory
{
    private final DiscoveryLookupClient lookupClient;
    private final NodeInfo nodeInfo;

    @Inject
    public SimpleServiceSelectorFactory(DiscoveryLookupClient lookupClient, NodeInfo nodeInfo)
    {
        requireNonNull(lookupClient, "client is null");
        requireNonNull(nodeInfo, "nodeInfo is null");

        this.lookupClient = lookupClient;
        this.nodeInfo = nodeInfo;
    }

    @Override
    public ServiceSelector createServiceSelector(String type, ServiceSelectorConfig selectorConfig)
    {
        requireNonNull(type, "type is null");
        requireNonNull(selectorConfig, "selectorConfig is null");

        return new SimpleServiceSelector(type, selectorConfig, nodeInfo, lookupClient);
    }
}
