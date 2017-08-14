/*
 * Copyright 2017 Proofpoint, Inc.
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
package com.proofpoint.http.server;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.DataSize.Unit;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.Collections;

import static com.proofpoint.tracetoken.TraceTokenManager.createAndRegisterNewRequestToken;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;

public abstract class AbstractTestRequestLog
{
    @Mock
    Request request;
    @Mock
    Response response;
    @Mock
    Principal principal;
    private File file;
    private long timeToFirstByte;
    private long timeToLastByte;
    private long now;
    private long timestamp;
    private String user;
    private String agent;
    private String referrer;
    private String protocol;
    private String method;
    private long requestSize;
    private String requestContentType;
    private long responseSize;
    private int responseCode;
    private String responseContentType;
    private String pathQuery;
    protected MockCurrentTimeMillisProvider currentTimeMillisProvider;
    protected RequestLog logger;
    private long currentTime;

    protected abstract void setup(HttpServerConfig httpServerConfig, CurrentTimeMillisProvider currentTimeMillisProvider)
            throws IOException;

    protected abstract void stopLogger()
            throws Exception;

    protected abstract String getExpectedLogLine(long timestamp, String clientAddr, String method, String pathQuery, String user, String agent, int responseCode, long requestSize, long responseSize, long timeToLastByte);

    @BeforeMethod
    final public void setupAbstract()
            throws IOException
    {
        initMocks(this);

        timeToFirstByte = 456;
        timeToLastByte = 3453;
        now = System.currentTimeMillis();
        timestamp = now - timeToLastByte;
        user = "martin";
        agent = "HttpClient 4.0";
        referrer = "http://www.google.com";
        protocol = "protocol";
        method = "GET";
        requestSize = 5432;
        requestContentType = "request/type";
        responseSize = 32311;
        responseCode = 200;
        responseContentType = "response/type";
        pathQuery = "/aaa+bbb/ccc?param=hello%20there&other=true";

        file = File.createTempFile(getClass().getName(), ".log");

        currentTimeMillisProvider = new MockCurrentTimeMillisProvider(timestamp + timeToLastByte);
        HttpServerConfig httpServerConfig = new HttpServerConfig()
                .setLogPath(file.getAbsolutePath())
                .setLogMaxHistory(1)
                .setLogMaxSegmentSize(new DataSize(1, Unit.MEGABYTE))
                .setLogMaxTotalSize(new DataSize(1, Unit.GIGABYTE));
        setup(httpServerConfig, currentTimeMillisProvider);

        when(principal.getName()).thenReturn(user);
        when(request.getTimeStamp()).thenReturn(timestamp);
        when(request.getHeader("User-Agent")).thenReturn(agent);
        when(request.getHeader("Referer")).thenReturn(referrer);
        when(request.getRemoteAddr()).thenReturn("9.9.9.9");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of()));
        when(request.getProtocol()).thenReturn("unknown");
        when(request.getHeader("X-FORWARDED-PROTO")).thenReturn(protocol);
        when(request.getAttribute(TimingFilter.FIRST_BYTE_TIME)).thenReturn(timestamp + timeToFirstByte);
        when(request.getHttpURI()).thenReturn(new HttpURI("http://www.example.com/aaa+bbb/ccc?param=hello%20there&other=true"));
        when(request.getUserPrincipal()).thenReturn(principal);
        when(request.getMethod()).thenReturn(method);
        when(request.getContentRead()).thenReturn(requestSize);
        when(request.getHeader("Content-Type")).thenReturn(requestContentType);
        when(response.getStatus()).thenReturn(responseCode);
        when(response.getContentCount()).thenReturn(responseSize);
        when(response.getHeader("Content-Type")).thenReturn(responseContentType);

        createAndRegisterNewRequestToken();
        currentTime = currentTimeMillisProvider.getCurrentTimeMillis();
    }

    @AfterMethod
    final public void teardownAbstract()
            throws IOException
    {
        if (!file.delete()) {
            throw new IOException("Error deleting " + file.getAbsolutePath());
        }
    }

    @Test
    public void testWriteLog()
            throws Exception
    {
        logger.log(request, response);
        stopLogger();

        String actual = Files.asCharSource(file, Charsets.UTF_8).read();
        String expected = getExpectedLogLine(timestamp, "9.9.9.9", method, pathQuery, user, agent, responseCode, requestSize, responseSize, currentTime - request.getTimeStamp());
        assertEquals(actual, expected);
    }

    @Test
    public void testIgnoreForwardedFor()
            throws Exception
    {
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("1.1.1.1, 2.2.2.2", "3.3.3.3, 4.4.4.4")));

        logger.log(request, response);
        stopLogger();

        String actual = Files.asCharSource(file, Charsets.UTF_8).read();
        String expected = getExpectedLogLine(timestamp, "9.9.9.9", method, pathQuery, user, agent, responseCode, requestSize, responseSize, currentTime - request.getTimeStamp());
        assertEquals(actual, expected);
    }

    @Test
    public void testUseForwardedFor()
            throws Exception
    {
        when(request.getRemoteAddr()).thenReturn("10.10.10.10");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("1.1.1.1, 2.2.2.2", "3.3.3.3, 4.4.4.4")));

        logger.log(request, response);
        stopLogger();

        String actual = Files.asCharSource(file, Charsets.UTF_8).read();
        String expected = getExpectedLogLine(timestamp, "4.4.4.4", method, pathQuery, user, agent, responseCode, requestSize, responseSize, currentTime - request.getTimeStamp());
        assertEquals(actual, expected);
    }

    @Test
    public void testUseForwardedForTwoHops()
            throws Exception
    {
        when(request.getRemoteAddr()).thenReturn("10.10.10.10");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("1.1.1.1, 2.2.2.2", "3.3.3.3, 10.11.12.13")));

        logger.log(request, response);
        stopLogger();

        String actual = Files.asCharSource(file, Charsets.UTF_8).read();
        String expected = getExpectedLogLine(timestamp, "3.3.3.3", method, pathQuery, user, agent, responseCode, requestSize, responseSize, currentTime - request.getTimeStamp());
        assertEquals(actual, expected);
    }

    @Test
    public void testUseForwardedForThreeHops()
            throws Exception
    {
        when(request.getRemoteAddr()).thenReturn("10.10.10.10");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("1.1.1.1, 2.2.2.2", "10.14.15.16, 10.11.12.13")));

        logger.log(request, response);
        stopLogger();

        String actual = Files.asCharSource(file, Charsets.UTF_8).read();
        String expected = getExpectedLogLine(timestamp, "2.2.2.2", method, pathQuery, user, agent, responseCode, requestSize, responseSize, currentTime - request.getTimeStamp());
        assertEquals(actual, expected);
    }

    @Test
    public void testUseForwardedForInternal()
            throws Exception
    {
        when(request.getRemoteAddr()).thenReturn("10.10.10.10");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("10.11.12.13")));

        logger.log(request, response);
        stopLogger();

        String actual = Files.asCharSource(file, Charsets.UTF_8).read();
        String expected = getExpectedLogLine(timestamp, "10.11.12.13", method, pathQuery, user, agent, responseCode, requestSize, responseSize, currentTime - request.getTimeStamp());
        assertEquals(actual, expected);
    }

    @Test
    public void testUseForwardedForInternalTwoHops()
            throws Exception
    {
        when(request.getRemoteAddr()).thenReturn("10.10.10.10");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("10.14.15.16, 10.11.12.13")));

        logger.log(request, response);
        stopLogger();

        String actual = Files.asCharSource(file, Charsets.UTF_8).read();
        String expected = getExpectedLogLine(timestamp, "10.14.15.16", method, pathQuery, user, agent, responseCode, requestSize, responseSize, currentTime - request.getTimeStamp());
        assertEquals(actual, expected);
    }

    @Test
    public void testInvalidIpAddress()
            throws Exception
    {
        when(request.getRemoteAddr()).thenReturn("10.10.10.10");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("notanaddr, 10.14.15.16, 10.11.12.13")));

        logger.log(request, response);
        stopLogger();

        String actual = Files.asCharSource(file, Charsets.UTF_8).read();
        String expected = getExpectedLogLine(timestamp, "10.14.15.16", method, pathQuery, user, agent, responseCode, requestSize, responseSize, currentTime - request.getTimeStamp());
        assertEquals(actual, expected);
    }
}
