package com.proofpoint.http.client.balancing;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.units.Duration;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.Min;
import org.testng.annotations.Test;

import java.util.Map;

import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;
import static com.proofpoint.testing.ValidationAssertions.assertValidates;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestHttpServiceBalancerConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(HttpServiceBalancerConfig.class)
                .setConsecutiveFailures(5)
                .setMinBackoff(new Duration(5, SECONDS))
                .setMaxBackoff(new Duration(2, MINUTES)));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("consecutive-failures", "4")
                .put("min-backoff", "20ms")
                .put("max-backoff", "50ms")
                .build();

        HttpServiceBalancerConfig expected = new HttpServiceBalancerConfig()
                .setConsecutiveFailures(4)
                .setMinBackoff(new Duration(20, MILLISECONDS))
                .setMaxBackoff(new Duration(50, MILLISECONDS));

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        ConfigAssertions.assertLegacyEquivalence(HttpServiceBalancerConfig.class,
                ImmutableMap.of());
    }

    @Test
    public void testMaxAttemptsBeanValidation()
    {
        assertValidates(new HttpServiceBalancerConfig());
        assertValidates(new HttpServiceBalancerConfig().setConsecutiveFailures(1));
        assertFailsValidation(new HttpServiceBalancerConfig().setConsecutiveFailures(0), "consecutiveFailures", "must be greater than or equal to 1", Min.class);
        assertValidates(new HttpServiceBalancerConfig().setMinBackoff(new Duration(20, MILLISECONDS)).setMaxBackoff(new Duration(20, MILLISECONDS)));
        assertFailsValidation(new HttpServiceBalancerConfig().setMinBackoff(new Duration(20, MILLISECONDS)).setMaxBackoff(new Duration(19, MILLISECONDS)),
                "maxBackoffLessThanMinBackoff", "must be false", AssertFalse.class);
    }
}
