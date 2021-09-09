package com.proofpoint.http.client.balancing;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.units.Duration;
import com.proofpoint.units.MaxDuration;
import com.proofpoint.units.MinDuration;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.Map;

import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;
import static com.proofpoint.testing.ValidationAssertions.assertValidates;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestBalancingHttpClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(BalancingHttpClientConfig.class)
                .setMaxAttempts(3)
                .setMinBackoff(new Duration(10, MILLISECONDS))
                .setMaxBackoff(new Duration(10, SECONDS))
                .setRetryBudgetRatio(new BigDecimal(2).movePointLeft(1))
                .setRetryBudgetRatioPeriod(new Duration(10, SECONDS))
                .setRetryBudgetMinPerSecond(10));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-client.max-attempts", "4")
                .put("http-client.min-backoff", "20ms")
                .put("http-client.max-backoff", "50ms")
                .put("http-client.retry-budget.ratio", "0.3")
                .put("http-client.retry-budget.ratio-period", "15s")
                .put("http-client.retry-budget.min-per-second", "19")
                .build();

        BalancingHttpClientConfig expected = new BalancingHttpClientConfig()
                .setMaxAttempts(4)
                .setMinBackoff(new Duration(20, MILLISECONDS))
                .setMaxBackoff(new Duration(50, MILLISECONDS))
                .setRetryBudgetRatio(new BigDecimal(3).movePointLeft(1))
                .setRetryBudgetRatioPeriod(new Duration(15, SECONDS))
                .setRetryBudgetMinPerSecond(19);

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        ConfigAssertions.assertLegacyEquivalence(BalancingHttpClientConfig.class,
                ImmutableMap.of());
    }

    @Test
    public void testDefaultValidates()
    {
        assertValidates(new BalancingHttpClientConfig());
    }

    @Test
    public void testMaxAttemptsBeanValidation()
    {
        assertValidates(new BalancingHttpClientConfig().setMaxAttempts(1));
        assertFailsValidation(new BalancingHttpClientConfig().setMaxAttempts(0), "maxAttempts", "must be greater than or equal to 1", Min.class);
        assertValidates(new BalancingHttpClientConfig().setMinBackoff(new Duration(20, MILLISECONDS)).setMaxBackoff(new Duration(20, MILLISECONDS)));
        assertFailsValidation(new BalancingHttpClientConfig().setMinBackoff(new Duration(20, MILLISECONDS)).setMaxBackoff(new Duration(19, MILLISECONDS)),
                "maxBackoffLessThanMinBackoff", "must be false", AssertFalse.class);
    }

    @Test
    public void TestRetryBudgetRatioBeanValidation()
    {
        assertValidates(new BalancingHttpClientConfig().setRetryBudgetRatio(new BigDecimal(0)));
        assertFailsValidation(new BalancingHttpClientConfig().setRetryBudgetRatio(new BigDecimal(-1).movePointLeft(3)),
                "retryBudgetRatio", "must be greater than or equal to 0", Min.class);
        assertValidates(new BalancingHttpClientConfig().setRetryBudgetRatio(new BigDecimal(1000)));
        assertFailsValidation(new BalancingHttpClientConfig().setRetryBudgetRatio(new BigDecimal(10001).movePointLeft(1)),
                "retryBudgetRatio", "must be less than or equal to 1000", Max.class);
    }

    @Test
    public void TestRetryBudgetRatioPeriodBeanValidation()
    {
        assertValidates(new BalancingHttpClientConfig().setRetryBudgetRatioPeriod(new Duration(1, SECONDS)));
        assertFailsValidation(new BalancingHttpClientConfig().setRetryBudgetRatioPeriod(new Duration(999, MILLISECONDS)),
                "retryBudgetRatioPeriod", "{com.proofpoint.units.MinDuration.message}", MinDuration.class);
        assertValidates(new BalancingHttpClientConfig().setRetryBudgetRatioPeriod(new Duration(1, MINUTES)));
        assertFailsValidation(new BalancingHttpClientConfig().setRetryBudgetRatioPeriod(new Duration(60001, MILLISECONDS)),
                "retryBudgetRatioPeriod", "{com.proofpoint.units.MaxDuration.message}", MaxDuration.class);
    }

    @Test
    public void TestRetryBudgetMinPerSecondBeanValidation()
    {
        assertValidates(new BalancingHttpClientConfig().setRetryBudgetMinPerSecond(1));
        assertFailsValidation(new BalancingHttpClientConfig().setRetryBudgetMinPerSecond(0),
                "retryBudgetMinPerSecond", "must be greater than or equal to 1", Min.class);
    }
}
