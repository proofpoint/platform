package com.proofpoint.http.client.balancing;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.http.client.balancing.HttpServiceBalancerUriConfig.UriMultiset;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Map;

import static com.proofpoint.configuration.testing.ConfigAssertions.assertLegacyEquivalence;
import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;
import static com.proofpoint.testing.ValidationAssertions.assertValidates;

public class TestHttpServiceBalancerUriConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(HttpServiceBalancerUriConfig.class)
                .setUris(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("uri", "https://authservice.invalid,https://authservice2.invalid")
                .build();

        HttpServiceBalancerUriConfig expected = new HttpServiceBalancerUriConfig()
                .setUris(UriMultiset.of(URI.create("https://authservice.invalid"), URI.create("https://authservice2.invalid")));

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        assertLegacyEquivalence(HttpServiceBalancerUriConfig.class,
                ImmutableMap.of("uri", "https://invalid.invalid"));
    }

    @Test
    public void testUrisBeanValidation()
    {
        assertFailsValidation(new HttpServiceBalancerUriConfig(), "uris", "must not be null", NotNull.class);
        assertFailsValidation(new HttpServiceBalancerUriConfig().setUris(UriMultiset.of()), "uris", "must not be empty", NotEmpty.class);
        assertValidates(new HttpServiceBalancerUriConfig().setUris(UriMultiset.of(URI.create("https://invalid.invalid"))));
    }
}
