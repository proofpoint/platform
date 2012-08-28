package com.proofpoint.event.api;

public final class ExceptionEventSource
    extends EventSourceBase
{
    public static ExceptionEventSource create()
    {
        return new ExceptionEventSource();
    }

    private ExceptionEventSource()
    {
    }

    public void raise(Throwable exception, String description, Object ... args)
    {
        if (isActive()) {
            dispatch(new ExceptionEvent(safeFormat(description, args), exception));
        }
    }

    public void raise(Throwable exception)
    {
        raise(exception, "");
    }
}
