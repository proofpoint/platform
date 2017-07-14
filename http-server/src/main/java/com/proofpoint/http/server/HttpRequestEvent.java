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
import com.proofpoint.event.client.EventField;
import com.proofpoint.event.client.EventType;
import com.proofpoint.units.Duration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.joda.time.DateTime;

import java.security.Principal;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.event.client.EventField.EventFieldMapping.TIMESTAMP;
import static com.proofpoint.http.server.ClientInfoUtils.clientAddressFor;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentRequestToken;
import static java.lang.Math.max;

/**
 * @deprecated Will no longer be public.
 */
@EventType("HttpRequest")
@JsonPropertyOrder({ "time", "traceToken", "sourceIp", "method", "requestUri", "username", "userAgent",
        "responseCode", "requestSize", "responseSize", "timeToLastByte"})
@Deprecated
public class HttpRequestEvent
{
    /**
     * @deprecated Will no longer be public.
     */
    @Deprecated
    public static HttpRequestEvent createHttpRequestEvent(Request request, Response response, long currentTimeInMillis)
    {
        String user = null;
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            user = principal.getName();
        }

        String token = getCurrentRequestToken();

        long dispatchTime = request.getTimeStamp();
        long timeToDispatch = max(dispatchTime - request.getTimeStamp(), 0);

        Long timeToFirstByte = null;
        Object firstByteTime = request.getAttribute(TimingFilter.FIRST_BYTE_TIME);
        if (firstByteTime instanceof Long) {
            Long time = (Long) firstByteTime;
            timeToFirstByte = max(time - request.getTimeStamp(), 0);
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

        String protocol = request.getHeader("X-FORWARDED-PROTO");
        if (protocol == null) {
            protocol = request.getScheme();
        }
        if (protocol != null) {
            protocol = protocol.toLowerCase();
        }

        return new HttpRequestEvent(
                new DateTime(request.getTimeStamp()),
                token,
                clientAddressFor(request),
                protocol,
                method,
                requestUri,
                user,
                request.getHeader("User-Agent"),
                request.getHeader("Referer"),
                request.getContentRead(),
                request.getHeader("Content-Type"),
                response.getContentCount(),
                response.getStatus(),
                response.getHeader("Content-Type"),
                timeToDispatch,
                timeToFirstByte,
                timeToLastByte
        );
    }

    private final DateTime timeStamp;
    private final String traceToken;
    private final String clientAddress;
    private final String protocol;
    private final String method;
    private final String requestUri;
    private final String user;
    private final String agent;
    private final String referrer;
    private final long requestSize;
    private final String requestContentType;
    private final long responseSize;
    private final int responseCode;
    private final String responseContentType;
    private final long timeToDispatch;
    private final Long timeToFirstByte;
    private final long timeToLastByte;

    /**
     * @deprecated Will no longer be public.
     */
    @Deprecated
    public HttpRequestEvent(DateTime timeStamp,
            String traceToken,
            String clientAddress,
            String protocol,
            String method,
            String requestUri,
            String user,
            String agent,
            String referrer,
            long requestSize,
            String requestContentType,
            long responseSize,
            int responseCode,
            String responseContentType,
            long timeToDispatch,
            Long timeToFirstByte,
            long timeToLastByte)
    {
        this.timeStamp = timeStamp;
        this.traceToken = traceToken;
        this.clientAddress = clientAddress;
        this.protocol = protocol;
        this.method = method;
        this.requestUri = requestUri;
        this.user = user;
        this.agent = agent;
        this.referrer = referrer;
        this.requestSize = requestSize;
        this.requestContentType = requestContentType;
        this.responseSize = responseSize;
        this.responseCode = responseCode;
        this.responseContentType = responseContentType;
        this.timeToDispatch = timeToDispatch;
        this.timeToFirstByte = timeToFirstByte;
        this.timeToLastByte = timeToLastByte;
    }

    @JsonProperty("time")
    @EventField(fieldMapping = TIMESTAMP)
    public DateTime getTimeStamp()
    {
        return timeStamp;
    }

    @JsonProperty
    @EventField
    public String getTraceToken()
    {
        return traceToken;
    }

    @JsonProperty("sourceIp")
    @EventField
    public String getClientAddress()
    {
        return clientAddress;
    }

    @EventField
    public String getProtocol()
    {
        return protocol;
    }

    @JsonProperty
    @EventField
    public String getMethod()
    {
        return method;
    }

    @JsonProperty
    @EventField
    public String getRequestUri()
    {
        return requestUri;
    }

    @JsonProperty("username")
    @EventField
    public String getUser()
    {
        return user;
    }

    @JsonProperty("userAgent")
    @EventField
    public String getAgent()
    {
        return agent;
    }

    @EventField
    public String getReferrer()
    {
        return referrer;
    }

    @JsonProperty
    @EventField
    public long getRequestSize()
    {
        return requestSize;
    }

    @EventField
    public String getRequestContentType()
    {
        return requestContentType;
    }

    @JsonProperty
    @EventField
    public long getResponseSize()
    {
        return responseSize;
    }

    @JsonProperty
    @EventField
    public int getResponseCode()
    {
        return responseCode;
    }

    @EventField
    public String getResponseContentType()
    {
        return responseContentType;
    }

    @EventField
    public long getTimeToDispatch()
    {
        return timeToDispatch;
    }

    @EventField
    public Long getTimeToFirstByte()
    {
        return timeToFirstByte;
    }

    @EventField
    public long getTimeToLastByte()
    {
        return timeToLastByte;
    }

    @JsonProperty("timeToLastByte")
    public Duration getTimeToLastByteDuration() {
        return new Duration(timeToLastByte, TimeUnit.MILLISECONDS);
    }
}
