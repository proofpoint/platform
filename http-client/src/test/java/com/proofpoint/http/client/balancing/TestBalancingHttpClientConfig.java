package com.proofpoint.http.client.balancing;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.testing.ValidationAssertions;
import com.proofpoint.units.Duration;
import org.testng.annotations.Test;

import javax.validation.constraints.AssertFalse;
import javax.validation.constraints.Min;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;
import static com.proofpoint.testing.ValidationAssertions.assertValidates;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestBalancingHttpClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(BalancingHttpClientConfig.class)
                .setMaxAttempts(3)
                .setMinBackoff(new Duration(10, MILLISECONDS))
                .setMaxBackoff(new Duration(10, SECONDS)));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-client.max-attempts", "4")
                .put("http-client.min-backoff", "20ms")
                .put("http-client.max-backoff", "50ms")
                .build();

        BalancingHttpClientConfig expected = new BalancingHttpClientConfig()
                .setMaxAttempts(4)
                .setMinBackoff(new Duration(20, MILLISECONDS))
                .setMaxBackoff(new Duration(50, MILLISECONDS));

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        ConfigAssertions.assertLegacyEquivalence(BalancingHttpClientConfig.class,
                ImmutableMap.<String, String>of());
    }

    @Test
    public void testMaxAttemptsBeanValidation()
    {
        assertValidates(new BalancingHttpClientConfig());
        assertValidates(new BalancingHttpClientConfig().setMaxAttempts(1));
        assertFailsValidation(new BalancingHttpClientConfig().setMaxAttempts(0), "maxAttempts", "must be greater than or equal to 1", Min.class);
        assertValidates(new BalancingHttpClientConfig().setMinBackoff(new Duration(20, MILLISECONDS)).setMaxBackoff(new Duration(20, MILLISECONDS)));
        assertFailsValidation(new BalancingHttpClientConfig().setMinBackoff(new Duration(20, MILLISECONDS)).setMaxBackoff(new Duration(19, MILLISECONDS)),
                "maxBackoffLessThanMinBackoff", "must be false", AssertFalse.class);
    }
}
