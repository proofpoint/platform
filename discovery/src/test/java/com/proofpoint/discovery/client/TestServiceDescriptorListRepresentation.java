package com.proofpoint.discovery.client;

import com.google.common.collect.ImmutableList;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;

public class TestServiceDescriptorListRepresentation
{
    private Validator validator;

    private final JsonCodec<ServiceDescriptorListRepresentation> serviceDescriptorListCodec = JsonCodec.jsonCodec(ServiceDescriptorListRepresentation.class);

    @BeforeMethod
    public void setup()
    {
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    public void testValid()
    {
        String json = "{\"environment\":\"test\",\"services\":[{},{}]}";
        test(json, null);
    }

    @Test
    public void testMissingEnvironment()
    {
        String json = "{\"services\":[{},{}]}";
        test(json, "Invalid ServiceDescriptorList: environment is null");
    }

    @Test
    public void testEmptyEnvironment()
    {
        String json = "{\"environment\":\"\",\"services\":[{},{}]}";
        test(json, "Invalid ServiceDescriptorList: environment is empty");
    }

    @Test
    public void testMissingServices()
    {
        String json = "{\"environment\":\"test\"}";
        test(json, "Invalid ServiceDescriptorList: serviceDescriptors is null");
    }

    @Test
    public void testEquivalence()
    {
        UUID serviceA = UUID.randomUUID();
        UUID serviceB = UUID.randomUUID();
        Map<String, String> properties = ImmutableMap.of("a", "b");
        List<ServiceDescriptorRepresentation> sdrA = ImmutableList.of(new ServiceDescriptorRepresentation(serviceA, "node", "type", "pool", "location", ServiceState.RUNNING, properties));
        List<ServiceDescriptorRepresentation> sdrB = ImmutableList.of(new ServiceDescriptorRepresentation(serviceB, "node", "type", "pool", "location", ServiceState.RUNNING, properties));
        equivalenceTester()
                .addEquivalentGroup(new ServiceDescriptorListRepresentation("A", sdrA))
                .addEquivalentGroup(new ServiceDescriptorListRepresentation("A", sdrB))
                .addEquivalentGroup(new ServiceDescriptorListRepresentation("B", sdrB))
                .addEquivalentGroup(new ServiceDescriptorListRepresentation("B", sdrA))
                .check();
    }

    private void test(String json, String message)
    {
        ServiceDescriptorListRepresentation descriptor = serviceDescriptorListCodec.fromJson(json);
        Collection<ConstraintViolation<ServiceDescriptorListRepresentation>> violations = validator.validate(descriptor);
        if (message != null) {
            Assert.assertEquals(violations.size(), 1);
            Assert.assertEquals(Iterables.getFirst(violations, null).getMessage(), message);
        }
        else {
            Assert.assertEquals(violations.size(), 0);
        }
    }
}
