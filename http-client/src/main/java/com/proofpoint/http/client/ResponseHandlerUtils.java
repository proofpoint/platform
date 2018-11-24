package com.proofpoint.http.client;

import java.io.IOException;
import java.net.ConnectException;

import static com.google.common.base.Throwables.throwIfUnchecked;

public final class ResponseHandlerUtils
{
    private ResponseHandlerUtils()
    {
    }

    @SuppressWarnings("deprecation")
    public static RuntimeException propagate(Request request, Throwable exception)
    {
        if (exception instanceof ConnectException) {
            throw new RuntimeIOException("Server refused connection: " + request.getUri().toASCIIString(), (ConnectException) exception);
        }
        if (exception instanceof IOException) {
            throw new RuntimeIOException((IOException) exception);
        }
        throwIfUnchecked(exception);
        throw new RuntimeException(exception);
    }
}
