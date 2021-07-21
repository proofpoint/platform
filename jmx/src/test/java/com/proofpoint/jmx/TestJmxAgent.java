package com.proofpoint.jmx;

import com.google.common.net.HostAndPort;
import org.testng.annotations.Test;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestJmxAgent
{
    @Test
    public void testDisabled()
            throws Exception
    {
        JmxAgent agent = new JmxAgent9(new JmxConfig().setEnabled(false));
        assertNull(agent.getUrl(), "agent url");
    }
}
