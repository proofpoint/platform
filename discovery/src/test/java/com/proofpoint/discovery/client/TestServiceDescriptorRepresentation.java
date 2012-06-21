package com.proofpoint.discovery.client;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.proofpoint.json.JsonCodec;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;

public class TestServiceDescriptorRepresentation
{
    private Validator validator;

    private final JsonCodec<ServiceDescriptorRepresentation> serviceDescriptorCodec = JsonCodec.jsonCodec(ServiceDescriptorRepresentation.class);

    @BeforeMethod
    public void setup()
    {
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    public void testValid()
    {
        String json = "{\"id\":\"12345678-1234-1234-1234-123456789012\",\"nodeId\":\"nodeId\",\"type\":\"type\",\"pool\":\"pool\",\"location\":\"location\",\"state\":\"RUNNING\",\"properties\":{\"a\":\"b\"}}";
        test(json, null);
    }

    @Test
    public void testMissingId()
    {
        String json = "{\"nodeId\":\"nodeId\",\"type\":\"type\",\"pool\":\"pool\",\"location\":\"location\",\"state\":\"RUNNING\",\"properties\":{\"a\":\"b\"}}";
        test(json, "Invalid ServiceDescriptor: id is null");
    }

    @Test
    public void testMissingNodeId()
    {
        String json = "{\"id\":\"12345678-1234-1234-1234-123456789012\",\"type\":\"type\",\"pool\":\"pool\",\"location\":\"location\",\"state\":\"RUNNING\",\"properties\":{\"a\":\"b\"}}";
        test(json, "Invalid ServiceDescriptor: nodeId is null");
    }

    @Test
    public void testEmptyNodeId()
    {
        String json = "{\"id\":\"12345678-1234-1234-1234-123456789012\",\"nodeId\":\"\",\"type\":\"type\",\"pool\":\"pool\",\"location\":\"location\",\"state\":\"RUNNING\",\"properties\":{\"a\":\"b\"}}";
        test(json, "Invalid ServiceDescriptor: nodeId is empty");
    }

    @Test
    public void testMissingType()
    {
        String json = "{\"id\":\"12345678-1234-1234-1234-123456789012\",\"nodeId\":\"nodeId\",\"pool\":\"pool\",\"location\":\"location\",\"state\":\"RUNNING\",\"properties\":{\"a\":\"b\"}}";
        test(json, "Invalid ServiceDescriptor: type is null");
    }

    @Test
    public void testEmptyType()
    {
        String json = "{\"id\":\"12345678-1234-1234-1234-123456789012\",\"nodeId\":\"nodeId\",\"type\":\"\",\"pool\":\"pool\",\"location\":\"location\",\"state\":\"RUNNING\",\"properties\":{\"a\":\"b\"}}";
        test(json, "Invalid ServiceDescriptor: type is empty");
    }

    @Test
    public void testMissingPool()
    {
        String json = "{\"id\":\"12345678-1234-1234-1234-123456789012\",\"nodeId\":\"nodeId\",\"type\":\"type\",\"location\":\"location\",\"state\":\"RUNNING\",\"properties\":{\"a\":\"b\"}}";
        test(json, "Invalid ServiceDescriptor: pool is null");
    }

    @Test
    public void testEmptyPool()
    {
        String json = "{\"id\":\"12345678-1234-1234-1234-123456789012\",\"nodeId\":\"nodeId\",\"type\":\"type\",\"pool\":\"\",\"location\":\"location\",\"state\":\"RUNNING\",\"properties\":{\"a\":\"b\"}}";
        test(json, "Invalid ServiceDescriptor: pool is empty");
    }

    @Test
    public void testMissingLocation()
    {
        String json = "{\"id\":\"12345678-1234-1234-1234-123456789012\",\"nodeId\":\"nodeId\",\"type\":\"type\",\"pool\":\"pool\",\"state\":\"RUNNING\",\"properties\":{\"a\":\"b\"}}";
        test(json, "Invalid ServiceDescriptor: location is null");
    }

    @Test
    public void testEmptyLocation()
    {
        String json = "{\"id\":\"12345678-1234-1234-1234-123456789012\",\"nodeId\":\"nodeId\",\"type\":\"type\",\"pool\":\"pool\",\"location\":\"\",\"state\":\"RUNNING\",\"properties\":{\"a\":\"b\"}}";
        test(json, "Invalid ServiceDescriptor: location is empty");
    }

    @Test
    public void testMissingState()
    {
        String json = "{\"id\":\"12345678-1234-1234-1234-123456789012\",\"nodeId\":\"nodeId\",\"type\":\"type\",\"pool\":\"pool\",\"location\":\"location\",\"properties\":{\"a\":\"b\"}}";
        test(json, "Invalid ServiceDescriptor: state is null");
    }

    @Test
    public void testMissingProperties()
    {
        String json = "{\"id\":\"12345678-1234-1234-1234-123456789012\",\"nodeId\":\"nodeId\",\"type\":\"type\",\"pool\":\"pool\",\"location\":\"location\",\"state\":\"RUNNING\"}";
        test(json, "Invalid ServiceDescriptor: properties is null");
    }

    @Test
    public void testEquivalence()
    {
        UUID serviceA = UUID.randomUUID();
        UUID serviceB = UUID.randomUUID();
        Map<String, String> propertiesA = ImmutableMap.of("a", "aa");
        Map<String, String> propertiesB = ImmutableMap.of("b", "bb");
        equivalenceTester()
                .addEquivalentGroup(new ServiceDescriptorRepresentation(serviceA, "node", "type", "pool", "location", ServiceState.RUNNING, propertiesA))
                .addEquivalentGroup(new ServiceDescriptorRepresentation(serviceB, "node", "type", "pool", "location", ServiceState.RUNNING, propertiesA))
                .addEquivalentGroup(new ServiceDescriptorRepresentation(serviceA, "node-X", "type", "pool", "location", ServiceState.RUNNING, propertiesA))
                .addEquivalentGroup(new ServiceDescriptorRepresentation(serviceA, "node", "type-X", "pool", "location", ServiceState.RUNNING, propertiesA))
                .addEquivalentGroup(new ServiceDescriptorRepresentation(serviceA, "node", "type", "pool-X", "location", ServiceState.RUNNING, propertiesA))
                .addEquivalentGroup(new ServiceDescriptorRepresentation(serviceA, "node", "type", "pool", "location-X", ServiceState.RUNNING, propertiesA))
                .addEquivalentGroup(new ServiceDescriptorRepresentation(serviceA, "node", "type", "pool", "location", ServiceState.STOPPED, propertiesA))
                .addEquivalentGroup(new ServiceDescriptorRepresentation(serviceA, "node", "type", "pool", "location", ServiceState.RUNNING, propertiesB))
                .check();
    }

    private void test(String json, String message)
    {
        ServiceDescriptorRepresentation descriptor = serviceDescriptorCodec.fromJson(json);
        Collection<ConstraintViolation<ServiceDescriptorRepresentation>> violations = validator.validate(descriptor);
        if (message != null) {
            Assert.assertEquals(violations.size(), 1);
            Assert.assertEquals(Iterables.getFirst(violations, null).getMessage(), message);
        }
        else {
            Assert.assertEquals(violations.size(), 0);
        }
    }
}
