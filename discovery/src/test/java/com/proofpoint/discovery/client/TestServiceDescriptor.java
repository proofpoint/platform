package com.proofpoint.discovery.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.proofpoint.json.JsonCodec;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.UUID;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestServiceDescriptor
{
    private final JsonCodec<ServiceDescriptor> serviceDescriptorCodec = jsonCodec(ServiceDescriptor.class);

    @Test
    public void testJsonDecode()
            throws Exception
    {
        ServiceDescriptor expected = new ServiceDescriptor(UUID.fromString("12345678-1234-1234-1234-123456789012"),
                "node",
                "type",
                "pool",
                "location",
                ServiceState.RUNNING, ImmutableMap.of("a", "apple", "b", "banana"));

        String json = Resources.toString(Resources.getResource("service-descriptor.json"), Charsets.UTF_8);
        ServiceDescriptor actual = serviceDescriptorCodec.fromJson(json);

        assertEquals(actual, expected);
        assertEquals(actual.getId(), expected.getId());
        assertEquals(actual.getNodeId(), expected.getNodeId());
        assertEquals(actual.getType(), expected.getType());
        assertEquals(actual.getPool(), expected.getPool());
        assertEquals(actual.getLocation(), expected.getLocation());
        assertEquals(actual.getProperties(), expected.getProperties());
    }

    @Test
    public void testValidator()
    {
        UUID uuid = UUID.randomUUID();
        Map<String, String> properties = ImmutableMap.of("a", "b");

        assertTrue(new ServiceDescriptor(uuid, "node", "type", "pool", "location", ServiceState.RUNNING, properties).isValid());
        assertFalse(new ServiceDescriptor(null, "node", "type", "pool", "location", ServiceState.RUNNING, properties).isValid());
        assertFalse(new ServiceDescriptor(uuid, null, "type", "pool", "location", ServiceState.RUNNING, properties).isValid());
        assertFalse(new ServiceDescriptor(uuid, "", "type", "pool", "location", ServiceState.RUNNING, properties).isValid());
        assertFalse(new ServiceDescriptor(uuid, "node", null, "pool", "location", ServiceState.RUNNING, properties).isValid());
        assertFalse(new ServiceDescriptor(uuid, "node", "", "pool", "location", ServiceState.RUNNING, properties).isValid());
        assertFalse(new ServiceDescriptor(uuid, "node", "type", null, "location", ServiceState.RUNNING, properties).isValid());
        assertFalse(new ServiceDescriptor(uuid, "node", "type", "", "location", ServiceState.RUNNING, properties).isValid());
        assertFalse(new ServiceDescriptor(uuid, "node", "type", "pool", null, ServiceState.RUNNING, properties).isValid());
        assertFalse(new ServiceDescriptor(uuid, "node", "type", "pool", "", ServiceState.RUNNING, properties).isValid());
        assertFalse(new ServiceDescriptor(uuid, "node", "type", "pool", "location", null, properties).isValid());
    }

    @Test
    public void testToString()
    {
        assertNotNull(new ServiceDescriptor(UUID.fromString("12345678-1234-1234-1234-123456789012"),
                "node",
                "type",
                "pool",
                "location",
                ServiceState.RUNNING, ImmutableMap.of("a", "apple", "b", "banana")));
    }

    @Test
    public void testEquivalence()
    {
        UUID serviceA = UUID.randomUUID();
        UUID serviceB = UUID.randomUUID();
        equivalenceTester()
                .addEquivalentGroup(
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node-X", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type-X", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool-X", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location-X", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a-X", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple-X"))
                )
                .addEquivalentGroup(
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node-X", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type-X", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool-X", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location-X", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a-X", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple-X"))
                )
                .check();
    }

}
