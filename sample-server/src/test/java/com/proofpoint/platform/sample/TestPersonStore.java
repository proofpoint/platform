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

import com.proofpoint.platform.sample.PersonStore.StoreEntry;
import com.proofpoint.testing.TestingTicker;
import com.proofpoint.units.Duration;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class TestPersonStore
{
    private final TestingTicker ticker = new TestingTicker();

    @Test
    public void testStartsEmpty()
    {
        PersonStore store = new PersonStore(new StoreConfig(), ticker);
        assertThat(store.getAll()).isEmpty();
    }

    @Test
    public void testTtl()
    {
        StoreConfig config = new StoreConfig();
        config.setTtl(new Duration(1, TimeUnit.MILLISECONDS));

        PersonStore store = new PersonStore(config, ticker);
        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        ticker.elapseTime(2, TimeUnit.MILLISECONDS);
        assertThat(store.get("foo")).isNull();
    }

    @Test
    public void testPut()
    {
        PersonStore store = new PersonStore(new StoreConfig(), ticker);
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        assertThat(store.get("foo")).isEqualTo(new Person("foo@example.com", "Mr Foo"));
        assertThat(store.getAll()).hasSize(1);
    }

    @Test
    public void testIdempotentPut()
    {
        PersonStore store = new PersonStore(new StoreConfig(), ticker);
        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        store.put("foo", new Person("foo@example.com", "Mr Bar"));

        assertThat(store.get("foo")).isEqualTo(new Person("foo@example.com", "Mr Bar"));
        assertThat(store.getAll()).hasSize(1);
    }

    @Test
    public void testDelete()
    {
        PersonStore store = new PersonStore(new StoreConfig(), ticker);
        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        store.delete("foo");

        assertThat(store.get("foo")).isNull();
        assertThat(store.getAll()).isEmpty();
    }

    @Test
    public void testIdempotentDelete()
    {
        PersonStore store = new PersonStore(new StoreConfig(), ticker);
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        store.delete("foo");
        assertThat(store.getAll()).isEmpty();
        assertThat(store.get("foo")).isNull();

        store.delete("foo");
        assertThat(store.getAll()).isEmpty();
        assertThat(store.get("foo")).isNull();
    }

    @Test
    public void testGetAll()
    {
        PersonStore store = new PersonStore(new StoreConfig(), ticker);

        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        store.put("bar", new Person("bar@example.com", "Mr Bar"));

        Collection<StoreEntry> entries = store.getAll();
        assertThat(entries).hasSize(2);

        assertThat(entries)
                .filteredOn("id", "foo")
                .extracting("person")
                .containsExactly(new Person("foo@example.com", "Mr Foo"));

        assertThat(entries)
                .filteredOn("id", "bar")
                .extracting("person")
                .containsExactly(new Person("bar@example.com", "Mr Bar"));
    }
}
