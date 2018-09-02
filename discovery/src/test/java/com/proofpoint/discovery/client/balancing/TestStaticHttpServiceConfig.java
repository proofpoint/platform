/*
 * Copyright 2017 Proofpoint, Inc.
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
package com.proofpoint.discovery.client.balancing;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.discovery.client.balancing.StaticHttpServiceConfig.UriMultiset;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Map;

import static com.proofpoint.configuration.testing.ConfigAssertions.assertLegacyEquivalence;

public class TestStaticHttpServiceConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(StaticHttpServiceConfig.class)
                .setUris(UriMultiset.of()));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("uri", "http://10.20.30.40:4111,http://50.60.70.80:9125")
                .build();

        StaticHttpServiceConfig expected = new StaticHttpServiceConfig()
                .setUris(UriMultiset.of(URI.create("http://10.20.30.40:4111"), URI.create("http://50.60.70.80:9125")));

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        assertLegacyEquivalence(StaticHttpServiceConfig.class,
                ImmutableMap.of());
    }
}
