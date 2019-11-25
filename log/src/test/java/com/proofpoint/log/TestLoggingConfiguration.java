package com.proofpoint.log;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.DataSize.Unit;
import org.testng.annotations.Test;

import java.util.Map;

@SuppressWarnings("deprecation")
public class TestLoggingConfiguration
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(LoggingConfiguration.class)
                .setConsoleEnabled(true)
                .setLogPath(null)
                .setBootstrapLogPath(null)
                .setMaxSegmentSize(new DataSize(100, Unit.MEGABYTE))
                .setMaxHistory(30)
                .setQueueSize(0)
                .setLevelsFile(null)
                .setMaxTotalSize(new DataSize(1, Unit.GIGABYTE))
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("log.enable-console", "false")
                .put("log.path", "var/log/foo.log")
                .put("log.bootstrap.path", "var/log/bar.log")
                .put("log.max-size", "1GB")
                .put("log.max-history", "25")
                .put("log.queue-size", "10000")
                .put("log.levels-file", "var/log/log-levels-test.cfg")
                .put("log.max-total-size", "5GB")
                .build();

        LoggingConfiguration expected = new LoggingConfiguration()
                .setConsoleEnabled(false)
                .setLogPath("var/log/foo.log")
                .setBootstrapLogPath("var/log/bar.log")
                .setMaxSegmentSize(new DataSize(1, Unit.GIGABYTE))
                .setMaxHistory(25)
                .setQueueSize(10_000)
                .setLevelsFile("var/log/log-levels-test.cfg")
                .setMaxTotalSize(new DataSize(5, Unit.GIGABYTE));

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("log.path", "var/log/foo.log")
                .put("log.max-size", "300B")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("log.output-file", "var/log/foo.log")
                .put("log.max-size-in-bytes", "300")
                .build();

        ConfigAssertions.assertLegacyEquivalence(LoggingConfiguration.class, currentProperties, oldProperties);
    }

}
