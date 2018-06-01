package com.proofpoint.reporting;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingAdminHttpServer;
import com.proofpoint.http.server.testing.TestingAdminHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.NodeConfig;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.stats.CounterStat;
import com.proofpoint.stats.MaxGauge;
import com.proofpoint.stats.SparseCounterStat;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Nested;

import javax.management.InstanceAlreadyExistsException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.inject.Scopes.SINGLETON;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.reporting.BucketIdProvider.BucketId.bucketId;
import static com.proofpoint.reporting.ReportBinder.reportBinder;
import static org.testng.Assert.assertEquals;

public class TestReportingPrometheusModule
{
    private static final String EXPECTED_INSTANCE_TAGS = "application=\"test-application\",environment=\"test_environment\",host=\"test.hostname\",pool=\"test_pool\"";
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
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 1\n" +
                        "#TYPE TestObject_Metric gauge\n" +
                        "TestObject_Metric{" + EXPECTED_INSTANCE_TAGS + "} 2\n");
    }

    @Test
    public void testVersionNumbers()
            throws Exception
    {
        Injector injector;
        injector = bootstrapTest()
                .withModules(
                        binder -> binder.bind(NodeInfo.class).toInstance(
                                new NodeInfo("test-application", "1.2", "platform.1",
                                        new NodeConfig().setEnvironment("test_environment")
                                                .setNodeInternalHostname("test.hostname")
                                                .setPool("test_pool")
                                )),
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
                        "ReportCollector_NumMetrics{applicationVersion=\"1.2\",platformVersion=\"platform.1\"," + EXPECTED_INSTANCE_TAGS  + "} 0\n");
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
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 1\n" +
                        "#TYPE TestApplication_TestObject_Metric gauge\n" +
                        "TestApplication_TestObject_Metric{_2=\"bar\"," + EXPECTED_INSTANCE_TAGS + "} 2\n");
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
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 2\n" +
                        "#TYPE TestObject_Metric gauge\n" +
                        "TestObject_Metric{a=\"b\",baz=\"quux\",c=\"d\\\"\\\\\\n\"," + EXPECTED_INSTANCE_TAGS + "} 2\n" +
                        "TestObject_Metric{foo=\"bar\"," + EXPECTED_INSTANCE_TAGS + "} 2\n");
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
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 1\n" +
                        "#TYPE TestObject_Metric gauge\n" +
                        "TestObject_Metric{foo=\"bar\"," + EXPECTED_INSTANCE_TAGS + "} 2\n");
    }

    @Test
    public void testUnreportedValues()
            throws InstanceAlreadyExistsException
    {
        ReportedBeanRegistry reportedBeanRegistry = createServer(binder -> {}).getInstance(ReportedBeanRegistry.class);

        UnreportedValueObject unreportedValueObject = new UnreportedValueObject();
        reportedBeanRegistry.register(unreportedValueObject, ReportedBean.forTarget(unreportedValueObject), false, "TestObject", ImmutableMap.of());

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 10\n" +
                        "#TYPE TestObject_ByteMetric gauge\n" +
                        "TestObject_ByteMetric{" + EXPECTED_INSTANCE_TAGS + "} 0\n" +
                        "#TYPE TestObject_DoubleMetric gauge\n" +
                        "TestObject_DoubleMetric{" + EXPECTED_INSTANCE_TAGS + "} 0.0\n" +
                        "#TYPE TestObject_FalseBooleanMetric gauge\n" +
                        "TestObject_FalseBooleanMetric{" + EXPECTED_INSTANCE_TAGS + "} 0\n" +
                        "#TYPE TestObject_FloatMetric gauge\n" +
                        "TestObject_FloatMetric{" + EXPECTED_INSTANCE_TAGS + "} 0.0\n" +
                        "#TYPE TestObject_IntegerMetric gauge\n" +
                        "TestObject_IntegerMetric{" + EXPECTED_INSTANCE_TAGS + "} 0\n" +
                        "#TYPE TestObject_LongMetric gauge\n" +
                        "TestObject_LongMetric{" + EXPECTED_INSTANCE_TAGS + "} 0\n" +
                        "#TYPE TestObject_MaxByteMetric gauge\n" +
                        "TestObject_MaxByteMetric{" + EXPECTED_INSTANCE_TAGS + "} 127\n" +
                        "#TYPE TestObject_MinByteMetric gauge\n" +
                        "TestObject_MinByteMetric{" + EXPECTED_INSTANCE_TAGS + "} -128\n" +
                        "#TYPE TestObject_ShortMetric gauge\n" +
                        "TestObject_ShortMetric{" + EXPECTED_INSTANCE_TAGS + "} 0\n" +
                        "#TYPE TestObject_TrueBooleanMetric gauge\n" +
                        "TestObject_TrueBooleanMetric{" + EXPECTED_INSTANCE_TAGS + "} 1\n");
    }

    @Test
    public void testCounters()
    {
        Injector injector = createServer(binder -> {
            binder.bind(CounterObject.class).in(SINGLETON);
            reportBinder(binder).export(CounterObject.class);
        });
        CounterObject counterObject = injector.getInstance(CounterObject.class);
        TestingBucketIdProvider bucketIdProvider = injector.getInstance(TestingBucketIdProvider.class);

        counterObject.getNormal().add(1);
        counterObject.getSparse().add(2);
        bucketIdProvider.incrementBucket();
        counterObject.getNormal().add(1);
        counterObject.getSparse().add(2);

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE CounterObject_Normal_Count counter\n" +
                        "CounterObject_Normal_Count{" + EXPECTED_INSTANCE_TAGS + "} 2\n" +
                        "#TYPE CounterObject_Sparse_Count counter\n" +
                        "CounterObject_Sparse_Count{" + EXPECTED_INSTANCE_TAGS + "} 4.0\n" +
                        "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 2\n");
    }

    @Test
    public void testMaxGauge()
    {
        Injector injector = createServer(binder -> {
            binder.bind(MaxGaugeObject.class).in(SINGLETON);
            reportBinder(binder).export(MaxGaugeObject.class);
        });
        MaxGaugeObject maxGaugeObject = injector.getInstance(MaxGaugeObject.class);
        TestingBucketIdProvider bucketIdProvider = injector.getInstance(TestingBucketIdProvider.class);

        maxGaugeObject.getMaxGauge().update(10);
        maxGaugeObject.getMaxGauge().update(1);
        bucketIdProvider.incrementBucket();
        maxGaugeObject.getMaxGauge().update(1000);

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE MaxGaugeObject_Max gauge\n" +
                        "MaxGaugeObject_Max{" + EXPECTED_INSTANCE_TAGS + "} 10 1100\n" +
                        "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 1\n");
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

    private static class CounterObject
    {
        private CounterStat normal = new CounterStat();
        private SparseCounterStat sparse = new SparseCounterStat();

        @Nested
        public CounterStat getNormal()
        {
            return normal;
        }

        @Nested
        public SparseCounterStat getSparse()
        {
            return sparse;
        }
    }

    private static class MaxGaugeObject
    {
        private MaxGauge maxGauge = new MaxGauge();

        @Flatten
        public MaxGauge getMaxGauge()
        {
            return maxGauge;
        }
    }

    private Injector createServer(Module module)
    {
        Injector injector;
        try {
            injector = bootstrapTest()
                    .withModules(
                            binder -> binder.bind(NodeInfo.class).toInstance(new NodeInfo("test-application", new NodeConfig()
                                            .setEnvironment("test_environment")
                                            .setNodeInternalHostname("test.hostname")
                                            .setPool("test_pool"))),
                            new TestingAdminHttpServerModule(),
                            explicitJaxrsModule(),
                            new JsonModule(),
                            Modules.override(new ReportingModule()).with(binder -> {
                                binder.bind(TestingBucketIdProvider.class).in(SINGLETON);
                                binder.bind(BucketIdProvider.class).to(TestingBucketIdProvider.class).in(SINGLETON);
                            }),
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
        return injector;
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }

    private static class TestingBucketIdProvider
        implements BucketIdProvider
    {
        private AtomicInteger bucket = new AtomicInteger();

        @Override
        public BucketId get()
        {
            int id = bucket.get();
            return bucketId(id, id * 100_000_000 + 1_000_000_000);
        }

        void incrementBucket()
        {
            bucket.incrementAndGet();
        }
    }
}
