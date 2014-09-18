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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.proofpoint.event.client.InMemoryEventClient;
import com.proofpoint.platform.sample.PersonStore.StoreEntry;
import com.proofpoint.units.Duration;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.platform.sample.Person.createPerson;
import static com.proofpoint.platform.sample.PersonEvent.personAdded;
import static com.proofpoint.platform.sample.PersonEvent.personRemoved;
import static com.proofpoint.platform.sample.PersonEvent.personUpdated;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestPersonStore
{
    @Test
    public void testStartsEmpty()
    {
        PersonStore store = new PersonStore(new StoreConfig(), new InMemoryEventClient());
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    public void testTtl()
            throws InterruptedException
    {
        StoreConfig config = new StoreConfig();
        config.setTtl(new Duration(1, TimeUnit.MILLISECONDS));

        PersonStore store = new PersonStore(config, new InMemoryEventClient());
        store.put("foo", createPerson("foo@example.com", "Mr Foo"));
        Thread.sleep(2);
        Assert.assertNull(store.get("foo"));
    }

    @Test
    public void testPut()
    {
        InMemoryEventClient eventClient = new InMemoryEventClient();
        PersonStore store = new PersonStore(new StoreConfig(), eventClient);
        store.put("foo", createPerson("foo@example.com", "Mr Foo"));

        assertEquals(createPerson("foo@example.com", "Mr Foo"), store.get("foo"));
        assertEquals(store.getAll().size(), 1);

        assertEquals(eventClient.getEvents(), ImmutableList.of(personAdded("foo", createPerson("foo@example.com", "Mr Foo"))));
    }

    @Test
    public void testIdempotentPut()
    {
        InMemoryEventClient eventClient = new InMemoryEventClient();
        PersonStore store = new PersonStore(new StoreConfig(), eventClient);
        store.put("foo", createPerson("foo@example.com", "Mr Foo"));
        store.put("foo", createPerson("foo@example.com", "Mr Bar"));

        assertEquals(createPerson("foo@example.com", "Mr Bar"), store.get("foo"));
        assertEquals(store.getAll().size(), 1);

        assertEquals(eventClient.getEvents(), ImmutableList.of(
                personAdded("foo", createPerson("foo@example.com", "Mr Foo")),
                personUpdated("foo", createPerson("foo@example.com", "Mr Bar"))
        ));
    }

    @Test
    public void testDelete()
    {
        InMemoryEventClient eventClient = new InMemoryEventClient();
        PersonStore store = new PersonStore(new StoreConfig(), eventClient);
        store.put("foo", createPerson("foo@example.com", "Mr Foo"));
        store.delete("foo");

        assertNull(store.get("foo"));
        assertTrue(store.getAll().isEmpty());

        assertEquals(eventClient.getEvents(), ImmutableList.of(
                personAdded("foo", createPerson("foo@example.com", "Mr Foo")),
                personRemoved("foo", createPerson("foo@example.com", "Mr Foo"))
        ));
    }

    @Test
    public void testIdempotentDelete()
    {
        InMemoryEventClient eventClient = new InMemoryEventClient();
        PersonStore store = new PersonStore(new StoreConfig(), eventClient);
        store.put("foo", createPerson("foo@example.com", "Mr Foo"));

        store.delete("foo");
        assertTrue(store.getAll().isEmpty());
        assertNull(store.get("foo"));

        store.delete("foo");
        assertTrue(store.getAll().isEmpty());
        assertNull(store.get("foo"));

        assertEquals(eventClient.getEvents(), ImmutableList.of(
                personAdded("foo", createPerson("foo@example.com", "Mr Foo")),
                personRemoved("foo", createPerson("foo@example.com", "Mr Foo"))
        ));
    }

    @Test
    public void testGetAll()
    {
        PersonStore store = new PersonStore(new StoreConfig(), new InMemoryEventClient());

        store.put("foo", createPerson("foo@example.com", "Mr Foo"));
        store.put("bar", createPerson("bar@example.com", "Mr Bar"));

        Collection<StoreEntry> entries = store.getAll();
        assertEquals(entries.size(), 2);

        StoreEntry fooEntry = Iterables.find(entries, new Predicate<StoreEntry>()
        {
            @Override
            public boolean apply(StoreEntry input)
            {
                return input.getId().equals("foo");
            }
        });
        assertEquals(fooEntry.getPerson(), createPerson("foo@example.com", "Mr Foo"));

        StoreEntry barEntry = Iterables.find(entries, new Predicate<StoreEntry>()
        {
            @Override
            public boolean apply(StoreEntry input)
            {
                return input.getId().equals("bar");
            }
        });
        assertEquals(barEntry.getPerson(), createPerson("bar@example.com", "Mr Bar"));
    }
}
