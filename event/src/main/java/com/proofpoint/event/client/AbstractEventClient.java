package com.proofpoint.event.client;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class AbstractEventClient
        implements EventClient
{
    @SafeVarargs
    @Override
    public final <T> ListenableFuture<Void> post(T... event)
            throws IllegalArgumentException
    {
        checkNotNull(event, "event is null");
        return post(Arrays.asList(event));
    }

    @Override
    public final <T> ListenableFuture<Void> post(final Iterable<T> events)
            throws IllegalArgumentException
    {
        checkNotNull(events, "events is null");
        try {
            for (T event : events) {
                checkNotNull(event, "event is null");
                postEvent(event);
            }
        }
        catch (IOException e) {
            return Futures.immediateFailedCheckedFuture(e);
        }
        return Futures.immediateCheckedFuture(null);
    }

    protected abstract <T> void postEvent(T event)
            throws IOException;
}
