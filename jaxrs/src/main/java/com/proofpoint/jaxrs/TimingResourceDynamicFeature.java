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
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.proofpoint.reporting.ReportExporter;
import com.proofpoint.stats.SparseTimeStat;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.ext.Provider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

@Provider
class TimingResourceDynamicFeature
        implements DynamicFeature
{
    private final Set<Class<?>> applicationPrefixedClasses;
    private final ReportExporter reportExporter;
    private final Ticker ticker;

    @Inject
    public TimingResourceDynamicFeature(ReportExporter reportExporter, @JaxrsApplicationPrefixed Set<Class<?>> applicationPrefixedClasses, @JaxrsTicker Ticker ticker)
    {
        this.reportExporter = requireNonNull(reportExporter, "reportExporter is null");
        this.applicationPrefixedClasses = requireNonNull(applicationPrefixedClasses, "applicationPrefixedClasses is null");
        this.ticker = requireNonNull(ticker, "ticker is null");
    }

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext featureContext)
    {
        Class<?> resourceClass = resourceInfo.getResourceClass();
        Method resourceMethod = resourceInfo.getResourceMethod();

        if (resourceClass == null || resourceMethod == null) {
            return;
        }

        if (!isJaxRsResource(resourceClass)) {
            return;
        }

        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add("method", "responseCode", "responseCodeFamily");

        if (TimingWrapped.class.isAssignableFrom(resourceClass)) {
            Map<String, List<String>> keyNamesMap;
            try {
                keyNamesMap = (Map<String, List<String>>) resourceClass.getDeclaredMethod("getKeyNames").invoke(null);
            }
            catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            List<String> keyNames = keyNamesMap.get(resourceMethod.getName());
            if (keyNames != null) {
                builder.addAll(keyNames);
            }

            try {
                resourceClass = resourceClass.getDeclaredField("delegate").getType();
            }
            catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        LoadingCache<List<Optional<String>>, SparseTimeStat> loadingCache = new CacheImplementation(
                applicationPrefixedClasses.contains(resourceClass),
                resourceClass.getSimpleName() + ".RequestTime",
                builder.build()
        ).getLoadingCache();

        featureContext.register(new TimingFilter(resourceMethod.getName(), loadingCache, ticker));
    }

    private static boolean isJaxRsResource(Class<?> type)
    {
        if (type == null) {
            return false;
        }

        else if (type.isAnnotationPresent(Path.class)) {
            return true;
        }
        if (isJaxRsResource(type.getSuperclass())) {
            return true;
        }
        for (Class<?> typeInterface : type.getInterfaces()) {
            if (isJaxRsResource(typeInterface)) {
                return true;
            }
        }

        return false;
    }

    private class CacheImplementation
    {
        private final LoadingCache<List<Optional<String>>, SparseTimeStat> loadingCache;
        @GuardedBy("registeredMap")
        private final Map<List<Optional<String>>, SparseTimeStat> registeredMap = new HashMap<>();
        @GuardedBy("registeredMap")
        private final Set<SparseTimeStat> reinsertedSet = new HashSet<>();

        CacheImplementation(boolean applicationPrefix, String namePrefix, List<String> keyNames)
        {
            loadingCache = CacheBuilder.newBuilder()
                    .ticker(ticker)
                    .expireAfterAccess(15, TimeUnit.MINUTES)
                    .removalListener(new UnexportRemovalListener())
                    .build(new CacheLoader<List<Optional<String>>, SparseTimeStat>()
                    {
                        @Override
                        public SparseTimeStat load(List<Optional<String>> key)
                        {
                            SparseTimeStat returnValue = new SparseTimeStat();

                            synchronized (registeredMap) {
                                SparseTimeStat existingStat = registeredMap.get(key);
                                if (existingStat != null) {
                                    reinsertedSet.add(existingStat);
                                    return existingStat;
                                }
                                registeredMap.put(key, returnValue);
                                Builder<String, String> tagBuilder = ImmutableMap.builder();
                                for (int i = 0; i < keyNames.size(); ++i) {
                                    Optional<String> keyValue = key.get(i);
                                    if (keyValue.isPresent()) {
                                        tagBuilder.put(keyNames.get(i), keyValue.get());
                                    }
                                }
                                reportExporter.export(returnValue, applicationPrefix, namePrefix, tagBuilder.build());
                            }
                            return returnValue;
                        }
                    });
        }

        LoadingCache<List<Optional<String>>, SparseTimeStat> getLoadingCache()
        {
            return loadingCache;
        }

        private class UnexportRemovalListener implements RemovalListener<List<Optional<String>>, SparseTimeStat>
        {
            @Override
            public void onRemoval(RemovalNotification<List<Optional<String>>, SparseTimeStat> notification)
            {
                synchronized (registeredMap) {
                    if (reinsertedSet.remove(notification.getValue())) {
                        return;
                    }
                    reportExporter.unexportObject(notification.getValue());
                    registeredMap.remove(notification.getKey());
                }
            }
        }
    }
}
