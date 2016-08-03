package com.proofpoint.jmx;

import com.google.common.net.HostAndPort;
import org.testng.annotations.Test;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestJmxAgent
{
    @Test
    public void testJava8Agent()
            throws Exception
    {
        HostAndPort address = JmxAgent8.getRunningAgentAddress(null, null);

        JmxAgent agent = new JmxAgent8(new JmxConfig().setEnabled(true));
        if (address == null) {
            // if agent wasn't running, it must have been started by the instantiation of JmxAgent
            address = JmxAgent8.getRunningAgentAddress(null, null);
            assertNotNull(address);
        }

        JMXServiceURL url = agent.getUrl();

        assertEquals(url.toString(), format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", address.getHost(), address.getPort()));

        JMXConnector connector = JMXConnectorFactory.connect(url);
        connector.connect();
    }
}
