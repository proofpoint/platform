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

import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.io.Resources;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

import static com.proofpoint.testing.Assertions.assertEqualsIgnoreOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;

@SuppressWarnings({"unchecked", "deprecation"})
public class TestServiceInventory
{
    private ServiceInventoryConfig serviceInventoryConfig;
    private DiscoveryClientConfig discoveryClientConfig;
    @Mock
    private HttpServiceBalancerImpl balancer;
    @Mock
    private DiscoveryAddressLookup discoveryAddressLookup;
    @Captor
    private ArgumentCaptor<Multiset<URI>> uriMultisetCaptor;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        initMocks(this);
        serviceInventoryConfig = new ServiceInventoryConfig();
        discoveryClientConfig = new DiscoveryClientConfig();
        when(discoveryAddressLookup.get()).thenThrow(new UnknownHostException("discovery"));
    }

    @Test
    public void testNullServiceInventory()
    {
        ServiceInventory serviceInventory = createServiceInventory();

        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 0);
        serviceInventory.updateServiceInventory();
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 0);

        verify(balancer, never()).updateHttpUris(any(Set.class));
    }

    @Test
    public void testDeprecatedServiceInventory()
    {
        discoveryClientConfig.setDiscoveryServiceURI(URI.create("https://example.com:4111"));
        ServiceInventory serviceInventory = createServiceInventory();

        verify(balancer).updateHttpUris(uriMultisetCaptor.capture());
        assertEquals(Iterables.size(uriMultisetCaptor.getValue()), 1);

        URI uri = Iterables.getOnlyElement(uriMultisetCaptor.getValue());
        assertEquals(uri, URI.create("https://example.com:4111"));

        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 0);
        serviceInventory.updateServiceInventory();
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 0);
    }

    @Test
    public void testFileServiceInventory()
            throws Exception
    {
        serviceInventoryConfig.setServiceInventoryUri(Resources.getResource("service-inventory.json").toURI());
        discoveryClientConfig.setDiscoveryServiceURI(URI.create("http://example.com:4111"));

        ServiceInventory serviceInventory = createServiceInventory();

        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 3);
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery")), 2);
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery", "general")), 2);

        verify(balancer).updateHttpUris(uriMultisetCaptor.capture());
        ImmutableMultiset<URI> expectedUris = ImmutableMultiset.of(URI.create("http://localhost:8411"), URI.create("http://localhost:8412"));
        assertEqualsIgnoreOrder(uriMultisetCaptor.getValue(), expectedUris);

        serviceInventory.updateServiceInventory();
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 3);
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery")), 2);
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery", "general")), 2);

        verify(balancer, times(2)).updateHttpUris(uriMultisetCaptor.capture());
        assertEqualsIgnoreOrder(uriMultisetCaptor.getValue(), expectedUris);
    }

    @Test
    public void testDnsServiceInventory()
            throws Exception
    {
        discoveryAddressLookup = mock(DiscoveryAddressLookup.class);
        when(discoveryAddressLookup.get()).thenReturn(List.of(
                InetAddress.getByName("1.2.3.4"),
                InetAddress.getByName("1:2:3:4:5:6:7:8")
        ));

        ServiceInventory serviceInventory = createServiceInventory();

        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 2);
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery")), 2);
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery", "general")), 2);

        verify(balancer).updateHttpUris(uriMultisetCaptor.capture());
        ImmutableMultiset<URI> expectedUris = ImmutableMultiset.of(URI.create("http://1.2.3.4:4111"), URI.create("http://[1:2:3:4:5:6:7:8]:4111"));
        assertEqualsIgnoreOrder(uriMultisetCaptor.getValue(), expectedUris);

        when(discoveryAddressLookup.get()).thenReturn(List.of(
                InetAddress.getByName("5.6.7.8"),
                InetAddress.getByName("1:2:3:4:5:6:7:8")
        ));
        serviceInventory.updateServiceInventory();

        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 2);
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery")), 2);
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery", "general")), 2);

        verify(balancer, times(2)).updateHttpUris(uriMultisetCaptor.capture());
        expectedUris = ImmutableMultiset.of(URI.create("http://5.6.7.8:4111"), URI.create("http://[1:2:3:4:5:6:7:8]:4111"));
        assertEqualsIgnoreOrder(uriMultisetCaptor.getValue(), expectedUris);
    }

    private ServiceInventory createServiceInventory()
    {
        return new ServiceInventory(serviceInventoryConfig,
                discoveryClientConfig, new NodeInfo("test"),
                JsonCodec.jsonCodec(ServiceDescriptorsRepresentation.class),
                balancer, discoveryAddressLookup);
    }
}
