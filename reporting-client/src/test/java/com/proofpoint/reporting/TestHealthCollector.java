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
import com.google.common.collect.ImmutableMap;
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
import java.net.URI;

import static com.google.common.base.Throwables.propagate;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertEquals;

public class TestHealthCollector
{
    private static final JsonCodec<Object> OBJECT_JSON_CODEC = jsonCodec(Object.class);
    private SerialScheduledExecutorService executorService;
    private HealthBeanRegistry registry;
    private Processor processor;
    private HealthCollector collector;

    @BeforeMethod
    public void setup()
    {
        executorService = new SerialScheduledExecutorService();
        registry = new HealthBeanRegistry();
        processor = mock(Processor.class);
        collector = new HealthCollector(
                new NodeInfo("test-application", new NodeConfig()
                        .setEnvironment("testenvironment")
                        .setNodeInternalHostname("test.example.com")),
                registry,
                new TestingHttpClient(processor),
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

    private void assertSendsHealthReport(Object expected)
    {
        try {
            executorService.elapseTime(1, MINUTES);

            ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
            verify(processor).handle(captor.capture());
            Request request = captor.getValue();

            assertEquals(request.getMethod(), "POST");
            assertEquals(request.getUri(), URI.create("/v1/monitoring/service"));
            assertEquals(request.getHeader("Content-Type"), "application/json");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BodySourceTester.writeBodySourceTo(request.getBodySource(), outputStream);
            assertEquals(OBJECT_JSON_CODEC.fromJson(outputStream.toByteArray()), expected);
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
