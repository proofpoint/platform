package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.proofpoint.event.api.Event;
import com.proofpoint.event.api.EventSink;

public class EventCollectorSink
    implements EventSink
{
    private EventClient eventClient;

    @Inject
    public EventCollectorSink(EventClient eventClient)
    {
        Preconditions.checkNotNull(eventClient, "eventClient is null");
        this.eventClient = eventClient;
    }

    public void post(Event event)
    {
        eventClient.post(event);
    }
}
