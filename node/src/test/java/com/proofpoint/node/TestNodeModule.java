package com.proofpoint.node;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.InetAddresses;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestNodeModule
{
    @Test
    public void testDefaultConfig()
    {
        long testStartTime = System.currentTimeMillis();

        ConfigurationFactory configFactory = new ConfigurationFactory(ImmutableMap.<String, String>of());
        Injector injector = Guice.createInjector(new NodeModule(), new ConfigurationModule(configFactory));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        Assert.assertNotNull(nodeInfo);
        Assert.assertNotNull(nodeInfo.getNodeId());
        Assert.assertNotNull(nodeInfo.getInstanceId());

        Assertions.assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        Assert.assertNotNull(nodeInfo.getPublicIp());
        Assert.assertFalse(nodeInfo.getPublicIp().isAnyLocalAddress());
        Assert.assertNotNull(nodeInfo.getBindIp());
        Assert.assertTrue(nodeInfo.getBindIp().isAnyLocalAddress());
        Assertions.assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        Assert.assertNotNull(nodeInfo.toString());
    }

    @Test
    public void testFullConfig()
    {
        long testStartTime = System.currentTimeMillis();

        String nodeId = "nodeId";
        String publicIp = "10.0.0.22";
        ConfigurationFactory configFactory = new ConfigurationFactory(ImmutableMap.<String, String>builder()
                .put("node.id", nodeId)
                .put("node.ip", publicIp)
                .build()
        );

        Injector injector = Guice.createInjector(new NodeModule(), new ConfigurationModule(configFactory));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        Assert.assertNotNull(nodeInfo);
        Assert.assertEquals(nodeInfo.getNodeId(), nodeId);
        Assert.assertNotNull(nodeInfo.getInstanceId());

        Assertions.assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        Assert.assertEquals(nodeInfo.getPublicIp(), InetAddresses.forString(publicIp));
        Assert.assertEquals(nodeInfo.getBindIp(), InetAddresses.forString(publicIp));
        Assertions.assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        Assert.assertNotNull(nodeInfo.toString());
    }
}
