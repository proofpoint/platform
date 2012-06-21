package com.proofpoint.discovery.client;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.proofpoint.http.client.ApacheHttpClient;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;

public class TestServiceInventory
{
    @Test
    public void testNullServiceInventory()
            throws Exception
    {
        ServiceInventory serviceInventory = new ServiceInventory(new ServiceInventoryConfig(),
                new NodeInfo("test"),
                JsonCodec.jsonCodec(ServiceDescriptorListRepresentation.class),
                new ApacheHttpClient());

        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 0);
        serviceInventory.updateServiceInventory(false);
        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 0);
    }

    @Test
    public void testFileServiceInventory()
            throws Exception
    {
        ServiceInventoryConfig serviceInventoryConfig = new ServiceInventoryConfig()
                .setServiceInventoryUri(Resources.getResource("service-inventory.json").toURI());

        ServiceInventory serviceInventory = new ServiceInventory(serviceInventoryConfig,
                new NodeInfo("test"),
                JsonCodec.jsonCodec(ServiceDescriptorListRepresentation.class),
                new ApacheHttpClient());

        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 2);
        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery")), 2);
        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery", "general")), 2);
        serviceInventory.updateServiceInventory(false);
        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 2);
        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery")), 2);
        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery", "general")), 2);
    }

    @Test
    public void testHttpServiceInventory()
            throws Exception
    {
        String serviceInventoryJson = Resources.toString(Resources.getResource("service-inventory.json"), Charsets.UTF_8);

        Server server = null;
        try {
            int port;
            ServerSocket socket = new ServerSocket();
            try {
                socket.bind(new InetSocketAddress(0));
                port = socket.getLocalPort();
            }
            finally {
                socket.close();
            }
            URI baseURI = new URI("http", null, "127.0.0.1", port, null, null, null);

            server = new Server();
            server.setSendServerVersion(false);

            SelectChannelConnector httpConnector;
            httpConnector = new SelectChannelConnector();
            httpConnector.setName("http");
            httpConnector.setPort(port);
            server.addConnector(httpConnector);

            ServletHolder servletHolder = new ServletHolder(new ServiceInventoryServlet(serviceInventoryJson));
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            context.addServlet(servletHolder, "/*");
            HandlerCollection handlers = new HandlerCollection();
            handlers.addHandler(context);
            server.setHandler(handlers);

            server.start();


            // test
            ServiceInventoryConfig serviceInventoryConfig = new ServiceInventoryConfig()
                    .setServiceInventoryUri(baseURI);

            ServiceInventory serviceInventory = new ServiceInventory(serviceInventoryConfig,
                    new NodeInfo("test"),
                    JsonCodec.jsonCodec(ServiceDescriptorListRepresentation.class),
                    new ApacheHttpClient());

            Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 2);
            Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery")), 2);
            Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery", "general")), 2);
            serviceInventory.updateServiceInventory(false);
            Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 2);
            Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery")), 2);
            Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery", "general")), 2);
        }
        finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    public void testEmptyServiceList()
            throws Exception
    {
        ServiceInventoryConfig serviceInventoryConfig = new ServiceInventoryConfig()
                .setServiceInventoryUri(Resources.getResource("service-inventory-empty.json").toURI());

        try {
            ServiceInventory serviceInventory = new ServiceInventory(serviceInventoryConfig,
                    new NodeInfo("test"),
                    JsonCodec.jsonCodec(ServiceDescriptorListRepresentation.class),
                    new ApacheHttpClient());
            Assert.fail("RuntimeException expected");
        }
        catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Invalid ServiceDescriptorList: environment is empty"));
            Assert.assertTrue(e.getMessage().contains("Invalid ServiceDescriptorList: serviceDescriptors is null"));
        }
    }

    @Test
    public void testInvalidEnvironment()
            throws Exception
    {
        ServiceInventoryConfig serviceInventoryConfig = new ServiceInventoryConfig()
                .setServiceInventoryUri(Resources.getResource("service-inventory.json").toURI());

        try {
            ServiceInventory serviceInventory = new ServiceInventory(serviceInventoryConfig,
                    new NodeInfo("test123"),
                    JsonCodec.jsonCodec(ServiceDescriptorListRepresentation.class),
                    new ApacheHttpClient());
            Assert.fail("RuntimeException expected");
        }
        catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Expected service inventory environment to be test123, but was test"));
        }
    }

    @Test
    public void testInvalidDescriptors()
            throws Exception
    {
        ServiceInventoryConfig serviceInventoryConfig = new ServiceInventoryConfig()
                .setServiceInventoryUri(Resources.getResource("service-inventory-invalid.json").toURI());

        try {
            ServiceInventory serviceInventory = new ServiceInventory(serviceInventoryConfig,
                    new NodeInfo("test"),
                    JsonCodec.jsonCodec(ServiceDescriptorListRepresentation.class),
                    new ApacheHttpClient());
            Assert.fail("RuntimeException expected");
        }
        catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: id is null"));
            Assert.assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: nodeId is null ServiceDescriptorRepresentation{id=370af416-"));
            Assert.assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: type is null ServiceDescriptorRepresentation{id=370af416-"));
            Assert.assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: pool is null ServiceDescriptorRepresentation{id=370af416-"));
            Assert.assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: location is null ServiceDescriptorRepresentation{id=370af416-"));
            Assert.assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: state is null ServiceDescriptorRepresentation{id=370af416-"));
            Assert.assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: properties is null ServiceDescriptorRepresentation{id=370af416-"));
        }
    }

    private class ServiceInventoryServlet extends HttpServlet
    {
        private final byte[] serviceInventory;

        private ServiceInventoryServlet(String serviceInventory)
        {
            this.serviceInventory = serviceInventory.getBytes(Charsets.UTF_8);
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException
        {
            response.setHeader("Content-Type", "application/json");
            response.setStatus(200);
            response.getOutputStream().write(serviceInventory);
        }
    }
}
