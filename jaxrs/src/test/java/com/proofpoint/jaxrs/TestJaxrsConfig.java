package com.proofpoint.jaxrs;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.units.Duration;
import com.proofpoint.units.MinDuration;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.configuration.testing.ConfigAssertions.assertFullMapping;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;
import static com.proofpoint.testing.ValidationAssertions.assertValidates;

public class TestJaxrsConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(ConfigAssertions.recordDefaults(JaxrsConfig.class)
                .setHstsMaxAge(null)
                .setIncludeSubDomains(false)
                .setPreload(false)
                .setOverrideMethodFilter(true)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("jaxrs.hsts.max-age", "600s")
                .put("jaxrs.hsts.include-sub-domains", "true")
                .put("jaxrs.hsts.preload", "true")
                .put("testing.jaxrs.override-method-filter", "false")
                .build();

        JaxrsConfig expected = new JaxrsConfig()
                .setHstsMaxAge(new Duration(600, TimeUnit.SECONDS))
                .setIncludeSubDomains(true)
                .setPreload(true)
                .setOverrideMethodFilter(false);

        assertFullMapping(properties, expected);
    }

    @Test
    public void testValidations()
    {
        assertValidates(new JaxrsConfig()
                .setHstsMaxAge(new Duration(1, TimeUnit.MINUTES)));
        assertValidates(new JaxrsConfig());
        assertFailsValidation(new JaxrsConfig().setHstsMaxAge(new Duration(1, TimeUnit.SECONDS)),
                "hstsMaxAge", "must be greater than or equal to 1m", MinDuration.class);
    }
}
