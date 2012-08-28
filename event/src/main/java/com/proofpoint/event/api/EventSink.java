package com.proofpoint.event.api;

public interface EventSink
{
    void post(Event event);
}
