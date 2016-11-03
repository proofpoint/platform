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

import com.proofpoint.tracetoken.TraceTokenScope;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerRequestToken;

public class TraceTokenCopyingExecutor
    extends WrappingExecutorService
{
    private TraceTokenCopyingExecutor(ExecutorService executor)
    {
        super(executor);
    }

    public static ExecutorService traceTokenCopyingExecutor(ExecutorService executor) {
        return new TraceTokenCopyingExecutor(executor);
    }

    @Override
    protected <T> Callable<T> wrapTask(Callable<T> callable)
    {
        String token = getCurrentRequestToken();
        return () -> {
            try (TraceTokenScope ignored = registerRequestToken(token)) {
                return callable.call();
            }
        };
    }

    @Override
    protected Runnable wrapTask(Runnable command)
    {
        String token = getCurrentRequestToken();
        return () -> {
            try (TraceTokenScope ignored = registerRequestToken(token)) {
                command.run();
            }
        };
    }
}
