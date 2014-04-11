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

import com.proofpoint.event.client.EventClient;
import com.proofpoint.stats.CounterStat;
import org.weakref.jmx.Nested;

public class PersonStoreStats
{
    private final CounterStat fetched = new CounterStat();
    private final CounterStat added = new CounterStat();
    private final CounterStat updated = new CounterStat();
    private final CounterStat removed = new CounterStat();
    private final EventClient eventClient;

    public PersonStoreStats(EventClient eventClient)
    {
        this.eventClient = eventClient;
    }

    @Nested
    public CounterStat getFetched()
    {
        return fetched;
    }

    @Nested
    public CounterStat getAdded()
    {
        return added;
    }

    @Nested
    public CounterStat getUpdated()
    {
        return updated;
    }

    @Nested
    public CounterStat getRemoved()
    {
        return removed;
    }

    public void personFetched()
    {
        fetched.add(1);
    }

    public void personAdded(String id, Person person)
    {
        added.add(1);
        eventClient.post(PersonEvent.personAdded(id, person));
    }

    public void personUpdated(String id, Person person)
    {
        updated.add(1);
        eventClient.post(PersonEvent.personUpdated(id, person));
    }

    public void personRemoved(String id, Person person)
    {
        removed.add(1);
        eventClient.post(PersonEvent.personRemoved(id, person));
    }
}
