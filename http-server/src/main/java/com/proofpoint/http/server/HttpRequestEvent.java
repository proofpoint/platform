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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.proofpoint.units.Duration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import java.security.Principal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentRequestToken;
import static java.lang.Math.max;

@JsonPropertyOrder({ "time", "traceToken", "sourceIp", "method", "requestUri", "username", "userAgent",
        "responseCode", "requestSize", "responseSize", "timeToLastByte"})
class HttpRequestEvent
{
    static HttpRequestEvent createHttpRequestEvent(Request request, Response response, long currentTimeInMillis, ClientAddressExtractor clientAddressExtractor)
    {
        String user = null;
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            user = principal.getName();
        }

        String token = getCurrentRequestToken();

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
                token,
                clientAddressExtractor.clientAddressFor(request),
                method,
                requestUri,
                user,
                request.getHeader("User-Agent"),
                request.getContentRead(),
                response.getContentCount(),
                response.getStatus(),
                timeToLastByte
        );
    }

    private final Instant timeStamp;
    private final String traceToken;
    private final String clientAddress;
    private final String method;
    private final String requestUri;
    private final String user;
    private final String agent;
    private final long requestSize;
    private final long responseSize;
    private final int responseCode;
    private final long timeToLastByte;

    private HttpRequestEvent(
            Instant timeStamp,
            String traceToken,
            String clientAddress,
            String method,
            String requestUri,
            String user,
            String agent,
            long requestSize,
            long responseSize,
            int responseCode,
            long timeToLastByte)
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
    }

    @JsonProperty("time")
    public Instant getTimeStamp()
    {
        return timeStamp;
    }

    @JsonProperty
    public String getTraceToken()
    {
        return traceToken;
    }

    @JsonProperty("sourceIp")
    public String getClientAddress()
    {
        return clientAddress;
    }

    @JsonProperty
    public String getMethod()
    {
        return method;
    }

    @JsonProperty
    public String getRequestUri()
    {
        return requestUri;
    }

    @JsonProperty("username")
    public String getUser()
    {
        return user;
    }

    @JsonProperty("userAgent")
    public String getAgent()
    {
        return agent;
    }

    @JsonProperty
    public long getRequestSize()
    {
        return requestSize;
    }

    @JsonProperty
    public long getResponseSize()
    {
        return responseSize;
    }

    @JsonProperty
    public int getResponseCode()
    {
        return responseCode;
    }

    public long getTimeToLastByte()
    {
        return timeToLastByte;
    }

    @JsonProperty("timeToLastByte")
    public Duration getTimeToLastByteDuration() {
        return new Duration(timeToLastByte, TimeUnit.MILLISECONDS);
    }
}
