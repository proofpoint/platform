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
package com.proofpoint.reporting;

import com.google.common.collect.ImmutableMap;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

class ReportCollectionProvider<T> implements Provider<T>
{
    private final Class<T> iface;
    private final AtomicBoolean provided = new AtomicBoolean();
    private boolean applicationPrefix = false;
    private String namePrefix;
    private Map<String, String> tags = ImmutableMap.of();
    private String legacyName = null;
    private ReportCollectionFactory reportCollectionFactory;

    ReportCollectionProvider(Class<T> iface)
    {
        this.iface = requireNonNull(iface, "iface is null");
        namePrefix = iface.getSimpleName();
    }

    @Inject
    public void setReportCollectionFactory(ReportCollectionFactory reportCollectionFactory)
    {
        this.reportCollectionFactory = reportCollectionFactory;
    }

    @Override
    @SuppressWarnings("deprecation")
    public T get()
    {
        provided.set(true);
        if (legacyName == null) {
            return reportCollectionFactory.createReportCollection(iface, applicationPrefix, namePrefix, tags);
        }
        return reportCollectionFactory.createReportCollection(iface, legacyName);
    }

    public void setApplicationPrefix(boolean applicationPrefix)
    {
        this.applicationPrefix = applicationPrefix;
    }

    void setNamePrefix(@Nullable String namePrefix)
    {
        this.namePrefix = namePrefix;
    }

    void setTags(Map<String, String> tags)
    {
        this.tags = ImmutableMap.copyOf(tags);
    }

    void setLegacyName(String legacyName)
    {
        checkState(!provided.get(), "report collection already provided");
        this.legacyName = requireNonNull(legacyName, "legacyName is null");
    }
}
