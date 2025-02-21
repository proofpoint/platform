package com.proofpoint.http.client;

import java.io.IOException;
import java.io.UncheckedIOException;
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
        if (exception instanceof ConnectException connectException) {
            throw new UncheckedIOException("Server refused connection: " + request.getUri().toASCIIString(), connectException);
        }
        if (exception instanceof IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
        throwIfUnchecked(exception);
        throw new RuntimeException(exception);
    }

    public static byte[] readResponseBytes(Request request, Response response)
    {
        try {
            return response.getInputStream().readAllBytes();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed reading response from server: " + urlFor(request), e);
        }
    }

    private static String urlFor(Request request)
    {
        return request.getUri().toASCIIString();
    }
}
