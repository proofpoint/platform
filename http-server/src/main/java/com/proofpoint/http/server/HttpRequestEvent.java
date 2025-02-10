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
import jakarta.annotation.Nullable;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import javax.net.ssl.SSLSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.time.Instant;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
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
            long beginToHandleMillis,
            long beginToEndMillis,
            long firstToLastContentTimeInMillis,
            @Nullable DoubleSummaryStats responseContentInterarrivalStats,
            ClientAddressExtractor clientAddressExtractor)
    {
        String user = null;
        Request.AuthenticationState authenticationState = Request.getAuthenticationState(request);
        if (authenticationState != null) {
            Principal principal = authenticationState.getUserPrincipal();
            if (principal != null) {
                user = principal.getName();
            }
        }

        long timeToLastByte = max(currentTimeInMillis - Request.getTimeStamp(request), 0);

        String requestUri = null;
        if (request.getHttpURI() != null) {
            requestUri = request.getHttpURI().getPathQuery();
        }

        String method = request.getMethod();
        if (method != null) {
            method = method.toUpperCase();
        }

        return new HttpRequestEvent(
                Instant.ofEpochMilli(Request.getTimeStamp(request)),
                getCurrentTraceToken(),
                clientAddressExtractor.clientAddressFor(requestAdapter(request)),
                method,
                requestUri,
                user,
                request.getHeaders().get("User-Agent"),
                Request.getContentBytesRead(request),
                Response.getContentBytesWritten(response),
                response.getStatus(),
                timeToLastByte,
                beginToHandleMillis,
                beginToEndMillis,
                firstToLastContentTimeInMillis,
                responseContentInterarrivalStats,
                request.getConnectionMetaData().getHttpVersion().asString(),
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
    private final long beginToHandleMillis;
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
            long beginToHandleMillis,
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
        this.beginToHandleMillis = beginToHandleMillis;
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
        return new Duration(beginToHandleMillis, MILLISECONDS);
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

    private static HttpServletRequest requestAdapter(Request request)
    {
        return new HttpServletRequest()
        {
            @Override
            public String getAuthType()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public Cookie[] getCookies()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public long getDateHeader(String s)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getHeader(String s)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public Enumeration<String> getHeaders(String s)
            {
                return request.getHeaders().getValues(s);
            }

            @Override
            public Enumeration<String> getHeaderNames()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getIntHeader(String s)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getMethod()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getPathInfo()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getPathTranslated()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getContextPath()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getQueryString()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getRemoteUser()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isUserInRole(String s)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public Principal getUserPrincipal()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getRequestedSessionId()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getRequestURI()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public StringBuffer getRequestURL()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getServletPath()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public HttpSession getSession(boolean b)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public HttpSession getSession()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String changeSessionId()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isRequestedSessionIdValid()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isRequestedSessionIdFromCookie()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isRequestedSessionIdFromURL()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean authenticate(HttpServletResponse httpServletResponse) throws IOException, ServletException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public void login(String s, String s1) throws ServletException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public void logout() throws ServletException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public Collection<Part> getParts() throws IOException, ServletException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public Part getPart(String s) throws IOException, ServletException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass) throws IOException, ServletException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object getAttribute(String s)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public Enumeration<String> getAttributeNames()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getCharacterEncoding()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setCharacterEncoding(String s) throws UnsupportedEncodingException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getContentLength()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public long getContentLengthLong()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getContentType()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ServletInputStream getInputStream() throws IOException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getParameter(String s)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public Enumeration<String> getParameterNames()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String[] getParameterValues(String s)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<String, String[]> getParameterMap()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getProtocol()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getScheme()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getServerName()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getServerPort()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public BufferedReader getReader() throws IOException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getRemoteAddr()
            {
                return Request.getRemoteAddr(request);
            }

            @Override
            public String getRemoteHost()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setAttribute(String s, Object o)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public void removeAttribute(String s)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public Locale getLocale()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public Enumeration<Locale> getLocales()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isSecure()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public RequestDispatcher getRequestDispatcher(String s)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getRemotePort()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getLocalName()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getLocalAddr()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getLocalPort()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ServletContext getServletContext()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public AsyncContext startAsync() throws IllegalStateException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isAsyncStarted()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isAsyncSupported()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public AsyncContext getAsyncContext()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public DispatcherType getDispatcherType()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getRequestId()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getProtocolRequestId()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ServletConnection getServletConnection()
            {
                throw new UnsupportedOperationException();
            }
        };
    }
}
