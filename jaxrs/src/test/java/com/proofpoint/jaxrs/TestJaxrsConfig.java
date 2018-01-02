package com.proofpoint.jaxrs;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

import static com.proofpoint.configuration.testing.ConfigAssertions.assertFullMapping;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertRecordedDefaults;

public class TestJaxrsConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(ConfigAssertions.recordDefaults(JaxrsConfig.class)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .build();

        JaxrsConfig expected = new JaxrsConfig();

        assertFullMapping(properties, expected);
    }
}
