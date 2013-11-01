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
import org.testng.annotations.Test;

import java.util.Map;

import static com.proofpoint.configuration.testing.ConfigAssertions.assertFullMapping;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertLegacyEquivalence;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static com.proofpoint.configuration.testing.ConfigAssertions.recordDefaults;

public class TestReportClientConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(ReportClientConfig.class)
                .setEnabled(true)
                .setTags(ImmutableMap.<String, String>of()));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("reporting.enabled", "false")
                .put("reporting.tag.foo", "bar")
                .build();

        ReportClientConfig expected = new ReportClientConfig()
                .setEnabled(false)
                .setTags(ImmutableMap.of("foo", "bar"));

        assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("reporting.tag.foo", "bar")
                .build();

        Map<String, String> olderProperties = new ImmutableMap.Builder<String, String>()
                .put("report.tag.foo", "bar")
                .build();

        assertLegacyEquivalence(ReportClientConfig.class, properties, olderProperties);
    }
}
