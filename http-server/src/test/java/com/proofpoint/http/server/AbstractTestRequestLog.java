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

import com.google.common.io.Files;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.DataSize.Unit;
import com.proofpoint.units.Duration;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.net.ssl.SSLSession;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.http.server.HttpRequestEvent.createHttpRequestEvent;
import static com.proofpoint.tracetoken.TraceTokenManager.clearRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.createAndRegisterNewRequestToken;
import static com.proofpoint.units.Duration.succinctDuration;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.eclipse.jetty.http.HttpVersion.HTTP_2;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;

public abstract class AbstractTestRequestLog
{
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    Request request;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    Response response;
    @Mock
    SSLSession sslSession;
    @Mock
    ClientAddressExtractor clientAddressExtractor;
    private AutoCloseable openMocks;
    private MockedStatic<Request> mockedRequest;
    private MockedStatic<Response> mockedResponse;
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
    protected RequestLog logger;
    private long currentTime;

    protected abstract void setup(HttpServerConfig httpServerConfig)
            throws IOException;

    protected abstract String getExpectedLogLine(
            long timestamp,
            String clientAddr,
            String method,
            String pathQuery,
            String user,
            String agent,
            int responseCode,
            long requestSize,
            long responseSize,
            String protocolVersion,
            String tlsProtocolVersion,
            String tlsCipherSuite,
            long timeToDispatch,
            long timeToRequestEnd,
            long timeResponseContent,
            long responseContentChunkCount,
            long responseContentChunkMax,
            long timeToLastByte
    );

    @BeforeMethod
    public final void setupAbstract()
            throws IOException
    {
        openMocks = openMocks(this);
        mockedRequest = mockStatic(Request.class, RETURNS_DEEP_STUBS);
        mockedResponse = mockStatic(Response.class, RETURNS_DEEP_STUBS);

        timeToLastByte = 3453;
        timeToFirstByte = timeToLastByte - 333;
        now = 1538192474453L;
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

        HttpServerConfig httpServerConfig = new HttpServerConfig()
                .setLogPath(file.getAbsolutePath())
                .setLogMaxHistory(1)
                .setLogMaxSegmentSize(new DataSize(1, Unit.MEGABYTE))
                .setLogMaxTotalSize(new DataSize(1, Unit.GIGABYTE));
        setup(httpServerConfig);

        when(sslSession.getProtocol()).thenReturn("TLS1.0");
        when(sslSession.getCipherSuite()).thenReturn("TLS_RSA_WITH_AES_256_CBC_SHA");
        when(clientAddressExtractor.clientAddressFor(any())).thenReturn("9.9.9.9");
        when(Request.getTimeStamp(request)).thenReturn(timestamp);
        when(request.getHeaders().get("User-Agent")).thenReturn(agent);
        when(request.getHeaders().get("Referer")).thenReturn(referrer);
        when(Request.getRemoteAddr(request)).thenReturn("8.8.8.8");
        when(request.getHeaders().getValues("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(List.of()));
        when(request.getConnectionMetaData().getProtocol()).thenReturn("unknown");
        when(request.getHeaders().get("X-FORWARDED-PROTO")).thenReturn(protocol);
        when(request.getHttpURI()).thenReturn(HttpURI.from("http://www.example.com/aaa+bbb/ccc?param=hello%20there&other=true"));
        when(Request.getAuthenticationState(request).getUserPrincipal().getName()).thenReturn(user);
        when(request.getMethod()).thenReturn(method);
        when(Request.getContentBytesRead(request)).thenReturn(requestSize);
        when(request.getHeaders().get("Content-Type")).thenReturn(requestContentType);
        when(request.getConnectionMetaData().getHttpVersion()).thenReturn(HTTP_2);
        when(response.getStatus()).thenReturn(responseCode);
        when(Response.getContentBytesWritten(response)).thenReturn(responseSize);
        when(response.getHeaders().get("Content-Type")).thenReturn(responseContentType);

        createAndRegisterNewRequestToken();
        currentTime = timestamp + timeToLastByte;
    }

    @AfterMethod
    public final void teardownAbstract()
            throws Exception
    {
        mockedResponse.close();
        mockedRequest.close();
        openMocks.close();
        if (!file.delete()) {
            throw new IOException("Error deleting " + file.getAbsolutePath());
        }
    }

    @Test
    public void testWriteLogSimpleToken()
            throws Exception
    {
        createAndRegisterNewRequestToken();
        testWriteLog();
        clearRequestToken();
    }

    @Test
    public void testWriteLogComplexToken()
            throws Exception
    {
        createAndRegisterNewRequestToken("key", "value");
        testWriteLog();
        clearRequestToken();
    }

    @Test
    public void testWriteLogLocalComplexToken()
            throws Exception
    {
        createAndRegisterNewRequestToken("_key", "value");
        testWriteLog();
        clearRequestToken();
    }

    private void testWriteLog()
            throws Exception
    {
        DoubleSummaryStatistics summaryStatistics = new DoubleSummaryStatistics();
        summaryStatistics.accept(1);
        summaryStatistics.accept(3);
        HttpRequestEvent event = createHttpRequestEvent(
                request,
                response,
                sslSession,
                new RequestTiming(Instant.ofEpochMilli(timestamp),
                        Duration.valueOf("111ms"),
                        Duration.valueOf("222ms"),
                        Duration.valueOf("333ms"),
                        Duration.succinctDuration(timeToLastByte, MILLISECONDS),
                        new DoubleSummaryStats(summaryStatistics)),
                clientAddressExtractor
        );
        logger.log(event);
        logger.stop();

        String actual = Files.asCharSource(file, UTF_8).read();
        String expected = getExpectedLogLine(
                timestamp,
                "9.9.9.9",
                method,
                pathQuery,
                user,
                agent,
                responseCode,
                requestSize,
                responseSize,
                "HTTP/2.0",
                "TLS1.0",
                "TLS_RSA_WITH_AES_256_CBC_SHA",
                111,
                222,
                333,
                2,
                3,
                currentTime - Request.getTimeStamp(request));
        assertEquals(actual, expected);
    }
}
