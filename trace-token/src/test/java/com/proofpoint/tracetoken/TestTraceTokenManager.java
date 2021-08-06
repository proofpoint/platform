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
package com.proofpoint.tracetoken;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static com.proofpoint.tracetoken.TraceTokenManager.addTraceTokenProperties;
import static com.proofpoint.tracetoken.TraceTokenManager.clearRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.createAndRegisterNewRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerTraceToken;
import static java.lang.Thread.currentThread;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

@SuppressWarnings("deprecation")
public class TestTraceTokenManager
{
    private static final ThreadLocal<String> originalThreadName = new ThreadLocal<>();
    private static final TraceToken TESTING_TRACE_TOKEN = new TraceToken(ImmutableMap.of("id", "testing-id", "key-d", "value-d"));

    @BeforeMethod
    public void setup()
    {
        originalThreadName.set(currentThread().getName());
        currentThread().setName("testing thread name");
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup()
    {
        currentThread().setName(originalThreadName.get());
    }

    @Test
    public void testCreateToken()
    {
        String tokenId = createAndRegisterNewRequestToken();

        assertEquals(getCurrentRequestToken(), tokenId);
        assertEquals(getCurrentTraceToken().keySet(), List.of("id"));
        assertEquals(currentThread().getName(), "testing thread name " + tokenId);
    }

    @Test
    public void testCreateTokenWithProperties()
    {
        String tokenId = createAndRegisterNewRequestToken("key-b", "value-b", "key-a", "value-a", "key-c", "value-c");

        assertEquals(getCurrentRequestToken(), "{id=" + tokenId + ", key-b=value-b, key-a=value-a, key-c=value-c}");
        TraceToken token = getCurrentTraceToken();
        assertEquals(token, ImmutableMap.of("id", tokenId, "key-b", "value-b", "key-a", "value-a", "key-c", "value-c"));
        assertEquals(token.keySet(), List.of("id", "key-b", "key-a", "key-c"));
        assertEquals(currentThread().getName(), "testing thread name {id=" + tokenId + ", key-b=value-b, key-a=value-a, key-c=value-c}");
    }

    @Test
    public void testRegisterCustomRequestToken()
    {
        registerRequestToken("abc");

        assertEquals(getCurrentRequestToken(), "abc");
        assertEquals(getCurrentTraceToken(), ImmutableMap.of("id", "abc"));
        assertEquals(currentThread().getName(), "testing thread name abc");
    }

    @Test
    public void testRegisterCustomTraceToken()
    {
        registerTraceToken(TESTING_TRACE_TOKEN);

        assertEquals(getCurrentRequestToken(), "{id=testing-id, key-d=value-d}");
        assertEquals(getCurrentTraceToken(), TESTING_TRACE_TOKEN);
        assertEquals(getCurrentTraceToken().keySet(), List.of("id", "key-d"));
        assertEquals(currentThread().getName(), "testing thread name {id=testing-id, key-d=value-d}");
    }

    @Test
    public void testOverrideRequestToken()
    {
        createAndRegisterNewRequestToken("somekey", "somevalue");

        registerRequestToken("abc");
        assertEquals(getCurrentRequestToken(), "abc");
        assertEquals(getCurrentTraceToken(), ImmutableMap.of("id", "abc"));
        assertEquals(currentThread().getName(), "testing thread name abc");
    }

    @Test
    public void testOverrideTraceToken()
    {
        createAndRegisterNewRequestToken("somekey", "somevalue");

        registerTraceToken(TESTING_TRACE_TOKEN);
        assertEquals(getCurrentRequestToken(), "{id=testing-id, key-d=value-d}");
        assertEquals(getCurrentTraceToken(), TESTING_TRACE_TOKEN);
        assertEquals(currentThread().getName(), "testing thread name {id=testing-id, key-d=value-d}");
    }

    @Test
    public void testClearRequestToken()
    {
        createAndRegisterNewRequestToken("somekey", "somevalue");

        clearRequestToken();
        assertNull(getCurrentRequestToken());
        assertNull(getCurrentTraceToken());
        assertEquals(currentThread().getName(), "testing thread name");
    }

    @Test
    public void testRestoreNoRequestToken()
    {
        clearRequestToken();

        try (TraceTokenScope ignored = registerRequestToken("abc"))
        {
            assertEquals(getCurrentRequestToken(), "abc");
        }
        assertNull(getCurrentRequestToken());
        assertNull(getCurrentTraceToken());
        assertEquals(currentThread().getName(), "testing thread name");
    }

    @Test
    public void testRestoreNoTraceToken()
    {
        clearRequestToken();

        try (TraceTokenScope ignored = registerTraceToken(TESTING_TRACE_TOKEN))
        {
            assertEquals(getCurrentTraceToken(), TESTING_TRACE_TOKEN);
        }
        assertNull(getCurrentRequestToken());
        assertNull(getCurrentTraceToken());
        assertEquals(currentThread().getName(), "testing thread name");
    }

    @Test
    public void testRestoreOldRequestToken()
    {
        createAndRegisterNewRequestToken("somekey", "somevalue");
        TraceToken token = getCurrentTraceToken();

        try (TraceTokenScope ignored = registerRequestToken("abc"))
        {
            assertEquals(getCurrentRequestToken(), "abc");
        }
        assertEquals(getCurrentRequestToken(), token.toString());
        assertEquals(getCurrentTraceToken(), token);
        assertEquals(currentThread().getName(), "testing thread name " + token);
    }

    @Test
    public void testRestoreOldTraceToken()
    {
        createAndRegisterNewRequestToken("somekey", "somevalue");
        TraceToken token = getCurrentTraceToken();

        try (TraceTokenScope ignored = registerTraceToken(TESTING_TRACE_TOKEN))
        {
            assertEquals(getCurrentTraceToken(), TESTING_TRACE_TOKEN);
        }
        assertEquals(getCurrentRequestToken(), token.toString());
        assertEquals(getCurrentTraceToken(), token);
        assertEquals(currentThread().getName(), "testing thread name " + token);
    }

    @Test
    public void testRegisterNullRequestToken()
    {
        String token = createAndRegisterNewRequestToken();

        try (TraceTokenScope ignored = registerRequestToken(null))
        {
            assertNull(getCurrentRequestToken());
            assertEquals(currentThread().getName(), "testing thread name");
        }
        assertEquals(getCurrentRequestToken(), token);
        assertEquals(currentThread().getName(), "testing thread name " + token);
    }

    @Test
    public void testRegisterNullTraceToken()
    {
        String token = createAndRegisterNewRequestToken();

        try (TraceTokenScope ignored = registerRequestToken(null))
        {
            assertNull(getCurrentRequestToken());
            assertEquals(currentThread().getName(), "testing thread name");
        }
        assertEquals(getCurrentRequestToken(), token);
        assertEquals(currentThread().getName(), "testing thread name " + token);
    }

    @Test
    public void testAddTraceTokenProperties()
    {
        registerTraceToken(TESTING_TRACE_TOKEN);

        try (TraceTokenScope ignored = addTraceTokenProperties("key-f", "value-f", "key-a", "value-a"))
        {
            assertEquals(getCurrentRequestToken(), "{id=testing-id, key-d=value-d, key-f=value-f, key-a=value-a}");
            assertEquals(getCurrentTraceToken(), ImmutableMap.of("id", "testing-id", "key-d", "value-d", "key-f", "value-f", "key-a", "value-a"));
            assertEquals(getCurrentTraceToken().keySet(), List.of("id", "key-d", "key-f", "key-a"));
            assertEquals(currentThread().getName(), "testing thread name {id=testing-id, key-d=value-d, key-f=value-f, key-a=value-a}");
        }
        assertEquals(getCurrentRequestToken(), "{id=testing-id, key-d=value-d}");
        assertEquals(getCurrentTraceToken(), TESTING_TRACE_TOKEN);
        assertEquals(currentThread().getName(), "testing thread name {id=testing-id, key-d=value-d}");
    }

    @Test
    public void testAddLocalTraceTokenProperties()
    {
        registerTraceToken(TESTING_TRACE_TOKEN);

        try (TraceTokenScope ignored = addTraceTokenProperties("_key-f", "value-f", "_key-a", "value-a"))
        {
            assertEquals(getCurrentRequestToken(), "{id=testing-id, key-d=value-d, _key-f=value-f, _key-a=value-a}");
            assertEquals(getCurrentTraceToken(), ImmutableMap.of("id", "testing-id", "key-d", "value-d", "_key-f", "value-f", "_key-a", "value-a"));
            assertEquals(getCurrentTraceToken().keySet(), List.of("id", "key-d", "_key-f", "_key-a"));
            assertEquals(currentThread().getName(), "testing thread name {id=testing-id, key-d=value-d, _key-f=value-f, _key-a=value-a}");
        }
        assertEquals(getCurrentRequestToken(), "{id=testing-id, key-d=value-d}");
        assertEquals(getCurrentTraceToken(), TESTING_TRACE_TOKEN);
        assertEquals(currentThread().getName(), "testing thread name {id=testing-id, key-d=value-d}");
    }

    @Test
    public void testAddTraceTokenPropertiesNoTraceToken()
    {
        clearRequestToken();

        try (TraceTokenScope ignored = addTraceTokenProperties("key-f", "value-f", "key-a", "value-a"))
        {
            assertNull(getCurrentRequestToken());
            assertNull(getCurrentTraceToken());
            assertEquals(currentThread().getName(), "testing thread name");
        }
        assertNull(getCurrentRequestToken());
        assertNull(getCurrentTraceToken());
        assertEquals(currentThread().getName(), "testing thread name");
    }
}
