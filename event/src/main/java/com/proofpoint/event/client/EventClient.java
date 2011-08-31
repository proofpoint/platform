package com.proofpoint.event.client;

import java.io.IOException;

import com.google.common.util.concurrent.ListenableFuture;

public interface EventClient
{
    <T> ListenableFuture<Void> post(T... event)
            throws IllegalArgumentException;

    <T> ListenableFuture<Void> post(Iterable<T> events)
            throws IllegalArgumentException;

    <T> ListenableFuture<Void> post(EventGenerator<T> eventGenerator)
            throws IllegalArgumentException;

    public interface EventGenerator<T>
    {
        void generate(EventPoster<T> eventPoster)
                throws IOException;
    }

    public interface EventPoster<T>
    {
        void post(T event)
                throws IOException;
    }
}
