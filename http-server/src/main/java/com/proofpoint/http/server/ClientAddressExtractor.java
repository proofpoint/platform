/*
 * Copyright 2016 Proofpoint, Inc.
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
package com.proofpoint.http.server;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Enumeration;

public class ClientAddressExtractor
{
    private static final CidrSet PRIVATE_NETWORKS = CidrSet.fromString(
            "127.0.0.0/8," +
            "169.254.0.0/16," +
            "192.168.0.0/16," +
            "172.16.0.0/12," +
            "10.0.0.0/8," +
            "100.64.0.0/10");

    private final CidrSet trustedNetworks;

    public ClientAddressExtractor()
    {
        trustedNetworks = PRIVATE_NETWORKS;
    }

    @Inject
    public ClientAddressExtractor(InternalNetworkConfig config)
    {
        trustedNetworks = PRIVATE_NETWORKS.union(config.getInternalNetworks());
    }

    public String clientAddressFor(HttpServletRequest request)
    {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (Enumeration<String> e = request.getHeaders("X-FORWARDED-FOR"); e != null && e.hasMoreElements(); ) {
            String forwardedFor = e.nextElement();
            builder.addAll(Splitter.on(',').trimResults().omitEmptyStrings().split(forwardedFor));
        }
        if (request.getRemoteAddr() != null) {
            builder.add(request.getRemoteAddr());
        }
        String clientAddress = null;
        ImmutableList<String> clientAddresses = builder.build();
        for (String address : Lists.reverse(clientAddresses)) {
            try {
                if (!trustedNetworks.containsAddress(InetAddresses.forString(address))) {
                    clientAddress = address;
                    break;
                }
                clientAddress = address;
            }
            catch (IllegalArgumentException ignored) {
                break;
            }
        }
        if (clientAddress == null) {
            clientAddress = request.getRemoteAddr();
        }
        return clientAddress;
    }
}
