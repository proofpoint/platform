package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.proofpoint.event.api.Event;
import com.proofpoint.event.api.EventSink;

import java.util.List;

public class InMemoryEventSink
    implements EventSink
{
    private final List<Event> events;

    public InMemoryEventSink()
    {
        events = Lists.newArrayList();
    }

    public synchronized void post(Event event)
    {
        Preconditions.checkNotNull(event);
        events.add(event);
    }

    public synchronized List<Event> getEvents()
    {
        return ImmutableList.copyOf(events);
    }
}
