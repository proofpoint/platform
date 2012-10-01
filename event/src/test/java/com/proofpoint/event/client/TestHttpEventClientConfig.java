package com.proofpoint.event.client;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.units.Duration;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestHttpEventClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(HttpEventClientConfig.class)
                .setJsonVersion(2)
                .setServiceType(null)
                .setMaxConnections(-1)
                .setConnectTimeout(new Duration(50, TimeUnit.MILLISECONDS))
                .setRequestTimeout(new Duration(60, TimeUnit.SECONDS))
                .setCompress(false)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("collector.json-version", "1")
                .put("collector.service-type", "EVENT")
                .put("collector.max-connections", "10")
                .put("collector.connect-timeout", "3s")
                .put("collector.request-timeout", "8s")
                .put("collector.compress", "true")
                .build();

        HttpEventClientConfig expected = new HttpEventClientConfig()
                .setJsonVersion(1)
                .setServiceType(HttpEventClientConfig.ServiceType.EVENT)
                .setMaxConnections(10)
                .setConnectTimeout(new Duration(3, TimeUnit.SECONDS))
                .setRequestTimeout(new Duration(8, TimeUnit.SECONDS))
                .setCompress(true);

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testServiceTypeDefaults()
    {
        HttpEventClientConfig config = new HttpEventClientConfig();
        Assert.assertEquals(config.getServiceType(), HttpEventClientConfig.ServiceType.COLLECTOR);
        config.setJsonVersion(1);
        Assert.assertEquals(config.getServiceType(), HttpEventClientConfig.ServiceType.EVENT);
    }
}
