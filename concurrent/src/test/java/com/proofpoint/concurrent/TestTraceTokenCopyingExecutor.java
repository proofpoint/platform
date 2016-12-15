/*
 * Copyright 2016 Proofpoint, Inc.
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
package com.proofpoint.concurrent;

import com.proofpoint.tracetoken.TraceToken;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static com.proofpoint.concurrent.TraceTokenCopyingExecutor.traceTokenCopyingExecutor;
import static com.proofpoint.tracetoken.TraceTokenManager.createAndRegisterNewRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestTraceTokenCopyingExecutor
{
    @Test
    public void testCallableTraceTokenCopied()
            throws InterruptedException, ExecutionException
    {
        AtomicReference<TraceToken> actualToken = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService innerExecutor = newSingleThreadExecutor();
        Object expectedReturn = new Object();

        try {
            createAndRegisterNewRequestToken("somekey", "somevalue");
            TraceToken token = getCurrentTraceToken();
            Future<Object> future = traceTokenCopyingExecutor(innerExecutor).submit(() -> {
                actualToken.set(getCurrentTraceToken());
                latch.countDown();
                return expectedReturn;
            });
            assertTrue(latch.await(10, SECONDS));
            assertEquals(actualToken.get(), token);
            assertEquals(future.get(), expectedReturn);
        }
        finally {
            innerExecutor.shutdown();
        }
    }

    @Test
    public void testRunnableTraceTokenCopied()
            throws InterruptedException, ExecutionException
    {
        AtomicReference<TraceToken> actualToken = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService innerExecutor = newSingleThreadExecutor();

        try {
            createAndRegisterNewRequestToken("somekey", "somevalue");
            TraceToken token = getCurrentTraceToken();
            traceTokenCopyingExecutor(innerExecutor).execute(() -> {
                actualToken.set(getCurrentTraceToken());
                latch.countDown();
            });
            assertTrue(latch.await(10, SECONDS));
            assertEquals(actualToken.get(), token);
        }
        finally {
            innerExecutor.shutdown();
        }
    }
}
