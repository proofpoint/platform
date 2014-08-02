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
package com.proofpoint.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.proofpoint.json.ObjectMapperProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static com.proofpoint.testing.Assertions.assertEqualsIgnoreOrder;
import static org.testng.Assert.assertEquals;

public class TestHealthResource
{
    HealthBeanRegistry healthBeanRegistry;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        healthBeanRegistry = new HealthBeanRegistry();
        HealthExporter exporter = new HealthExporter(healthBeanRegistry);
        exporter.export(null, new TestHealth1());
        exporter.export("two", new TestHealth2());
    }

    @Test
    public void testGetHealthRegistration()
            throws Exception
    {
        HealthResource resource = new HealthResource(healthBeanRegistry);
        ObjectMapper mapper = new ObjectMapperProvider().get().enable(INDENT_OUTPUT);
        String json = mapper.writeValueAsString(
                resource.getHealthRegistrations());

        Map actual = mapper.readValue(json, Map.class);
        assertEquals(actual.keySet(), ImmutableSet.of("checks"), " in json " + json);
        assertEqualsIgnoreOrder((Iterable) actual.get("checks"), ImmutableList.of(
                ImmutableMap.of("description", "Check one"),
                ImmutableMap.of("description", "Check two"),
                ImmutableMap.of("description", "Check three (two)")
        ), "in json " + json);
    }

    @Test
    public void testCheckStatus()
            throws Exception
    {
        HealthResource resource = new HealthResource(healthBeanRegistry);
        ObjectMapper mapper = new ObjectMapperProvider().get().enable(INDENT_OUTPUT);
        String json = mapper.writeValueAsString(
                resource.checkStatus());
        List actual = mapper.readValue(json, List.class);
        assertEqualsIgnoreOrder(actual, ImmutableList.of(
                ImmutableMap.of("description", "Check one", "status", "ERROR", "reason", "Failed check one"),
                ImmutableMap.of("description", "Check two", "status", "OK"),
                ImmutableMap.of("description", "Check three (two)", "status", "UNKNOWN", "reason", "Exception check three")
        ));
    }

    private static class TestHealth1
    {
        @HealthCheck("Check one")
        public String getCheckOne()
        {
            return "Failed check one\nignore second line";
        }

        @HealthCheck("Check two")
        private Object getCheckTwo()
        {
            return null;
        }
    }

    private static class TestHealth2
    {
        @HealthCheck("Check three")
        private String getCheckThree()
        {
            throw new RuntimeException("Exception check three");
        }
    }
}
