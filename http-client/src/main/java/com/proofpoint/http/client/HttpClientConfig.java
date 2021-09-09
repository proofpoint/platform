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
package com.proofpoint.http.client;

import com.google.common.net.HostAndPort;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;
import com.proofpoint.configuration.DefunctConfig;
import com.proofpoint.configuration.LegacyConfig;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.Duration;
import com.proofpoint.units.MaxDataSize;
import com.proofpoint.units.MinDataSize;
import com.proofpoint.units.MinDuration;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import static com.proofpoint.units.DataSize.Unit.KILOBYTE;
import static com.proofpoint.units.DataSize.Unit.MEGABYTE;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

@DefunctConfig({"http-client.max-connections", "http-client.keep-alive-interval"})
public class HttpClientConfig
{
    public static final String JAVAX_NET_SSL_KEY_STORE = "javax.net.ssl.keyStore";
    public static final String JAVAX_NET_SSL_KEY_STORE_PASSWORD = "javax.net.ssl.keyStorePassword";
    public static final String JAVAX_NET_SSL_TRUST_STORE = "javax.net.ssl.trustStore";
    public static final String JAVAX_NET_SSL_TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";

    private Duration connectTimeout = new Duration(2, SECONDS);
    private Duration requestTimeout = null;
    private Duration idleTimeout = new Duration(1, MINUTES);
    private int maxConnectionsPerServer = 100;
    private int maxRequestsQueuedPerDestination = 100;
    private DataSize maxContentLength = new DataSize(16, MEGABYTE);
    private DataSize requestBufferSize = new DataSize(4, KILOBYTE);
    private DataSize responseBufferSize = new DataSize(16, KILOBYTE);
    private HostAndPort socksProxy;
    private String keyStorePath = System.getProperty(JAVAX_NET_SSL_KEY_STORE);
    private String keyStorePassword = System.getProperty(JAVAX_NET_SSL_KEY_STORE_PASSWORD);
    private String trustStorePath;
    private String trustStorePassword;

    private int maxThreads = 200;
    private int minThreads = 8;
    private int timeoutThreads = 1;
    private int timeoutConcurrency = 1;

    private boolean http2Enabled;
    private DataSize http2InitialSessionReceiveWindowSize = new DataSize(16, MEGABYTE);
    private DataSize http2InitialStreamReceiveWindowSize = new DataSize(16, MEGABYTE);
    private DataSize http2InputBufferSize = new DataSize(8, KILOBYTE);
    private int selectorCount = 2;

    public boolean isHttp2Enabled()
    {
        return http2Enabled;
    }

    @Config("http-client.http2.enabled")
    @ConfigDescription("Enable the HTTP/2 transport")
    public HttpClientConfig setHttp2Enabled(boolean http2Enabled)
    {
        this.http2Enabled = http2Enabled;
        return this;
    }

    @NotNull
    @MinDuration("0ms")
    public Duration getConnectTimeout()
    {
        return connectTimeout;
    }

    @Config("http-client.connect-timeout")
    public HttpClientConfig setConnectTimeout(Duration connectTimeout)
    {
        this.connectTimeout = connectTimeout;
        return this;
    }

    @MinDuration("0ms")
    public Duration getRequestTimeout()
    {
        return requestTimeout;
    }

    @Config("http-client.request-timeout")
    public HttpClientConfig setRequestTimeout(Duration requestTimeout)
    {
        this.requestTimeout = requestTimeout;
        return this;
    }

    @NotNull
    @MinDuration("0ms")
    public Duration getIdleTimeout()
    {
        return idleTimeout;
    }

    @Config("http-client.idle-timeout")
    @LegacyConfig("http-client.read-timeout")
    public HttpClientConfig setIdleTimeout(Duration idleTimeout)
    {
        this.idleTimeout = idleTimeout;
        return this;
    }

    @Min(1)
    public int getMaxConnectionsPerServer()
    {
        return maxConnectionsPerServer;
    }

    @Config("http-client.max-connections-per-server")
    public HttpClientConfig setMaxConnectionsPerServer(int maxConnectionsPerServer)
    {
        this.maxConnectionsPerServer = maxConnectionsPerServer;
        return this;
    }

    @Min(1)
    public int getMaxRequestsQueuedPerDestination()
    {
        return maxRequestsQueuedPerDestination;
    }

    @Config("http-client.max-requests-queued-per-destination")
    public HttpClientConfig setMaxRequestsQueuedPerDestination(int maxRequestsQueuedPerDestination)
    {
        this.maxRequestsQueuedPerDestination = maxRequestsQueuedPerDestination;
        return this;
    }

    @NotNull
    public DataSize getMaxContentLength()
    {
        return maxContentLength;
    }

    @Config("http-client.max-content-length")
    public HttpClientConfig setMaxContentLength(DataSize maxContentLength)
    {
        this.maxContentLength = maxContentLength;
        return this;
    }

    @NotNull
    @MaxDataSize("32MB")
    public DataSize getRequestBufferSize()
    {
        return requestBufferSize;
    }

    @Config("http-client.request-buffer-size")
    public HttpClientConfig setRequestBufferSize(DataSize requestBufferSize)
    {
        this.requestBufferSize = requestBufferSize;
        return this;
    }

    @NotNull
    @MaxDataSize("32MB")
    public DataSize getResponseBufferSize()
    {
        return responseBufferSize;
    }

    @Config("http-client.response-buffer-size")
    public HttpClientConfig setResponseBufferSize(DataSize responseBufferSize)
    {
        this.responseBufferSize = responseBufferSize;
        return this;
    }

    public HostAndPort getSocksProxy()
    {
        return socksProxy;
    }

    @Config("http-client.socks-proxy")
    public HttpClientConfig setSocksProxy(HostAndPort socksProxy)
    {
        this.socksProxy = socksProxy;
        return this;
    }

    public String getKeyStorePath()
    {
        return keyStorePath;
    }

    @Config("http-client.key-store-path")
    public HttpClientConfig setKeyStorePath(String keyStorePath)
    {
        this.keyStorePath = keyStorePath;
        return this;
    }

    public String getKeyStorePassword()
    {
        return keyStorePassword;
    }

    @Config("http-client.key-store-password")
    public HttpClientConfig setKeyStorePassword(String keyStorePassword)
    {
        this.keyStorePassword = keyStorePassword;
        return this;
    }

    public String getTrustStorePath()
    {
        return trustStorePath;
    }

    @Config("http-client.trust-store-path")
    public HttpClientConfig setTrustStorePath(String trustStorePath)
    {
        this.trustStorePath = trustStorePath;
        return this;
    }

    public String getTrustStorePassword()
    {
        return trustStorePassword;
    }

    @Config("http-client.trust-store-password")
    public HttpClientConfig setTrustStorePassword(String trustStorePassword)
    {
        this.trustStorePassword = trustStorePassword;
        return this;
    }

    @NotNull
    @MinDataSize("1kB")
    @MaxDataSize("1GB")
    public DataSize getHttp2InitialSessionReceiveWindowSize()
    {
        return http2InitialSessionReceiveWindowSize;
    }

    @Config("http-client.http2.session-receive-window-size")
    @ConfigDescription("Initial size of session's flow control receive window for HTTP/2")
    public HttpClientConfig setHttp2InitialSessionReceiveWindowSize(DataSize http2InitialSessionReceiveWindowSize)
    {
        this.http2InitialSessionReceiveWindowSize = http2InitialSessionReceiveWindowSize;
        return this;
    }

    @NotNull
    @MinDataSize("1kB")
    @MaxDataSize("1GB")
    public DataSize getHttp2InitialStreamReceiveWindowSize()
    {
        return http2InitialStreamReceiveWindowSize;
    }

    @Config("http-client.http2.stream-receive-window-size")
    @ConfigDescription("Initial size of stream's flow control receive window for HTTP/2")
    public HttpClientConfig setHttp2InitialStreamReceiveWindowSize(DataSize http2InitialStreamReceiveWindowSize)
    {
        this.http2InitialStreamReceiveWindowSize = http2InitialStreamReceiveWindowSize;
        return this;
    }

    @NotNull
    @MinDataSize("1kB")
    @MaxDataSize("32MB")
    public DataSize getHttp2InputBufferSize()
    {
        return http2InputBufferSize;
    }

    @Config("http-client.http2.input-buffer-size")
    @ConfigDescription("Size of the buffer used to read from the network for HTTP/2")
    public HttpClientConfig setHttp2InputBufferSize(DataSize http2InputBufferSize)
    {
        this.http2InputBufferSize = http2InputBufferSize;
        return this;
    }

    @Min(1)
    public int getSelectorCount()
    {
        return selectorCount;
    }

    @Config("http-client.selector-count")
    public HttpClientConfig setSelectorCount(int selectorCount)
    {
        this.selectorCount = selectorCount;
        return this;
    }

    @Min(1)
    public int getMaxThreads()
    {
        return maxThreads;
    }

    @Config("http-client.max-threads")
    @LegacyConfig("http-client.threads")
    public HttpClientConfig setMaxThreads(int maxThreads)
    {
        this.maxThreads = maxThreads;
        return this;
    }

    @Min(1)
    public int getMinThreads()
    {
        return minThreads;
    }

    @Config("http-client.min-threads")
    public HttpClientConfig setMinThreads(int minThreads)
    {
        this.minThreads = minThreads;
        return this;
    }

    @Min(1)
    public int getTimeoutThreads()
    {
        return timeoutThreads;
    }

    @Config("http-client.timeout-threads")
    @ConfigDescription("Total number of timeout threads")
    public HttpClientConfig setTimeoutThreads(int timeoutThreads)
    {
        this.timeoutThreads = timeoutThreads;
        return this;
    }

    @Min(1)
    public int getTimeoutConcurrency()
    {
        return timeoutConcurrency;
    }

    @Config("http-client.timeout-concurrency")
    @ConfigDescription("Number of concurrent locks for timeout")
    public HttpClientConfig setTimeoutConcurrency(int timeoutConcurrency)
    {
        this.timeoutConcurrency = timeoutConcurrency;
        return this;
    }
}
