package com.proofpoint.reporting;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
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
import com.proofpoint.stats.DistributionStat;
import com.proofpoint.stats.MaxGauge;
import com.proofpoint.stats.SparseCounterStat;
import com.proofpoint.stats.SparseDistributionStat;
import com.proofpoint.stats.SparseTimeStat;
import com.proofpoint.stats.TimeStat;
import com.proofpoint.testing.Closeables;
import com.proofpoint.units.Duration;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Nested;

import javax.management.InstanceAlreadyExistsException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
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
        Injector injector = createServer(binder -> {
            binder.bind(ReportedObject.class);
            reportBinder(binder).export(ReportedObject.class).withNamePrefix("TestObject");
        });

        injector.getInstance(TestingBucketIdProvider.class).incrementBucket();

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_LogErrors_Count gauge\n" +
                        "ReportCollector_LogErrors_Count{" + EXPECTED_INSTANCE_TAGS + "} 0.0 1100\n" +
                        "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 2\n" +
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
                                                .setNodeInternalIp(getV4Localhost())
                                                .setNodeBindIp(getV4Localhost())
                                )),
                        new TestingAdminHttpServerModule(),
                        explicitJaxrsModule(),
                        new JsonModule(),
                        Modules.override(new ReportingModule()).with(binder -> {
                            binder.bind(TestingBucketIdProvider.class).in(SINGLETON);
                            binder.bind(BucketIdProvider.class).to(TestingBucketIdProvider.class).in(SINGLETON);
                        }),
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
                "#TYPE ReportCollector_LogErrors_Count gauge\n" +
                        "ReportCollector_LogErrors_Count{" + EXPECTED_INSTANCE_TAGS + "} 0.0 1000\n" +
                        "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{applicationVersion=\"1.2\",platformVersion=\"platform.1\"," + EXPECTED_INSTANCE_TAGS + "} 1\n" +
                        "#TYPE ReportCollector_ServerStart gauge\n" +
                        "ReportCollector_ServerStart{applicationVersion=\"1.2\",platformVersion=\"platform.1\"," + EXPECTED_INSTANCE_TAGS + "} 1 1000\n");
    }

    @Test
    public void testApplicationPrefixAndTags()
    {
        Injector injector = createServer(binder -> {
            binder.bind(ReportedObject.class);
            reportBinder(binder).export(ReportedObject.class)
                    .withApplicationPrefix()
                    .withNamePrefix("TestObject")
                    .withTags(ImmutableMap.of("2", "bar"));
        });

        injector.getInstance(TestingBucketIdProvider.class).incrementBucket();

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_LogErrors_Count gauge\n" +
                        "ReportCollector_LogErrors_Count{" + EXPECTED_INSTANCE_TAGS + "} 0.0 1100\n" +
                        "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 2\n" +
                        "#TYPE TestApplication_TestObject_Metric gauge\n" +
                        "TestApplication_TestObject_Metric{_2=\"bar\"," + EXPECTED_INSTANCE_TAGS + "} 2\n");
    }

    @Test
    public void testMultipleTags()
    {
        Injector injector = createServer(binder -> {
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

        injector.getInstance(TestingBucketIdProvider.class).incrementBucket();

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_LogErrors_Count gauge\n" +
                        "ReportCollector_LogErrors_Count{" + EXPECTED_INSTANCE_TAGS + "} 0.0 1100\n" +
                        "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 3\n" +
                        "#TYPE TestObject_Metric gauge\n" +
                        "TestObject_Metric{a=\"b\",baz=\"quux\",c=\"d\\\"\\\\\\n\"," + EXPECTED_INSTANCE_TAGS + "} 2\n" +
                        "TestObject_Metric{foo=\"bar\"," + EXPECTED_INSTANCE_TAGS + "} 2\n");
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testLegacy()
    {
        Injector injector = createServer(binder -> {
            binder.bind(ReportedObject.class);
            reportBinder(binder).export(ReportedObject.class)
                    .as("com.proofpoint.reporting.test:name=TestObject,foo=bar");
        });

        injector.getInstance(TestingBucketIdProvider.class).incrementBucket();

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_LogErrors_Count gauge\n" +
                        "ReportCollector_LogErrors_Count{" + EXPECTED_INSTANCE_TAGS + "} 0.0 1100\n" +
                        "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 2\n" +
                        "#TYPE TestObject_Metric gauge\n" +
                        "TestObject_Metric{foo=\"bar\"," + EXPECTED_INSTANCE_TAGS + "} 2\n");
    }

    @Test
    public void testUnreportedValues()
            throws InstanceAlreadyExistsException
    {
        Injector injector = createServer(binder -> {
        });
        ReportedBeanRegistry reportedBeanRegistry = injector.getInstance(ReportedBeanRegistry.class);
        TestingBucketIdProvider bucketIdProvider = injector.getInstance(TestingBucketIdProvider.class);
        bucketIdProvider.incrementBucket();

        UnreportedValueObject unreportedValueObject = new UnreportedValueObject();
        reportedBeanRegistry.register(unreportedValueObject, ReportedBean.forTarget(unreportedValueObject, bucketIdProvider), false, "TestObject", ImmutableMap.of());

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_LogErrors_Count gauge\n" +
                        "ReportCollector_LogErrors_Count{" + EXPECTED_INSTANCE_TAGS + "} 0.0 1100\n" +
                        "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 11\n" +
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
                "#TYPE CounterObject_Normal_Count gauge\n" +
                        "CounterObject_Normal_Count{" + EXPECTED_INSTANCE_TAGS + "} 1.0 1100\n" +
                        "#TYPE CounterObject_Sparse_Count gauge\n" +
                        "CounterObject_Sparse_Count{" + EXPECTED_INSTANCE_TAGS + "} 2.0 1100\n" +
                        "#TYPE ReportCollector_LogErrors_Count gauge\n" +
                        "ReportCollector_LogErrors_Count{" + EXPECTED_INSTANCE_TAGS + "} 0.0 1100\n" +
                        "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 3\n");
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
                        "#TYPE ReportCollector_LogErrors_Count gauge\n" +
                        "ReportCollector_LogErrors_Count{" + EXPECTED_INSTANCE_TAGS + "} 0.0 1100\n" +
                        "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 2\n");
    }

    @Test(dataProvider = "getStatsObjects")
    public void testSummary(StatsObject statsObject, String expectedSuffix)
    {
        Injector injector = createServer(binder -> {
            binder.bind(StatsObject.class).toInstance(statsObject);
            reportBinder(binder).export(StatsObject.class);
        });
        TestingBucketIdProvider bucketIdProvider = injector.getInstance(TestingBucketIdProvider.class);

        for (int i = 0; i < 100; i++) {
            statsObject.add(1000);
        }
        bucketIdProvider.incrementBucket();
        for (int i = 0; i < 100; i++) {
            statsObject.add(i);
        }
        bucketIdProvider.incrementBucket();
        statsObject.add(1000);

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_LogErrors_Count gauge\n" +
                        "ReportCollector_LogErrors_Count{" + EXPECTED_INSTANCE_TAGS + "} 0.0 1200\n" +
                        "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 10\n" +
                        "#TYPE StatsObject_Count gauge\n" +
                        "StatsObject_Count{" + EXPECTED_INSTANCE_TAGS + "} 100.0 1200\n" +
                        "#TYPE StatsObject_Max gauge\n" +
                        "StatsObject_Max{" + EXPECTED_INSTANCE_TAGS + "} 99" + expectedSuffix + " 1200\n" +
                        "#TYPE StatsObject_Min gauge\n" +
                        "StatsObject_Min{" + EXPECTED_INSTANCE_TAGS + "} 0" + expectedSuffix + " 1200\n" +
                        "#TYPE StatsObject_P50 gauge\n" +
                        "StatsObject_P50{" + EXPECTED_INSTANCE_TAGS + "} 50" + expectedSuffix + " 1200\n" +
                        "#TYPE StatsObject_P75 gauge\n" +
                        "StatsObject_P75{" + EXPECTED_INSTANCE_TAGS + "} 75" + expectedSuffix + " 1200\n" +
                        "#TYPE StatsObject_P90 gauge\n" +
                        "StatsObject_P90{" + EXPECTED_INSTANCE_TAGS + "} 90" + expectedSuffix + " 1200\n" +
                        "#TYPE StatsObject_P95 gauge\n" +
                        "StatsObject_P95{" + EXPECTED_INSTANCE_TAGS + "} 95" + expectedSuffix + " 1200\n" +
                        "#TYPE StatsObject_P99 gauge\n" +
                        "StatsObject_P99{" + EXPECTED_INSTANCE_TAGS + "} 99" + expectedSuffix + " 1200\n" +
                        "#TYPE StatsObject_Total gauge\n" +
                        "StatsObject_Total{" + EXPECTED_INSTANCE_TAGS + "} 4950" + expectedSuffix + " 1200\n"
        );
    }

    @DataProvider(name = "getStatsObjects")
    public Object[][] getStatsObjects()
    {
        return new Object[][] {
                new Object[] {new StatsObject()
                {
                    private final DistributionStat delegate = new DistributionStat();

                    @Override
                    public Object getDelegate()
                    {
                        return delegate;
                    }

                    @Override
                    void add(int value)
                    {
                        delegate.add(value);
                    }
                }, ""},
                new Object[] {new StatsObject()
                {
                    private final SparseDistributionStat delegate = new SparseDistributionStat();

                    @Override
                    public Object getDelegate()
                    {
                        return delegate;
                    }

                    @Override
                    void add(int value)
                    {
                        delegate.add(value);
                    }
                }, ""},
                new Object[] {new StatsObject()
                {
                    private final TimeStat delegate = new TimeStat();

                    @Override
                    public Object getDelegate()
                    {
                        return delegate;
                    }

                    @Override
                    void add(int value)
                    {
                        delegate.add(new Duration(value, TimeUnit.SECONDS));
                    }
                }, ".0"},
                new Object[] {new StatsObject()
                {
                    private final SparseTimeStat delegate = new SparseTimeStat();

                    @Override
                    public Object getDelegate()
                    {
                        return delegate;
                    }

                    @Override
                    void add(int value)
                    {
                        delegate.add(new Duration(value, TimeUnit.SECONDS));
                    }
                }, ".0"},
        };
    }

    @Test
    public void testNestedSummary()
    {
        Injector injector = createServer(binder -> {
            binder.bind(NestedStatsObject.class).in(Scopes.SINGLETON);
            reportBinder(binder).export(NestedStatsObject.class).withNamePrefix("StatsObject");
        });
        TestingBucketIdProvider bucketIdProvider = injector.getInstance(TestingBucketIdProvider.class);
        NestedStatsObject statsObject = injector.getInstance(NestedStatsObject.class);

        for (int i = 0; i < 100; i++) {
            statsObject.add(1000);
        }
        bucketIdProvider.incrementBucket();
        for (int i = 0; i < 100; i++) {
            statsObject.add(i);
        }
        bucketIdProvider.incrementBucket();
        statsObject.add(1000);

        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/metrics")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody(),
                "#TYPE ReportCollector_LogErrors_Count gauge\n" +
                        "ReportCollector_LogErrors_Count{" + EXPECTED_INSTANCE_TAGS + "} 0.0 1200\n" +
                        "#TYPE ReportCollector_NumMetrics gauge\n" +
                        "ReportCollector_NumMetrics{" + EXPECTED_INSTANCE_TAGS + "} 10\n" +
                        "#TYPE StatsObject_DistributionStat_Count gauge\n" +
                        "StatsObject_DistributionStat_Count{" + EXPECTED_INSTANCE_TAGS + "} 100.0 1200\n" +
                        "#TYPE StatsObject_DistributionStat_Max gauge\n" +
                        "StatsObject_DistributionStat_Max{" + EXPECTED_INSTANCE_TAGS + "} 99 1200\n" +
                        "#TYPE StatsObject_DistributionStat_Min gauge\n" +
                        "StatsObject_DistributionStat_Min{" + EXPECTED_INSTANCE_TAGS + "} 0 1200\n" +
                        "#TYPE StatsObject_DistributionStat_P50 gauge\n" +
                        "StatsObject_DistributionStat_P50{" + EXPECTED_INSTANCE_TAGS + "} 50 1200\n" +
                        "#TYPE StatsObject_DistributionStat_P75 gauge\n" +
                        "StatsObject_DistributionStat_P75{" + EXPECTED_INSTANCE_TAGS + "} 75 1200\n" +
                        "#TYPE StatsObject_DistributionStat_P90 gauge\n" +
                        "StatsObject_DistributionStat_P90{" + EXPECTED_INSTANCE_TAGS + "} 90 1200\n" +
                        "#TYPE StatsObject_DistributionStat_P95 gauge\n" +
                        "StatsObject_DistributionStat_P95{" + EXPECTED_INSTANCE_TAGS + "} 95 1200\n" +
                        "#TYPE StatsObject_DistributionStat_P99 gauge\n" +
                        "StatsObject_DistributionStat_P99{" + EXPECTED_INSTANCE_TAGS + "} 99 1200\n" +
                        "#TYPE StatsObject_DistributionStat_Total gauge\n" +
                        "StatsObject_DistributionStat_Total{" + EXPECTED_INSTANCE_TAGS + "} 4950 1200\n"
        );
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
                                    .setPool("test_pool")
                                    .setNodeInternalIp(getV4Localhost())
                                    .setNodeBindIp(getV4Localhost())
                            )),
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

    private abstract static class StatsObject
    {
        @Flatten
        public abstract Object getDelegate();

        abstract void add(int value);

        @Override
        public String toString()
        {
            return getDelegate().getClass().getSimpleName();
        }
    }

    private static class NestedStatsObject
    {
        private final DistributionStat distributionStat = new DistributionStat();

        @Nested
        public DistributionStat getDistributionStat()
        {
            return distributionStat;
        }

        public void add(int value)
        {
            distributionStat.add(value);
        }
    }

    @SuppressWarnings("ImplicitNumericConversion")
    private static InetAddress getV4Localhost()
    {
        try {
            return InetAddress.getByAddress("localhost", new byte[] {127, 0, 0, 1});
        }
        catch (UnknownHostException e) {
            throw new AssertionError("Could not create localhost address");
        }
    }
}
