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

import com.google.common.collect.ImmutableMap;
import com.proofpoint.reporting.HealthStatusRepresentation.Status;
import org.testng.annotations.Test;

import java.util.Map;

import static com.proofpoint.json.testing.JsonTester.assertJsonEncode;

public class TestHealthStatusRepresentation
{
    @Test
    public void testJsonEncodeOk()
    {
        HealthStatusRepresentation actual = new HealthStatusRepresentation("Test check", Status.OK, null);
        Map<String, String> expected = ImmutableMap.of("description", "Test check", "status", "OK");
        assertJsonEncode(actual, expected);
    }

    @Test
    public void testJsonEncodeError()
    {
        HealthStatusRepresentation actual = new HealthStatusRepresentation("Test check 2", Status.ERROR, "some failure");
        Map<String, String> expected = ImmutableMap.of("description", "Test check 2", "status", "ERROR", "reason", "some failure");
        assertJsonEncode(actual, expected);
    }

    @Test
    public void testJsonEncodeUnknown()
    {
        HealthStatusRepresentation actual = new HealthStatusRepresentation("Test check 3", Status.UNKNOWN, "some other failure");
        Map<String, String> expected = ImmutableMap.of("description", "Test check 3", "status", "UNKNOWN", "reason", "some other failure");
        assertJsonEncode(actual, expected);
    }
}
