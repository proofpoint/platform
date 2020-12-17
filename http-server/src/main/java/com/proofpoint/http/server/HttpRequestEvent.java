/*
 * Copyright 2010 Proofpoint, Inc.
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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.tracetoken.TraceToken;
import com.proofpoint.units.Duration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;

import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@JsonPropertyOrder({ "t", "tt", "ip", "m", "u", "user",
        "c", "qs", "rs",
        "td", "tq", "tr", "rc", "tl"})
class HttpRequestEvent
{
    static HttpRequestEvent createHttpRequestEvent(
            Request request,
            Response response,
            @Nullable SSLSession sslSession,
            long currentTimeInMillis,
            long beginToDispatchMillis,
            long beginToEndMillis,
            long firstToLastContentTimeInMillis,
            @Nullable DoubleSummaryStats responseContentInterarrivalStats,
            ClientAddressExtractor clientAddressExtractor)
    {
        String user = null;
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            user = principal.getName();
        }

        long timeToLastByte = max(currentTimeInMillis - request.getTimeStamp(), 0);

        String requestUri = null;
        if (request.getHttpURI() != null) {
            requestUri = request.getHttpURI().getPathQuery();
        }

        String method = request.getMethod();
        if (method != null) {
            method = method.toUpperCase();
        }

        return new HttpRequestEvent(
                Instant.ofEpochMilli(request.getTimeStamp()),
                getCurrentTraceToken(),
                clientAddressExtractor.clientAddressFor(request),
                method,
                requestUri,
                user,
                request.getHeader("User-Agent"),
                request.getContentRead(),
                response.getContentCount(),
                response.getStatus(),
                timeToLastByte,
                beginToDispatchMillis,
                beginToEndMillis,
                firstToLastContentTimeInMillis,
                responseContentInterarrivalStats,
                request.getHttpVersion().toString(),
                sslSession == null ? null : sslSession.getProtocol(),
                sslSession == null ? null : sslSession.getCipherSuite()
        );
    }

    private final Instant timeStamp;
    private final TraceToken traceToken;
    private final String clientAddress;
    private final String method;
    private final String requestUri;
    private final String user;
    private final String agent;
    private final long requestSize;
    private final long responseSize;
    private final int responseCode;
    private final long timeToLastByte;
    private final long beginToDispatchMillis;
    private final long beginToEndMillis;
    private final long firstToLastContentTimeInMillis;
    private final DoubleSummaryStats responseContentInterarrivalStats;
    private final String protocolVersion;
    private final String tlsProtocolVersion;
    private final String tlsCipherSuite;

    private HttpRequestEvent(
            Instant timeStamp,
            TraceToken traceToken,
            String clientAddress,
            String method,
            String requestUri,
            String user,
            String agent,
            long requestSize,
            long responseSize,
            int responseCode,
            long timeToLastByte,
            long beginToDispatchMillis,
            long beginToEndMillis,
            long firstToLastContentTimeInMillis,
            DoubleSummaryStats responseContentInterarrivalStats,
            String protocolVersion,
            String tlsProtocolVersion,
            String tlsCipherSuite)
    {
        this.timeStamp = timeStamp;
        this.traceToken = traceToken;
        this.clientAddress = clientAddress;
        this.method = method;
        this.requestUri = requestUri;
        this.user = user;
        this.agent = agent;
        this.requestSize = requestSize;
        this.responseSize = responseSize;
        this.responseCode = responseCode;
        this.timeToLastByte = timeToLastByte;
        this.beginToDispatchMillis = beginToDispatchMillis;
        this.beginToEndMillis = beginToEndMillis;
        this.firstToLastContentTimeInMillis = firstToLastContentTimeInMillis;
        this.responseContentInterarrivalStats = responseContentInterarrivalStats;
        this.protocolVersion = protocolVersion;
        this.tlsProtocolVersion = tlsProtocolVersion;
        this.tlsCipherSuite = tlsCipherSuite;
    }

    @JsonProperty("t")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    public Instant getTimeStamp()
    {
        return timeStamp;
    }

    public TraceToken getTraceToken()
    {
        return traceToken;
    }

    @JsonProperty("tt")
    Map<String, String> getTraceTokenForLog()
    {
        if (traceToken == null) {
            return null;
        }
        return ImmutableMap.copyOf(traceToken);
    }

    @JsonProperty("ip")
    public String getClientAddress()
    {
        return clientAddress;
    }

    @JsonProperty("m")
    public String getMethod()
    {
        return method;
    }

    @JsonProperty("u")
    public String getRequestUri()
    {
        return requestUri;
    }

    @JsonProperty
    public String getUser()
    {
        return user;
    }

    public String getAgent()
    {
        return agent;
    }

    @JsonProperty("qs")
    public long getRequestSize()
    {
        return requestSize;
    }

    @JsonProperty("rs")
    public long getResponseSize()
    {
        return responseSize;
    }

    @JsonProperty("c")
    public int getResponseCode()
    {
        return responseCode;
    }

    public long getTimeToLastByte()
    {
        return timeToLastByte;
    }

    @JsonProperty("tl")
    public Duration getTimeToLastByteDuration() {
        return new Duration(timeToLastByte, MILLISECONDS);
    }

    @JsonProperty("td")
    public Duration getTimeToDispatch()
    {
        return new Duration(beginToDispatchMillis, MILLISECONDS);
    }

    @JsonProperty("tq")
    public Duration getTimeToRequestEnd()
    {
        return new Duration(beginToEndMillis, MILLISECONDS);
    }

    @Nullable
    @JsonProperty("tr")
    public Duration getTimeResponseContent()
    {
        if (firstToLastContentTimeInMillis < 0) {
            return null;
        }
        return new Duration(firstToLastContentTimeInMillis, MILLISECONDS);
    }

    @Nullable
    @JsonProperty("rc")
    public DoubleSummaryStats getResponseContentChunk()
    {
        return responseContentInterarrivalStats;
    }

    public String getProtocolVersion()
    {
        return protocolVersion;
    }

    public String getTlsProtocolVersion()
    {
        return tlsProtocolVersion;
    }

    public String getTlsCipherSuite()
    {
        return tlsCipherSuite;
    }
}
