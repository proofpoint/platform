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
package com.proofpoint.platform.sample;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.inject.Inject;
import com.proofpoint.event.client.EventClient;
import com.proofpoint.reporting.Gauge;
import com.proofpoint.reporting.HealthCheck;
import org.weakref.jmx.Flatten;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class PersonStore
{
    private final ConcurrentMap<String, Person> persons;
    private final PersonStoreStats stats;

    @Inject
    public PersonStore(StoreConfig config, Ticker ticker)
    {
        requireNonNull(config, "config must not be null");

        Cache<String, Person> personCache = CacheBuilder.newBuilder()
                .expireAfterWrite(config.getTtl().toMillis(), TimeUnit.MILLISECONDS)
                .ticker(ticker)
                .build();
        persons = personCache.asMap();
        stats = new PersonStoreStats();
    }

    @Flatten
    public PersonStoreStats getStats()
    {
        return stats;
    }

    @Gauge
    private int getSize()
    {
        return persons.size();
    }

    @HealthCheck("Person store size")
    private String checkSize()
    {
        int size = persons.size();
        if (size < 2) {
            return "Not enough persons in store: " + size;
        }
        return null;
    }

    public Person get(String id)
    {
        requireNonNull(id, "id must not be null");

        Person person = persons.get(id);
        if (person != null) {
            stats.personFetched();
        }
        return person;
    }

    /**
     * @return true if the entry was created for the first time
     */
    public boolean put(String id, Person person)
    {
        requireNonNull(id, "id must not be null");
        requireNonNull(person, "person must not be null");

        boolean added = persons.put(id, person) == null;
        if (added) {
            stats.personAdded();
        }
        else {
            stats.personUpdated();
        }
        return added;
    }

    /**
     * @return true if the entry was removed
     */
    public boolean delete(String id)
    {
        requireNonNull(id, "id must not be null");

        Person removedPerson = persons.remove(id);
        if (removedPerson != null) {
            stats.personRemoved();
        }

        return removedPerson != null;
    }

    public Collection<StoreEntry> getAll()
    {
        Builder<StoreEntry> builder = ImmutableList.builder();
        for (Entry<String, Person> entry : persons.entrySet()) {
            builder.add(new AutoValue_PersonStore_StoreEntry(entry.getKey(), entry.getValue()));
        }
        return builder.build();
    }

    @AutoValue
    public abstract static class StoreEntry
    {
        public abstract String getId();

        public abstract Person getPerson();
    }
}
