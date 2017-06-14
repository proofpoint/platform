/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.http.client.balancing;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableSet;
import com.proofpoint.http.client.BodySource;
import com.proofpoint.http.client.LimitedRetryable;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.log.Logger;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

final class RetryingResponseHandler<T, E extends Exception>
        implements ResponseHandler<T, RetryException>
{
    private static final Set<Integer> RETRYABLE_STATUS_CODES = ImmutableSet.of(408, 499, 500, 502, 503, 504, 598, 599);
    private static final Logger log = Logger.get(RetryingResponseHandler.class);
    private final ResponseHandler<T, E> innerHandler;
    private final RetryBudget retryBudget;
    private final Cache<Class<? extends Exception>, Boolean> exceptionCache;

    RetryingResponseHandler(ResponseHandler<T, E> innerHandler, RetryBudget retryBudget, Cache<Class<? extends Exception>, Boolean> exceptionCache)
    {
        this.innerHandler = innerHandler;
        this.retryBudget = retryBudget;
        this.exceptionCache = exceptionCache;
    }

    @Override
    public T handleException(Request request, final Exception exception)
            throws RetryException
    {
        final AtomicBoolean isLogged = new AtomicBoolean(false);
        try {
            exceptionCache.get(exception.getClass(), () -> {
                log.warn(exception, "Exception querying %s",
                        request.getUri().resolve("/"));
                isLogged.set(true);
                return true;
            });
        }
        catch (ExecutionException ignored) {
            // can't happen
        }
        if (!isLogged.get()) {
            log.warn("Exception querying %s: %s",
                    request.getUri().resolve("/"),
                    exception);
        }

        if (!bodySourceRetryable(request) || !retryBudget.canRetry()) {
            Object result;
            try {
                result = innerHandler.handleException(request, exception);
            }
            catch (Exception e) {
                throw new InnerHandlerException(e, exception);
            }
            throw new FailureStatusException(result, exception);
        }

        throw new RetryException(exception);
    }

    @Override
    public T handle(Request request, Response response)
            throws RetryException
    {
        String failureCategory = response.getStatusCode() + " status code";
        if (RETRYABLE_STATUS_CODES.contains(response.getStatusCode())) {
            String retryHeader = response.getHeader("X-Proofpoint-Retry");
            log.warn("%d response querying %s",
                    response.getStatusCode(), request.getUri().resolve("/"));
            if (!("no".equalsIgnoreCase(retryHeader)) && bodySourceRetryable(request) && retryBudget.canRetry()) {
                throw new RetryException(failureCategory);
            }

            Object result;
            try {
                result = innerHandler.handle(request, response);
            }
            catch (Exception e) {
                throw new InnerHandlerException(e, failureCategory);
            }
            throw new FailureStatusException(result, failureCategory);
        }

        try {
            return innerHandler.handle(request, response);
        }
        catch (Exception e) {
            throw new InnerHandlerException(e, failureCategory);
        }
    }

    private static boolean bodySourceRetryable(Request request)
    {
        BodySource bodySource = request.getBodySource();
        return !(bodySource instanceof LimitedRetryable) || ((LimitedRetryable) bodySource).isRetryable();
    }
}
