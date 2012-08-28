package com.proofpoint.event.api;

import com.proofpoint.units.Duration;

public final class OperationEventSource
    extends EventSourceBase
{
    public static OperationEventSource create()
    {
        return new OperationEventSource();
    }

    private OperationEventSource()
    {
    }

    public void raise(boolean success, int status, Duration duration, String description, Object ... args)
    {
        if (isActive()) {
            dispatch(new OperationEvent(safeFormat(description, args), success, status, duration));
        }
    }

    public void raise(boolean success, Duration duration, String description, Object ... args)
    {
        if (isActive()) {
            dispatch(new OperationEvent(safeFormat(description, args), success, 0, duration));
        }
    }

    public void raise(Duration duration, String description, Object ... args)
    {
        raise(true, 0, duration, description, args);
    }
}
