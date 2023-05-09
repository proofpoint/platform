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
package com.proofpoint.jaxrs;

import com.google.common.base.Ticker;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.proofpoint.stats.SparseTimeStat;
import com.proofpoint.units.Duration;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

@Priority(100)
class TimingFilter
        implements ContainerRequestFilter, ContainerResponseFilter
{
    static final String TAGS_KEY = TimingFilter.class.getName() + ".tags";

    private static final String START_TIME_KEY = TimingFilter.class.getName() + ".start-time";
    private final String methodName;
    private final LoadingCache<List<Optional<String>>, SparseTimeStat> loadingCache;
    private final Ticker ticker;

    TimingFilter(String methodName, LoadingCache<List<Optional<String>>, SparseTimeStat> loadingCache, Ticker ticker)
    {
        this.methodName = requireNonNull(methodName, "methodName is null");
        this.loadingCache = requireNonNull(loadingCache, "loadingCache is null");
        this.ticker = requireNonNull(ticker, "ticker is null");
    }

    @Override
    public void filter(ContainerRequestContext request)
    {
        request.setProperty(START_TIME_KEY, ticker.read());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response)
    {
        Long startTime = (Long) request.getProperty(START_TIME_KEY);

        ImmutableList.Builder<Optional<String>> builder = ImmutableList.builder();
        builder.add(Optional.of(methodName), Optional.of(Integer.toString(response.getStatus())), Optional.of(Integer.toString(response.getStatus()/100)));

        Collection<Optional<Object>> tags = (Collection<Optional<Object>>) request.getProperty(TAGS_KEY);
        if (tags != null) {
            for (Optional<Object> tag : tags) {
                builder.add(tag.map(Object::toString));
            }
        }

        loadingCache.getUnchecked(builder.build())
                .add(Duration.succinctNanos(ticker.read() - startTime));
    }
}
