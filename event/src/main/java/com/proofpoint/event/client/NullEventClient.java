package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Arrays;

public class NullEventClient implements EventClient
{
    @Override
    public <T> ListenableFuture<Void> post(T... events)
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(events, "event is null");
        return post(Arrays.asList(events));
    }

    @Override
    public <T> ListenableFuture<Void> post(Iterable<T> events)
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(events, "event is null");
        try {
            for (T event : events) {
                Preconditions.checkNotNull(event, "event is null");
            }
        }
        catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
        return Futures.immediateFuture(null);
    }

    @Override
    public <T> ListenableFuture<Void> post(EventGenerator<T> eventGenerator)
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(eventGenerator, "eventGenerator is null");
        try {
            eventGenerator.generate(new EventPoster<T>()
            {
                @Override
                public void post(T event)
                {
                    Preconditions.checkNotNull(event, "event is null");
                }
            });
        }
        catch (Exception e) {
            Futures.immediateFailedFuture(e);
        }
        return Futures.immediateFuture(null);
    }
}
