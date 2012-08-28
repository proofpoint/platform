package com.proofpoint.event.api;

public final class SimpleEventSource
    extends EventSourceBase
{
    public static SimpleEventSource create()
    {
        return new SimpleEventSource();
    }

    private SimpleEventSource()
    {
    }

    public void raise(String description, Object ... args)
    {
        if (isActive()) {
            dispatch(new SimpleEvent(safeFormat(description, args)));
        }
    }
}
