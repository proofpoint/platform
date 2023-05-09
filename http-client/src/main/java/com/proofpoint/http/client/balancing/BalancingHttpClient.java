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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.tracetoken.TraceToken;
import com.proofpoint.tracetoken.TraceTokenScope;
import com.proofpoint.units.Duration;
import jakarta.inject.Inject;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.cache.CacheBuilder.newBuilder;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerTraceToken;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class BalancingHttpClient
        implements HttpClient
{
    private static final Duration ZERO_DURATION = new Duration(0, TimeUnit.MILLISECONDS);

    private final HttpServiceBalancer pool;
    private final HttpClient httpClient;
    private final int maxAttempts;
    private final RetryBudget retryBudget;
    private final BackoffPolicy backoffPolicy;
    private final ScheduledExecutorService retryExecutor;
    private final Cache<Class<? extends Exception>, Boolean> exceptionCache = newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();

    @Inject
    public BalancingHttpClient(@ForBalancingHttpClient HttpServiceBalancer pool,
            @ForBalancingHttpClient HttpClient httpClient,
            BalancingHttpClientConfig config,
            @ForBalancingHttpClient ScheduledExecutorService retryExecutor)
    {
        this(pool, httpClient, config, retryExecutor, Ticker.systemTicker());
    }

    @VisibleForTesting
    BalancingHttpClient(@ForBalancingHttpClient HttpServiceBalancer pool,
            @ForBalancingHttpClient HttpClient httpClient,
            BalancingHttpClientConfig config,
            @ForBalancingHttpClient ScheduledExecutorService retryExecutor,
            Ticker ticker)
    {
        this.pool = requireNonNull(pool, "pool is null");
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
        maxAttempts = requireNonNull(config, "config is null").getMaxAttempts();
        retryBudget = TokenRetryBudget.tokenRetryBudget(config.getRetryBudgetRatio(), config.getRetryBudgetRatioPeriod(), config.getRetryBudgetMinPerSecond(), ticker);
        backoffPolicy = new DecorrelatedJitteredBackoffPolicy(config.getMinBackoff(), config.getMaxBackoff());
        this.retryExecutor = requireNonNull(retryExecutor, "retryExecutor is null");
    }

    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        checkArgument(!request.getUri().isAbsolute(), request.getUri() + " is not a relative URI");
        checkArgument(request.getUri().getHost() == null, request.getUri() + " has a host component");
        String path = request.getUri().getPath();
        checkArgument(path == null || !path.startsWith("/"), request.getUri() + " path starts with '/'");

        HttpServiceAttempt attempt;
        try {
            attempt = pool.createAttempt();
        }
        catch (RuntimeException e) {
            return responseHandler.handleException(request, e);
        }
        int attemptsLeft = maxAttempts;
        retryBudget.initialAttempt();
        BackoffPolicy attemptBackoffPolicy = backoffPolicy;
        Duration previousBackoff = ZERO_DURATION;

        RetryingResponseHandler<T, E> retryingResponseHandler = new RetryingResponseHandler<>(responseHandler, retryBudget, exceptionCache);

        for (;;) {
            URI uri = attempt.getUri();
            if (!uri.toString().endsWith("/")) {
                uri = URI.create(uri.toString() + '/');
            }
            uri = uri.resolve(request.getUri());

            Request subRequest = Request.Builder.fromRequest(request)
                    .setUri(uri)
                    .build();

            if (attemptsLeft <= 1) {
                retryingResponseHandler = new RetryingResponseHandler<>(responseHandler, NoRetryBudget.INSTANCE, exceptionCache);
            }

            --attemptsLeft;
            try {
                T t = httpClient.execute(subRequest, retryingResponseHandler);
                attempt.markGood();
                return t;
            }
            catch (InnerHandlerException e) {
                attempt.markBad(e.getFailureCategory(), e.getHandlerCategory());
                //noinspection unchecked
                throw (E) e.getCause();
            }
            catch (FailureStatusException e) {
                attempt.markBad(e.getFailureCategory());
                //noinspection unchecked
                return (T) e.result;
            }
            catch (RetryException e) {
                attempt.markBad(e.getFailureCategory());
                Duration backoff = attemptBackoffPolicy.backoff(previousBackoff);
                long millis = backoff.roundTo(MILLISECONDS);
                try {
                    Thread.sleep(millis);
                }
                catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                    return responseHandler.handleException(request, e1);
                }
                try {
                    attempt = attempt.next();
                    previousBackoff = backoff;
                    attemptBackoffPolicy = attemptBackoffPolicy.nextAttempt();
                }
                catch (RuntimeException e1) {
                    return responseHandler.handleException(request, e1);
                }
            }
        }
    }

    @Override
    public <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
    {
        checkArgument(!request.getUri().isAbsolute(), request.getUri() + " is not a relative URI");
        checkArgument(request.getUri().getHost() == null, request.getUri() + " has a host component");
        String path = request.getUri().getPath();
        checkArgument(path == null || !path.startsWith("/"), request.getUri() + " path starts with '/'");

        HttpServiceAttempt attempt;
        try {
            attempt = pool.createAttempt();
        }
        catch (RuntimeException e) {
            try {
                return new ImmediateHttpResponseFuture<>(responseHandler.handleException(request, e));
            }
            catch (Exception e1) {
                return new ImmediateFailedHttpResponseFuture<>((E) e1);
            }
        }
        retryBudget.initialAttempt();
        RetryFuture<T, E> retryFuture = new RetryFuture<>(request, responseHandler);
        attemptQuery(retryFuture, request, responseHandler, attempt, maxAttempts);
        return retryFuture;
    }

    private <T, E extends Exception> void attemptQuery(RetryFuture<T, E> retryFuture, Request request, ResponseHandler<T, E> responseHandler, HttpServiceAttempt attempt, int attemptsLeft)
    {
        RetryingResponseHandler<T, E> retryingResponseHandler = new RetryingResponseHandler<>(
                responseHandler,
                (attemptsLeft <= 1) ? NoRetryBudget.INSTANCE :  retryBudget,
                exceptionCache
        );

        URI uri = attempt.getUri();
        if (!uri.toString().endsWith("/")) {
            uri = URI.create(uri.toString() + '/');
        }
        uri = uri.resolve(request.getUri());

        Request subRequest = Request.Builder.fromRequest(request)
                .setUri(uri)
                .build();

        --attemptsLeft;
        HttpResponseFuture<T> future = httpClient.executeAsync(subRequest, retryingResponseHandler);
        retryFuture.newAttempt(future, attempt, uri, attemptsLeft);
    }

    @Flatten
    @Override
    public RequestStats getStats()
    {
        return httpClient.getStats();
    }

    @Flatten
    RetryBudget getRetryBudget() {
        return retryBudget;
    }

    @Managed
    public String dump()
    {
        if (httpClient instanceof JettyHttpClient) {
            return ((JettyHttpClient) httpClient).dump();
        }
        return null;
    }

    @Managed
    public void dumpStdErr()
    {
        if (httpClient instanceof JettyHttpClient) {
            ((JettyHttpClient) httpClient).dumpStdErr();
        }
    }

    @Override
    public void close()
    {
        retryExecutor.shutdown();
        retryExecutor.shutdownNow();
        httpClient.close();
    }

    @Override
    public boolean isClosed()
    {
        return httpClient.isClosed();
    }

    private class RetryFuture<T, E extends Exception>
            extends AbstractFuture<T>
            implements HttpResponseFuture<T>
    {

        private final Request request;
        private final ResponseHandler<T,E> responseHandler;
        private final Object subFutureLock = new Object();
        @GuardedBy("subFutureLock")
        private HttpServiceAttempt attempt = null;
        @GuardedBy("subFutureLock")
        private BackoffPolicy attemptBackoffPolicy = backoffPolicy;
        @GuardedBy("subFutureLock")
        private Duration previousBackoff = ZERO_DURATION;
        @GuardedBy("subFutureLock")
        private URI uri = null;
        @GuardedBy("subFutureLock")
        private HttpResponseFuture<T> subFuture = null;

        RetryFuture(Request request, ResponseHandler<T, E> responseHandler)
        {
            this.request = request;
            this.responseHandler = responseHandler;
        }

        void newAttempt(final HttpResponseFuture<T> future, final HttpServiceAttempt attempt, URI uri, final int attemptsLeft)
        {
            synchronized (subFutureLock) {
                this.attempt = attempt;
                this.subFuture = future;
                this.uri = uri;
            }
            final RetryFuture<T, E> retryFuture = this;
            final Request request = this.request;
            final ResponseHandler<T, E> responseHandler = this.responseHandler;
            Futures.addCallback(future, new FutureCallback<T>()
            {
                @Override
                public void onSuccess(T result)
                {
                    attempt.markGood();
                    set(result);
                }

                @Override
                public void onFailure(Throwable t)
                {
                    if (t instanceof InnerHandlerException) {
                        InnerHandlerException innerHandlerException = (InnerHandlerException) t;
                        attempt.markBad(innerHandlerException.getFailureCategory(), innerHandlerException.getHandlerCategory());
                        setException(t.getCause());
                    }
                    else if (t instanceof FailureStatusException) {
                        attempt.markBad(((FailureStatusException) t).getFailureCategory());
                        //noinspection unchecked
                        set((T) ((FailureStatusException) t).result);
                    }
                    else if (t instanceof RetryException) {
                        attempt.markBad(((RetryException) t).getFailureCategory());
                        TraceToken traceToken = getCurrentTraceToken();
                        synchronized (subFutureLock) {
                            Duration backoff = attemptBackoffPolicy.backoff(previousBackoff);
                            ScheduledFuture<?> scheduledFuture = retryExecutor.schedule(() -> {
                                try (TraceTokenScope scope = registerTraceToken(traceToken)){
                                    synchronized (subFutureLock) {
                                        HttpServiceAttempt nextAttempt;
                                        try {
                                            nextAttempt = attempt.next();
                                            previousBackoff = backoff;
                                            attemptBackoffPolicy = attemptBackoffPolicy.nextAttempt();
                                        }
                                        catch (RuntimeException e1) {
                                            try {
                                                set(responseHandler.handleException(request, e1));
                                            }
                                            catch (Exception e2) {
                                                setException(e2);
                                            }
                                            return;
                                        }
                                        try {
                                            attemptQuery(retryFuture, request, responseHandler, nextAttempt, attemptsLeft);
                                        }
                                        catch (RuntimeException e1) {
                                            setException(e1);
                                        }
                                    }
                                }
                            }, backoff.roundTo(MILLISECONDS), MILLISECONDS);
                            subFuture = new RetryDelayFuture<>(scheduledFuture, attempt);
                        }
                    }
                }
            }, directExecutor());
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {

            if (super.cancel(mayInterruptIfRunning)) {
                // todo attempt.cancel() ?
                synchronized (subFutureLock) {
                    subFuture.cancel(mayInterruptIfRunning);
                }
                return true;
            }
            return false;
        }

        @Override
        public String getState()
        {
            synchronized (subFutureLock) {
                return format("Attempt %s to %s: %s", attempt, uri, subFuture.getState());
            }
        }
    }

    private static class ImmediateHttpResponseFuture<T>
            extends AbstractFuture<T>
            implements HttpResponseFuture<T>
    {
        private final T result;

        ImmediateHttpResponseFuture(T result)
        {
            this.result = result;
            set(result);
        }

        @Override
        public String getState()
        {
            return "Succeeded with result " + result;
        }
    }

    private static class ImmediateFailedHttpResponseFuture<T, E extends Exception>
            extends AbstractFuture<T>
            implements HttpResponseFuture<T>
    {
        private final E exception;

        ImmediateFailedHttpResponseFuture(E exception)
        {
            this.exception = exception;
            setException(exception);
        }

        @Override
        public String getState()
        {
            return "Failed with exception " + exception;
        }
    }

    private static class RetryDelayFuture<T>
            extends AbstractFuture<T>
            implements HttpResponseFuture<T>
    {
        private final ScheduledFuture<?> scheduledFuture;
        private final HttpServiceAttempt attempt;

        public RetryDelayFuture(ScheduledFuture<?> scheduledFuture, HttpServiceAttempt attempt)
        {
            this.scheduledFuture = requireNonNull(scheduledFuture, "scheduledFuture is null");
            this.attempt = requireNonNull(attempt, "attempt is null");
        }

        @Override
        public String getState()
        {
            return format("Delaying for retry after attempt %s", attempt);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            return scheduledFuture.cancel(mayInterruptIfRunning);
        }
    }
}
