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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.Map;

import static com.proofpoint.json.testing.JsonTester.assertJsonEncode;
import static com.proofpoint.reporting.HealthCheckRegistrationRepresentation.healthCheckRegistrationRepresentation;
import static com.proofpoint.reporting.HealthRegistrationsRepresentation.healthRegistrationsRepresentation;

public class TestHealthRegistrationsRepresentation
{
    @Test
    public void testJsonEncode()
    {
        HealthRegistrationsRepresentation actual = healthRegistrationsRepresentation(ImmutableList.of(
                healthCheckRegistrationRepresentation("Some description"),
                healthCheckRegistrationRepresentation("Other description")
        ));
        Map<String, ImmutableList<ImmutableMap<String, String>>> expected = ImmutableMap.of("checks", ImmutableList.of(
                ImmutableMap.of("description", "Some description"),
                ImmutableMap.of("description", "Other description")
        ));
        assertJsonEncode(actual, expected);
    }
}
