package com.proofpoint.event.client;

import com.proofpoint.event.api.Event;
import com.proofpoint.event.api.EventSourceBase;

public interface EventDispatcher
{
    void registerSource(EventSourceBase eventSource);
    void dispatch(Event event, EventSourceBase eventSource);
}
