package com.proofpoint.reporting;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingAdminHttpServer;
import com.proofpoint.http.server.testing.TestingAdminHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.NodeConfig;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.InstanceAlreadyExistsException;
import java.net.URI;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.reporting.ReportBinder.reportBinder;
import static org.testng.Assert.assertEquals;

public class TestReportingPrometheusModule
{
    private final HttpClient client = new JettyHttpClient();

    private LifeCycleManager lifeCycleManager;
    private TestingAdminHttpServer server;

    @BeforeMethod
    public void setup()
    {
        lifeCycleManager = null;
        server = null;
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
            throws Exception
    {
        if (lifeCycleManager != null) {
            lifeCycleManager.stop();
        }
    }

    @AfterClass(alwaysRun = true)
    public void teardownClass()
    {
        Closeables.closeQuietly(client);
    }

    @Test
    public void testGetMetrics()
    {
        createServer(binder -> {
            binder.bind(ReportedObject.class);
            reportBinder(binder).export(ReportedObject.class).withNamePrefix("TestObject");
        });

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics 1\n" +
                        "#TYPE TestObject_Metric gauge\n" +
                        "TestObject_Metric 2\n");
    }

    @Test
    public void testVersionNumbers()
            throws Exception
    {
        Injector injector;
        injector = bootstrapTest()
                .withModules(
                        binder -> binder.bind(NodeInfo.class).toInstance(
                                new NodeInfo("test-application", "1.2", "platform.1", new NodeConfig().setEnvironment("testing"))),
                        new TestingAdminHttpServerModule(),
                        explicitJaxrsModule(),
                        new JsonModule(),
                        new ReportingModule(),
                        new ReportingPrometheusModule()
                )
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingAdminHttpServer.class);
        injector.getInstance(ReportedBeanRegistry.class);

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{applicationVersion=\"1.2\",platformVersion=\"platform.1\"} 0\n");
    }

    @Test
    public void testApplicationPrefixAndTags()
    {
        createServer(binder -> {
            binder.bind(ReportedObject.class);
            reportBinder(binder).export(ReportedObject.class)
                    .withApplicationPrefix()
                    .withNamePrefix("TestObject")
                    .withTags(ImmutableMap.of("2", "bar"));
        });

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics 1\n" +
                        "#TYPE TestApplication_TestObject_Metric gauge\n" +
                        "TestApplication_TestObject_Metric{_2=\"bar\"} 2\n");
    }

    @Test
    public void testMultipleTags()
    {
        createServer(binder -> {
            binder.bind(ReportedObject.class);
            reportBinder(binder).export(ReportedObject.class)
                    .withNamePrefix("TestObject")
                    .withTags(ImmutableMap.of("foo", "bar"));
            binder.bind(ReportedObject.class).annotatedWith(Names.named("second")).to(ReportedObject.class);
            reportBinder(binder).export(ReportedObject.class)
                    .annotatedWith(Names.named("second"))
                    .withNamePrefix("TestObject")
                    .withTags(ImmutableMap.of("baz", "quux", "a", "b", "c", "d\"\\\n"));
        });

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics 2\n" +
                        "#TYPE TestObject_Metric gauge\n" +
                        "TestObject_Metric{a=\"b\",baz=\"quux\",c=\"d\\\"\\\\\\n\"} 2\n" +
                        "TestObject_Metric{foo=\"bar\"} 2\n");
    }

    @Test
    public void testLegacy()
    {
        createServer(binder -> {
            binder.bind(ReportedObject.class);
            reportBinder(binder).export(ReportedObject.class)
                    .as("com.proofpoint.reporting.test:name=TestObject,foo=bar");
        });

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics 1\n" +
                        "#TYPE TestObject_Metric gauge\n" +
                        "TestObject_Metric{foo=\"bar\"} 2\n");
    }

    @Test
    public void testUnreportedValues()
            throws InstanceAlreadyExistsException
    {
        ReportedBeanRegistry reportedBeanRegistry = createServer(binder -> {});

        UnreportedValueObject unreportedValueObject = new UnreportedValueObject();
        reportedBeanRegistry.register(unreportedValueObject, ReportedBean.forTarget(unreportedValueObject), false, "TestObject", ImmutableMap.of());

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics 10\n" +
                        "#TYPE TestObject_ByteMetric gauge\n" +
                        "TestObject_ByteMetric 0\n" +
                        "#TYPE TestObject_DoubleMetric gauge\n" +
                        "TestObject_DoubleMetric 0.0\n" +
                        "#TYPE TestObject_FalseBooleanMetric gauge\n" +
                        "TestObject_FalseBooleanMetric 0\n" +
                        "#TYPE TestObject_FloatMetric gauge\n" +
                        "TestObject_FloatMetric 0.0\n" +
                        "#TYPE TestObject_IntegerMetric gauge\n" +
                        "TestObject_IntegerMetric 0\n" +
                        "#TYPE TestObject_LongMetric gauge\n" +
                        "TestObject_LongMetric 0\n" +
                        "#TYPE TestObject_MaxByteMetric gauge\n" +
                        "TestObject_MaxByteMetric 127\n" +
                        "#TYPE TestObject_MinByteMetric gauge\n" +
                        "TestObject_MinByteMetric -128\n" +
                        "#TYPE TestObject_ShortMetric gauge\n" +
                        "TestObject_ShortMetric 0\n" +
                        "#TYPE TestObject_TrueBooleanMetric gauge\n" +
                        "TestObject_TrueBooleanMetric 1\n");
    }

    private static class TestingValue
    {
        @Override
        public String toString()
        {
            return "testing toString value";
        }
    }

    private static class ReportedObject
    {
        @Reported
        public int getMetric()
        {
            return 2;
        }
    }

    private static class UnreportedValueObject
    {
        @Reported
        public double getDoubleMetric()
        {
            return 0;
        }

        @Reported
        public double getNanDouble()
        {
            return Double.NaN;
        }

        @Reported
        public double getInfiniteDouble()
        {
            return Double.NEGATIVE_INFINITY;
        }

        @Reported
        public float getFloatMetric()
        {
            return 0F;
        }

        @Reported
        public float getNanFloat()
        {
            return Float.NaN;
        }

        @Reported
        public float getInfiniteFloat()
        {
            return Float.POSITIVE_INFINITY;
        }

        @Reported
        public long getLongMetric()
        {
            return 0L;
        }

        @Reported
        public long getMaxLongMetric()
        {
            return Long.MAX_VALUE;
        }

        @Reported
        public long getMinLongMetric()
        {
            return Long.MIN_VALUE;
        }

        @Reported
        public int getIntegerMetric()
        {
            return 0;
        }

        @Reported
        public int getMaxIntegerMetric()
        {
            return Integer.MAX_VALUE;
        }

        @Reported
        public int getMinIntegerMetric()
        {
            return Integer.MIN_VALUE;
        }

        @Reported
        public short getShortMetric()
        {
            return 0;
        }

        @Reported
        public short getMaxShortMetric()
        {
            return Short.MAX_VALUE;
        }

        @Reported
        public short getMinShortMetric()
        {
            return Short.MIN_VALUE;
        }

        @Reported
        public byte getByteMetric()
        {
            return 0;
        }

        @Reported
        public byte getMaxByteMetric()
        {
            return Byte.MAX_VALUE;
        }

        @Reported
        public byte getMinByteMetric()
        {
            return Byte.MIN_VALUE;
        }

        @Reported
        public boolean getFalseBooleanMetric()
        {
            return false;
        }

        @Reported
        public Boolean getTrueBooleanMetric()
        {
            return true;
        }

        @Reported
        public Boolean getNullBooleanMetric()
        {
            return null;
        }

        @Reported
        public TestingValue getTestingValueMetric()
        {
            return new TestingValue();
        }

        @Reported
        public Integer getNullMetric()
        {
            return null;
        }

        @Reported
        public int getExceptionMetric()
        {
            throw new UnsupportedOperationException();
        }
    }

    private ReportedBeanRegistry createServer(Module module)
    {
        Injector injector;
        try {
            injector = bootstrapTest()
                    .withModules(
                            new TestingNodeModule(),
                            new TestingAdminHttpServerModule(),
                            explicitJaxrsModule(),
                            new JsonModule(),
                            new ReportingModule(),
                            new ReportingPrometheusModule(),
                            module
                    )
                    .initialize();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingAdminHttpServer.class);
        return injector.getInstance(ReportedBeanRegistry.class);
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }
}
