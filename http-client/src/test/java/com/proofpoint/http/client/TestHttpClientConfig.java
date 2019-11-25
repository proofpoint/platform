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

import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.Duration;
import org.testng.annotations.Test;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Map;

import static com.proofpoint.configuration.testing.ConfigAssertions.assertFullMapping;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertLegacyEquivalence;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static com.proofpoint.configuration.testing.ConfigAssertions.recordDefaults;
import static com.proofpoint.http.client.HttpClientConfig.JAVAX_NET_SSL_KEY_STORE;
import static com.proofpoint.http.client.HttpClientConfig.JAVAX_NET_SSL_KEY_STORE_PASSWORD;
import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;
import static com.proofpoint.testing.ValidationAssertions.assertValidates;
import static com.proofpoint.units.DataSize.Unit.KILOBYTE;
import static com.proofpoint.units.DataSize.Unit.MEGABYTE;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

@SuppressWarnings("deprecation")
public class TestHttpClientConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(HttpClientConfig.class)
                .setHttp2Enabled(false)
                .setConnectTimeout(new Duration(2, SECONDS))
                .setRequestTimeout(null)
                .setIdleTimeout(new Duration(1, MINUTES))
                .setMaxConnectionsPerServer(100)
                .setMaxRequestsQueuedPerDestination(100)
                .setMaxContentLength(new DataSize(16, MEGABYTE))
                .setRequestBufferSize(new DataSize(4, KILOBYTE))
                .setResponseBufferSize(new DataSize(16, KILOBYTE))
                .setSocksProxy(null)
                .setKeyStorePath(System.getProperty(JAVAX_NET_SSL_KEY_STORE))
                .setKeyStorePassword(System.getProperty(JAVAX_NET_SSL_KEY_STORE_PASSWORD))
                .setTrustStorePath(null)
                .setTrustStorePassword(null)
                .setHttp2InitialSessionReceiveWindowSize(new DataSize(16, MEGABYTE))
                .setHttp2InitialStreamReceiveWindowSize(new DataSize(16, MEGABYTE))
                .setHttp2InputBufferSize(new DataSize(8, KILOBYTE))
                .setSelectorCount(2)
                .setMaxThreads(200)
                .setMinThreads(8)
                .setTimeoutConcurrency(1)
                .setTimeoutThreads(1));
        ;
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-client.http2.enabled", "true")
                .put("http-client.connect-timeout", "4s")
                .put("http-client.request-timeout", "15s")
                .put("http-client.idle-timeout", "5s")
                .put("http-client.max-connections-per-server", "3")
                .put("http-client.max-requests-queued-per-destination", "10")
                .put("http-client.max-content-length", "1MB")
                .put("http-client.request-buffer-size", "42kB")
                .put("http-client.response-buffer-size", "43kB")
                .put("http-client.socks-proxy", "localhost:1080")
                .put("http-client.key-store-path", "key-store")
                .put("http-client.key-store-password", "key-store-password")
                .put("http-client.trust-store-path", "trust-store")
                .put("http-client.trust-store-password", "trust-store-password")
                .put("http-client.http2.session-receive-window-size", "7MB")
                .put("http-client.http2.stream-receive-window-size", "7MB")
                .put("http-client.http2.input-buffer-size", "1MB")
                .put("http-client.selector-count", "16")
                .put("http-client.max-threads", "33")
                .put("http-client.min-threads", "11")
                .put("http-client.timeout-concurrency", "33")
                .put("http-client.timeout-threads", "44")
                .build();

        HttpClientConfig expected = new HttpClientConfig()
                .setHttp2Enabled(true)
                .setConnectTimeout(new Duration(4, SECONDS))
                .setRequestTimeout(new Duration(15, SECONDS))
                .setIdleTimeout(new Duration(5, SECONDS))
                .setMaxConnectionsPerServer(3)
                .setMaxRequestsQueuedPerDestination(10)
                .setMaxContentLength(new DataSize(1, MEGABYTE))
                .setRequestBufferSize(new DataSize(42, KILOBYTE))
                .setResponseBufferSize(new DataSize(43, KILOBYTE))
                .setSocksProxy(HostAndPort.fromParts("localhost", 1080))
                .setKeyStorePath("key-store")
                .setKeyStorePassword("key-store-password")
                .setTrustStorePath("trust-store")
                .setTrustStorePassword("trust-store-password")
                .setHttp2InitialSessionReceiveWindowSize(new DataSize(7, MEGABYTE))
                .setHttp2InitialStreamReceiveWindowSize(new DataSize(7, MEGABYTE))
                .setHttp2InputBufferSize(new DataSize(1, MEGABYTE))
                .setHttp2InitialStreamReceiveWindowSize(new DataSize(7, MEGABYTE))
                .setSelectorCount(16)
                .setMaxThreads(33)
                .setMinThreads(11)
                .setTimeoutConcurrency(33)
                .setTimeoutThreads(44);

        assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("http-client.idle-timeout", "111m")
                .put("http-client.max-threads", "111")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("http-client.read-timeout", "111m")
                .put("http-client.threads", "111")
                .build();

        assertLegacyEquivalence(HttpClientConfig.class, currentProperties, oldProperties);
    }

    @Test
    public void testValidations()
    {
        assertValidates(new HttpClientConfig().setMaxRequestsQueuedPerDestination(1).setMaxConnectionsPerServer(1));
        assertFailsValidation(new HttpClientConfig().setConnectTimeout(null), "connectTimeout", "must not be null", NotNull.class);
        assertFailsValidation(new HttpClientConfig().setIdleTimeout(null), "idleTimeout", "must not be null", NotNull.class);
        assertFailsValidation(new HttpClientConfig().setMaxConnectionsPerServer(0), "maxConnectionsPerServer", "must be greater than or equal to 1", Min.class);
        assertFailsValidation(new HttpClientConfig().setMaxRequestsQueuedPerDestination(0), "maxRequestsQueuedPerDestination", "must be greater than or equal to 1", Min.class);
        assertFailsValidation(new HttpClientConfig().setMaxContentLength(null), "maxContentLength", "must not be null", NotNull.class);
    }
}
