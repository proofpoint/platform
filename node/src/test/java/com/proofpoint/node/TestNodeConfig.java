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
package com.proofpoint.node;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.InetAddresses;
import com.proofpoint.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

import static com.proofpoint.configuration.testing.ConfigAssertions.assertFullMapping;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertLegacyEquivalence;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertRecordedDefaults;

public class TestNodeConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(ConfigAssertions.recordDefaults(NodeConfig.class)
                .setEnvironment(null)
                .setPool("general")
                .setNodeId(null)
                .setNodeInternalIp((String) null)
                .setNodeInternalHostname(null)
                .setNodeBindIp((String) null)
                .setNodeExternalAddress(null)
                .setLocation(null)
                .setBinarySpec(null)
                .setConfigSpec(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "environment")
                .put("node.pool", "pool")
                .put("node.id", "nodeId")
                .put("node.ip", "10.9.8.7")
                .put("node.hostname", "local.hostname")
                .put("node.bind-ip", "10.11.12.13")
                .put("node.external-address", "external")
                .put("node.location", "location")
                .put("node.binary-spec", "binary")
                .put("node.config-spec", "config")
                .build();

        NodeConfig expected = new NodeConfig()
                .setEnvironment("environment")
                .setPool("pool")
                .setNodeId("nodeId")
                .setNodeInternalIp(InetAddresses.forString("10.9.8.7"))
                .setNodeInternalHostname("local.hostname")
                .setNodeBindIp(InetAddresses.forString("10.11.12.13"))
                .setNodeExternalAddress("external")
                .setLocation("location")
                .setBinarySpec("binary")
                .setConfigSpec("config");

        assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                 .put("node.environment", "environment")
                 .build();

        assertLegacyEquivalence(NodeConfig.class, currentProperties);
    }

}
