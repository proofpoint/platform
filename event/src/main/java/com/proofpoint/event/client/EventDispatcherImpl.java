package com.proofpoint.event.client;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.proofpoint.event.api.Event;
import com.proofpoint.event.api.EventSink;
import com.proofpoint.event.api.EventSourceBase;

import java.util.List;
import java.util.Set;

public class EventDispatcherImpl
    implements EventDispatcher
{
    private final List<EventSink> eventSinks;

    @Inject
    public EventDispatcherImpl(Set<EventSink> eventSinks)
    {
        this.eventSinks = ImmutableList.copyOf(eventSinks);
    }

    public void registerSource(EventSourceBase eventSource)
    {
        //TODO: implement
    }

    public void dispatch(Event event, EventSourceBase eventSource)
    {
        //TODO: implement selective dispatching based on event source

        for (EventSink eventSink : eventSinks) {
            eventSink.post(event);
        }
    }
}
