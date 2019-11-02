package com.proofpoint.audit;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.DataSize.Unit;
import org.testng.annotations.Test;

import java.util.Map;

public class TestAuditConfiguration
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(AuditConfiguration.class)
                .setLogEnable(true)
                .setLogPath("var/log/audit.log")
                .setMaxSegmentSize(new DataSize(100, Unit.MEGABYTE))
                .setMaxHistory(30)
                .setMaxTotalSize(new DataSize(1, Unit.GIGABYTE))
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("audit.log.enable", "false")
                .put("audit.log.path", "var/log/foo.log")
                .put("audit.log.max-size", "1GB")
                .put("audit.log.max-history", "25")
                .put("audit.log.max-total-size", "5GB")
                .build();

        AuditConfiguration expected = new AuditConfiguration()
                .setLogEnable(false)
                .setLogPath("var/log/foo.log")
                .setMaxSegmentSize(new DataSize(1, Unit.GIGABYTE))
                .setMaxHistory(25)
                .setMaxTotalSize(new DataSize(5, Unit.GIGABYTE));

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .build();

        ConfigAssertions.assertLegacyEquivalence(AuditConfiguration.class, currentProperties);
    }

}
