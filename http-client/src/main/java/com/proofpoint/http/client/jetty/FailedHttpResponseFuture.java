package com.proofpoint.http.client.jetty;

import com.google.common.util.concurrent.ForwardingListenableFuture.SimpleForwardingListenableFuture;
import com.proofpoint.http.client.HttpClient;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

public class FailedHttpResponseFuture<T>
        extends SimpleForwardingListenableFuture<T>
        implements HttpClient.HttpResponseFuture<T>
{
    public FailedHttpResponseFuture(Throwable throwable)
    {
        super(immediateFailedFuture(throwable));
    }

    @Override
    public String getState()
    {
        return "FAILED";
    }
}