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

import com.google.common.annotations.Beta;
import com.google.common.base.Ticker;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.proofpoint.http.client.balancing.HttpServiceBalancerStats.Status;
import com.proofpoint.stats.MaxGauge;
import com.proofpoint.units.Duration;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.weakref.jmx.Nested;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class HttpServiceBalancerImpl
        implements HttpServiceBalancer
{
    private static final InstanceState INSTANCE_STATE_WORST = new InstanceState(Liveness.DEAD, Integer.MAX_VALUE);
    private static final Duration ZERO_DURATION = new Duration(0, SECONDS);
    private final AtomicReference<ImmutableMultiset<URI>> httpUris = new AtomicReference<>(ImmutableMultiset.of());

    @GuardedBy("uriStates")
    private final Map<URI, InstanceState> uriStates = new HashMap<>();
    private final String description;
    private final HttpServiceBalancerStats httpServiceBalancerStats;
    private final int consecutiveFailures;
    private final BackoffPolicy backoffPolicy;
    private final Ticker ticker;
    private final MaxGauge concurrency = new MaxGauge();

    public HttpServiceBalancerImpl(String description, HttpServiceBalancerStats httpServiceBalancerStats, HttpServiceBalancerConfig config)
    {
        this(description, httpServiceBalancerStats, config, Ticker.systemTicker());
    }

    HttpServiceBalancerImpl(String description, HttpServiceBalancerStats httpServiceBalancerStats, HttpServiceBalancerConfig config, Ticker ticker)
    {
        this.description = requireNonNull(description, "description is null");
        this.httpServiceBalancerStats = requireNonNull(httpServiceBalancerStats, "httpServiceBalancerStats is null");
        consecutiveFailures = requireNonNull(config, "config is null").getConsecutiveFailures();
        backoffPolicy = new DecorrelatedJitteredBackoffPolicy(config.getMinBackoff(), config.getMaxBackoff());
        this.ticker = requireNonNull(ticker, "ticker is null");
    }

    // Protect against finalizer attacks, as constructor can throw exception.
    @SuppressWarnings("deprecation")
    @Override
    protected final void finalize()
    {
    }

    @Override
    public HttpServiceAttempt createAttempt()
    {
        return new HttpServiceAttemptImpl(Set.of());
    }

    @Beta
    public void updateHttpUris(Collection<URI> newHttpUris)
    {
        httpUris.set(ImmutableMultiset.copyOf(newHttpUris));
    }

    private class HttpServiceAttemptImpl
            implements HttpServiceAttempt
    {
        private final Set<URI> attempted;
        private final URI uri;
        private final long startTick;
        private boolean inProgress = true;

        HttpServiceAttemptImpl(Set<URI> attempted)
        {
            Set<URI> attemptedCopy = attempted;
            Multiset<URI> httpUris = HttpServiceBalancerImpl.this.httpUris.get().stream()
                    .filter(uri -> !attemptedCopy.contains(uri))
                    .collect(Collectors.toCollection(HashMultiset::create));

            if (httpUris.isEmpty()) {
                httpUris = HttpServiceBalancerImpl.this.httpUris.get();
                attempted = Set.of();

                if (httpUris.isEmpty()) {
                    throw new ServiceUnavailableException(description);
                }
            }

            InstanceState bestState = INSTANCE_STATE_WORST;
            List<URI> leastUris = new ArrayList<>();
            synchronized (uriStates) {
                long now = ticker.read();
                for (;;) {
                    for (Entry<URI> uriEntry : httpUris.entrySet()) {
                        URI uri = uriEntry.getElement();
                        InstanceState uriState = uriStates.computeIfAbsent(uri, k -> new InstanceState(Liveness.ALIVE, 0));
                        if (uriState.weight != uriEntry.getCount()) {
                            uriState.weight = uriEntry.getCount();
                        }
                        if (uriState.liveness == Liveness.DEAD && uriState.deadUntil <= now) {
                            uriState.liveness = Liveness.PROBING;
                        }
                        int comparison = uriState.compareTo(bestState);
                        if (comparison <= 0) {
                            if (comparison < 0) {
                                bestState = uriState;
                                leastUris = new ArrayList<>();
                            }
                            for (int i = uriState.weight - (uriState.concurrency % uriState.weight); i > 0; i--) {
                                leastUris.add(uri);
                            }
                        }
                    }

                    if (bestState.liveness != Liveness.DEAD || attempted.isEmpty()) {
                        break;
                    }

                    httpUris = HttpServiceBalancerImpl.this.httpUris.get();
                    attempted = Set.of();
                }

                uri = leastUris.get(ThreadLocalRandom.current().nextInt(0, leastUris.size()));

                InstanceState uriState = uriStates.get(uri);
                if (uriState.liveness == Liveness.PROBING && uriState.concurrency == 0) {
                    httpServiceBalancerStats.probe(uri).add(1);
                }

                if (uriState.concurrency++ == concurrency.get()) {
                    concurrency.update(uriState.concurrency);
                }
            }

            this.attempted = Set.copyOf(attempted);
            startTick = ticker.read();
        }

        @Override
        public URI getUri()
        {
            return uri;
        }

        @Override
        public void markGood()
        {
            decrementConcurrency(false);
            httpServiceBalancerStats.requestTime(uri, Status.SUCCESS).add(ticker.read() - startTick, TimeUnit.NANOSECONDS);
        }

        @Override
        public void markBad(String failureCategory)
        {
            decrementConcurrency(true);
            httpServiceBalancerStats.requestTime(uri, Status.FAILURE).add(ticker.read() - startTick, TimeUnit.NANOSECONDS);
            httpServiceBalancerStats.failure(uri, failureCategory).add(1);
        }

        @Override
        public void markBad(String failureCategory, String handlerCategory)
        {
            decrementConcurrency(true);
            httpServiceBalancerStats.requestTime(uri, Status.FAILURE).add(ticker.read() - startTick, TimeUnit.NANOSECONDS);
            httpServiceBalancerStats.failure(uri, failureCategory, handlerCategory).add(1);
        }

        private void decrementConcurrency(boolean isFailure)
        {
            checkState(inProgress, "is in progress");
            inProgress = false;
            synchronized (uriStates) {
                InstanceState uriState = uriStates.get(uri);

                uriState.liveness.mark(isFailure, uriState, this, HttpServiceBalancerImpl.this);
                int oldConcurrency = uriState.concurrency;
                if (oldConcurrency > 0) {
                    --uriState.concurrency;
                }

                if (oldConcurrency == 1 && !isFailure && uriState.liveness == Liveness.ALIVE) {
                    uriStates.remove(uri);
                    if (uriStates.isEmpty()) {
                        concurrency.update(0);
                        return;
                    }
                }
                if (concurrency.get() == oldConcurrency) {
                    for (InstanceState instanceState : uriStates.values()) {
                        if (oldConcurrency == instanceState.concurrency) {
                            return;
                        }
                    }
                    concurrency.update(oldConcurrency - 1);
                }
            }
        }

        @Override
        public HttpServiceAttempt next()
        {
            checkState(!inProgress, "is not still in progress");
            Set<URI> newAttempted = ImmutableSet.<URI>builder()
                    .add(uri)
                    .addAll(attempted)
                    .build();
            return new HttpServiceAttemptImpl(newAttempted);
        }
    }

    @Nested
    public MaxGauge getConcurrency()
    {
        return concurrency;
    }

    @SuppressFBWarnings(value = "EQ_COMPARETO_USE_OBJECT_EQUALS", justification = "Object does not implement Comparable")
    private static class InstanceState
    {
        Liveness liveness;
        int weight = 1;
        int concurrency;
        int numFailures = 0;
        BackoffPolicy backoffPolicy;
        Duration lastBackoff;
        long deadUntil;

        InstanceState(Liveness liveness, int concurrency)
        {
            this.liveness = liveness;
            this.concurrency = concurrency;
        }

        int compareTo(InstanceState that)
        {
            if (liveness == Liveness.DEAD || (liveness == Liveness.PROBING && concurrency > 0)) {
                if (that.liveness == Liveness.DEAD || (that.liveness == Liveness.PROBING && that.concurrency > 0)) {
                    return Integer.compare(concurrency / weight, that.concurrency / that.weight);
                }
                return 1;
            }
            if (that.liveness == Liveness.DEAD || (that.liveness == Liveness.PROBING && that.concurrency > 0)) {
                return -1;
            }
            return Integer.compare(concurrency / weight, that.concurrency / that.weight);
        }
    }

    private enum Liveness
    {
        ALIVE {
            @Override
            public void mark(boolean isFailure, InstanceState uriState, HttpServiceAttemptImpl attempt, HttpServiceBalancerImpl balancer)
            {
                if (isFailure) {
                    if (++uriState.numFailures >= balancer.consecutiveFailures) {
                        uriState.liveness = DEAD;
                        uriState.backoffPolicy = balancer.backoffPolicy;
                        uriState.lastBackoff = uriState.backoffPolicy.backoff(ZERO_DURATION);
                        uriState.deadUntil = balancer.ticker.read() + uriState.lastBackoff.roundTo(NANOSECONDS);
                        balancer.httpServiceBalancerStats.removal(attempt.uri).add(uriState.lastBackoff);
                    }
                }
                else {
                    uriState.numFailures = 0;
                }
            }
        },

        DEAD {
            @Override
            public void mark(boolean isFailure, InstanceState uriState, HttpServiceAttemptImpl attempt, HttpServiceBalancerImpl balancer)
            {
                if (!isFailure) {
                    uriState.liveness = ALIVE;
                    uriState.numFailures = 0;
                    uriState.backoffPolicy = null;
                    uriState.lastBackoff = null;
                    balancer.httpServiceBalancerStats.revival(attempt.uri).add(1);
                }
            }
        },

        PROBING {
            @Override
            public void mark(boolean isFailure, InstanceState uriState, HttpServiceAttemptImpl attempt, HttpServiceBalancerImpl balancer)
            {
                if (isFailure) {
                    uriState.liveness = DEAD;
                    uriState.backoffPolicy = uriState.backoffPolicy.nextAttempt();
                    uriState.lastBackoff = uriState.backoffPolicy.backoff(uriState.lastBackoff);
                    uriState.deadUntil = balancer.ticker.read() + uriState.lastBackoff.roundTo(NANOSECONDS);
                    balancer.httpServiceBalancerStats.removal(attempt.uri).add(uriState.lastBackoff);
                }
                else {
                    uriState.liveness = ALIVE;
                    uriState.numFailures = 0;
                    uriState.backoffPolicy = null;
                    uriState.lastBackoff = null;
                    balancer.httpServiceBalancerStats.revival(attempt.uri).add(1);
                }
            }
        };

        public abstract void mark(boolean isFailure, InstanceState uriState, HttpServiceAttemptImpl attempt, HttpServiceBalancerImpl balancer);
    }
}
