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
import com.proofpoint.units.Duration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;
import java.security.Principal;
import java.time.Instant;

import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentRequestToken;
import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@JsonPropertyOrder({ "time", "traceToken", "sourceIp", "method", "requestUri", "username", "userAgent",
        "responseCode", "requestSize", "responseSize", "protocolVersion", "tlsProtocolVersion", "tlsCipherSuite",
        "timeToDispatch", "timeToRequestEnd", "timeResponseContent", "responseContentChunk", "timeToLastByte"})
class VerboseHttpRequestEvent
{

    private final HttpRequestEvent delegate;

    VerboseHttpRequestEvent(HttpRequestEvent delegate)
    {
        this.delegate = delegate;
    }

    @JsonProperty("time")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    public Instant getTimeStamp()
    {
        return delegate.getTimeStamp();
    }

    @JsonProperty
    public String getTraceToken()
    {
        return delegate.getTraceToken();
    }

    @JsonProperty("sourceIp")
    public String getClientAddress()
    {
        return delegate.getClientAddress();
    }

    @JsonProperty
    public String getMethod()
    {
        return delegate.getMethod();
    }

    @JsonProperty
    public String getRequestUri()
    {
        return delegate.getRequestUri();
    }

    @JsonProperty("username")
    public String getUser()
    {
        return delegate.getUser();
    }

    @JsonProperty("userAgent")
    public String getAgent()
    {
        return delegate.getAgent();
    }

    @JsonProperty
    public long getRequestSize()
    {
        return delegate.getRequestSize();
    }

    @JsonProperty
    public long getResponseSize()
    {
        return delegate.getResponseSize();
    }

    @JsonProperty
    public int getResponseCode()
    {
        return delegate.getResponseCode();
    }

    public long getTimeToLastByte()
    {
        return delegate.getTimeToLastByte();
    }

    @JsonProperty("timeToLastByte")
    public Duration getTimeToLastByteDuration() {
        return delegate.getTimeToLastByteDuration();
    }

    @JsonProperty
    public Duration getTimeToDispatch()
    {
        return delegate.getTimeToDispatch();
    }

    @JsonProperty
    public Duration getTimeToRequestEnd()
    {
        return delegate.getTimeToRequestEnd();
    }

    @Nullable
    @JsonProperty
    public Duration getTimeResponseContent()
    {
        return delegate.getTimeResponseContent();
    }

    @Nullable
    @JsonProperty
    public DoubleSummaryStats getResponseContentChunk()
    {
        return delegate.getResponseContentChunk();
    }

    @JsonProperty
    public String getProtocolVersion()
    {
        return delegate.getProtocolVersion();
    }

    @JsonProperty
    public String getTlsProtocolVersion()
    {
        return delegate.getTlsProtocolVersion();
    }

    @JsonProperty
    public String getTlsCipherSuite()
    {
        return delegate.getTlsCipherSuite();
    }
}
