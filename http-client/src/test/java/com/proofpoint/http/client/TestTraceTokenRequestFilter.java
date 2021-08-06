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

import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;

import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.TraceTokenRequestFilter.TRACETOKEN_HEADER;
import static com.proofpoint.tracetoken.TraceTokenManager.addTraceTokenProperties;
import static com.proofpoint.tracetoken.TraceTokenManager.clearRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerRequestToken;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;

public class TestTraceTokenRequestFilter
{
    @Test
    public void testBasic()
    {
        registerRequestToken("testBasic");
        TraceTokenRequestFilter filter = new TraceTokenRequestFilter();
        Request original = prepareGet().setUri(URI.create("http://example.com")).build();

        Request filtered = filter.filterRequest(original);

        assertNotSame(filter, original);
        assertEquals(filtered.getUri(), original.getUri());
        assertEquals(original.getHeaders().size(), 0);
        assertEquals(filtered.getHeaders().size(), 1);
        assertEquals(filtered.getHeaders().get(TRACETOKEN_HEADER), List.of("testBasic"));
    }

    @Test
    public void testWithProperties()
    {
        registerRequestToken("testBasic");
        addTraceTokenProperties("key-b", "value-b", "key-a", "value-a", "key-c", "value-c");
        TraceTokenRequestFilter filter = new TraceTokenRequestFilter();
        Request original = prepareGet().setUri(URI.create("http://example.com")).build();

        Request filtered = filter.filterRequest(original);

        assertNotSame(filter, original);
        assertEquals(filtered.getUri(), original.getUri());
        assertEquals(original.getHeaders().size(), 0);
        assertEquals(filtered.getHeaders().size(), 1);
        assertEquals(filtered.getHeaders().get(TRACETOKEN_HEADER), List.of("{\"id\":\"testBasic\",\"key-b\":\"value-b\",\"key-a\":\"value-a\",\"key-c\":\"value-c\"}"));
    }

    @Test
    public void testIgnoresLocalProperties()
    {
        registerRequestToken("testBasic");
        addTraceTokenProperties("key-b", "value-b", "_local-1", "value-1", "key-a", "value-a");
        TraceTokenRequestFilter filter = new TraceTokenRequestFilter();
        Request original = prepareGet().setUri(URI.create("http://example.com")).build();

        Request filtered = filter.filterRequest(original);

        assertNotSame(filter, original);
        assertEquals(filtered.getUri(), original.getUri());
        assertEquals(original.getHeaders().size(), 0);
        assertEquals(filtered.getHeaders().size(), 1);
        assertEquals(filtered.getHeaders().get(TRACETOKEN_HEADER), List.of("{\"id\":\"testBasic\",\"key-b\":\"value-b\",\"key-a\":\"value-a\"}"));
    }

    @Test
    public void testSameRequestReturnedWhenTraceTokenNotSet()
    {
        TraceTokenRequestFilter filter = new TraceTokenRequestFilter();
        clearRequestToken();
        Request original =  prepareGet().setUri(URI.create("http://example.com")).build();

        Request request = filter.filterRequest(original);

        assertSame(request, original);
    }
}
