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
                .setHstsMaxAge(31536000)
                .setIncludeSubDomains(false)
                .setPreload(false)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("jaxrs.hsts.max-age-seconds", "600")
                .put("jaxrs.hsts.include-sub-domains", "true")
                .put("jaxrs.hsts.preload", "true")
                .build();

        JaxrsConfig expected = new JaxrsConfig()
                .setHstsMaxAge(600)
                .setIncludeSubDomains(true)
                .setPreload(true);

        assertFullMapping(properties, expected);
    }
}
