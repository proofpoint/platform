/*
 * Copyright 2014 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.reporting;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.proofpoint.discovery.client.CachingServiceSelector;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceSelectorConfig;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.testing.BodySourceTester;
import com.proofpoint.http.client.testing.TestingHttpClient;
import com.proofpoint.http.client.testing.TestingHttpClient.Processor;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeConfig;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.testing.SerialScheduledExecutorService;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.annotation.Nullable;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanException;
import javax.management.ReflectionException;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;

import static com.google.common.base.Throwables.propagate;
import static com.proofpoint.discovery.client.ServiceState.RUNNING;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestHealthCollector
{
    private static final JsonCodec<Object> OBJECT_JSON_CODEC = jsonCodec(Object.class);
    private static final String INSTANCE_URL = "http://instance.example.com";
    private SerialScheduledExecutorService executorService;
    private HealthBeanRegistry registry;
    private CachingServiceSelector selector;
    private Processor processor;
    private HealthCollector collector;

    @BeforeMethod
    public void setup()
    {
        NodeInfo nodeInfo = new NodeInfo("test-application", new NodeConfig()
                .setEnvironment("testenvironment")
                .setNodeInternalHostname("test.example.com"));
        executorService = new SerialScheduledExecutorService();
        registry = new HealthBeanRegistry();
        selector = new CachingServiceSelector("monitoring-acceptor", new ServiceSelectorConfig(), nodeInfo);
        processor = mock(Processor.class);
        collector = new HealthCollector(
                nodeInfo,
                registry,
                new TestingHttpClient(processor),
                selector,
                new CurrentTimeSecsProvider()
                {
                    private final Ticker ticker = executorService.getTicker();

                    @Override
                    public long getCurrentTimeSecs()
                    {
                        return ticker.read() / 1_000_000_000L;
                    }
                },
                executorService,
                jsonCodec(HealthReport.class)
        );
        updateSelector(INSTANCE_URL);
        collector.start();
    }

    @Test
    public void testNoHealthBeans()
    {
        executorService.elapseTime(1, MINUTES);
        verifyNoMoreInteractions(processor);
    }

    @Test
    public void testOkStatus()
    {
        registerTestAttribute("test description", null);

        assertSendsHealthReport(ImmutableMap.of(
                "host", "test.example.com",
                "time", 60,
                "results", ImmutableList.of(ImmutableMap.of(
                        "service", "test-application test description (suffix)",
                        "status", "OK"
                ))
        ));
    }

    @Test
    public void testCriticalStatus()
    {
        registerTestAttribute("test description", "test status");

        assertSendsHealthReport(ImmutableMap.of(
                "host", "test.example.com",
                "time", 60,
                "results", ImmutableList.of(ImmutableMap.of(
                        "service", "test-application test description (suffix)",
                        "status", "CRITICAL",
                        "message", "test status"
                ))
        ));
    }

    @Test
    public void testMultipleRecievers()
    {
        registerTestAttribute("test description", null);
        updateSelector("http://instance1.example.com", "http://instance2.example.com/", "http://instance3.example.com/foo");

        assertSendsHealthReport(ImmutableMap.of(
                "host", "test.example.com",
                "time", 60,
                "results", ImmutableList.of(ImmutableMap.of(
                        "service", "test-application test description (suffix)",
                        "status", "OK"
                ))
        ), "http://instance1.example.com", "http://instance2.example.com", "http://instance3.example.com/foo");
    }

    @Test
    public void testAttributeNotFoundException()
    {
        registerTestAttributeException("test description", new AttributeNotFoundException("test exception"));

        assertSendsHealthReport(ImmutableMap.of(
                "host", "test.example.com",
                "time", 60,
                "results", ImmutableList.of(ImmutableMap.of(
                        "service", "test-application test description (suffix)",
                        "status", "UNKNOWN",
                        "message", "Health check attribute not found"
                ))
        ));
    }

    @Test
    public void testMBeanException()
    {
        registerTestAttributeException("test description", new MBeanException(new Exception("test exception"), "mbean exception"));

        assertSendsHealthReport(ImmutableMap.of(
                "host", "test.example.com",
                "time", 60,
                "results", ImmutableList.of(ImmutableMap.of(
                        "service", "test-application test description (suffix)",
                        "status", "UNKNOWN",
                        "message", "test exception"
                ))
        ));
    }

    @Test
    public void testReflectionException()
    {
        registerTestAttributeException("test description", new ReflectionException(new Exception("test exception"), "reflection exception"));

        assertSendsHealthReport(ImmutableMap.of(
                "host", "test.example.com",
                "time", 60,
                "results", ImmutableList.of(ImmutableMap.of(
                        "service", "test-application test description (suffix)",
                        "status", "UNKNOWN",
                        "message", "test exception"
                ))
        ));
    }

    private void updateSelector(String... urls)
    {
        Builder<ServiceDescriptor> builder = ImmutableList.builder();
        for (String url : urls) {
            builder.add(new ServiceDescriptor(randomUUID(), randomUUID().toString(),
                    "monitoring-acceptor", "general", randomUUID().toString(), RUNNING,
                    ImmutableMap.of(
                            "http", url
                    )));
        }
        selector.updateServiceDescriptors(builder.build());
    }

    private void assertSendsHealthReport(Object expected, String... urls)
    {
        if (urls.length == 0) {
            urls = new String[] {INSTANCE_URL};
        }
        HashSet<String> expectedUrls = new HashSet<>();
        for (String url : urls) {
            expectedUrls.add(url + "/v1/monitoring/service");
        }

        try {
            executorService.elapseTime(1, MINUTES);

            ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
            verify(processor, atLeastOnce()).handle(captor.capture());
            List<Request> requests = captor.getAllValues();

            for (Request request : requests) {
                String uri = request.getUri().toString();
                assertTrue(expectedUrls.remove(uri), uri + " expected");
                assertEquals(request.getMethod(), "POST", uri + " method");
                assertEquals(request.getHeader("Content-Type"), "application/json", uri + " content-type");

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                BodySourceTester.writeBodySourceTo(request.getBodySource(), outputStream);
                assertEquals(OBJECT_JSON_CODEC.fromJson(outputStream.toByteArray()), expected, uri + " body");
            }

            assertEquals(expectedUrls, ImmutableSet.<String>of(), "URIs not sent a message");
        }
        catch (Exception e) {
            throw propagate(e);
        }
    }

    private void registerTestAttribute(final String description, @Nullable final String value)
    {
        try {
            registry.register(new HealthBeanAttribute()
            {
                @Override
                public String getDescription()
                {
                    return description;
                }

                @Override
                public String getValue()
                {
                    return value;
                }
            }, description + " (suffix)");
        }
        catch (InstanceAlreadyExistsException e) {
            throw propagate(e);
        }
    }

    private void registerTestAttributeException(final String description, final Exception e)
    {
        try {
            registry.register(new HealthBeanAttribute()
            {
                @Override
                public String getDescription()
                {
                    return description;
                }

                @Override
                public String getValue()
                        throws AttributeNotFoundException, MBeanException, ReflectionException
                {
                    if (e instanceof AttributeNotFoundException) {
                        throw (AttributeNotFoundException) e;
                    }
                    if (e instanceof MBeanException) {
                        throw (MBeanException) e;
                    }
                    if (e instanceof ReflectionException) {
                        throw (ReflectionException) e;
                    }
                    throw propagate(e);
                }
            }, description + " (suffix)");
        }
        catch (InstanceAlreadyExistsException ex) {
            throw propagate(ex);
        }
    }
}
