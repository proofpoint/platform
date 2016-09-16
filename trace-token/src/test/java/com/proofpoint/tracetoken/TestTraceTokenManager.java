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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.proofpoint.tracetoken.TraceTokenManager.clearRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.createAndRegisterNewRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerRequestToken;
import static java.lang.Thread.currentThread;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestTraceTokenManager
{
    private static final ThreadLocal<String> originalThreadName = new ThreadLocal<>();

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
        String token = createAndRegisterNewRequestToken();

        assertEquals(getCurrentRequestToken(), token);
        assertEquals(currentThread().getName(), "testing thread name " + token);
    }

    @Test
    public void testRegisterCustomToken()
    {
        registerRequestToken("abc");

        assertEquals(getCurrentRequestToken(), "abc");
        assertEquals(currentThread().getName(), "testing thread name abc");
    }

    @Test
    public void testOverrideRequestToken()
    {
        String oldToken = createAndRegisterNewRequestToken();

        assertEquals(getCurrentRequestToken(), oldToken);

        registerRequestToken("abc");
        assertEquals(getCurrentRequestToken(), "abc");
        assertEquals(currentThread().getName(), "testing thread name abc");
    }

    @Test
    public void testClearRequestToken()
    {
        String oldToken = createAndRegisterNewRequestToken();

        assertEquals(getCurrentRequestToken(), oldToken);

        clearRequestToken();
        assertNull(getCurrentRequestToken());
        assertEquals(currentThread().getName(), "testing thread name");
    }

    @Test
    public void testRestoreNoToken()
    {
        clearRequestToken();

        try (TraceTokenScope ignored = registerRequestToken("abc"))
        {
            assertEquals(getCurrentRequestToken(), "abc");
        }
        assertNull(getCurrentRequestToken());
        assertEquals(currentThread().getName(), "testing thread name");
    }

    @Test
    public void testRestoreOldToken()
    {
        String token = createAndRegisterNewRequestToken();

        try (TraceTokenScope ignored = registerRequestToken("abc"))
        {
            assertEquals(getCurrentRequestToken(), "abc");
        }
        assertEquals(getCurrentRequestToken(), token);
        assertEquals(currentThread().getName(), "testing thread name " + token);
    }

    @Test
    public void testRegisterNullToken()
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
}
