package com.proofpoint.http.client;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * @deprecated Use {@link UncheckedIOException}.
 */
@Deprecated
public class RuntimeIOException
        extends UncheckedIOException
{
    public RuntimeIOException(IOException cause)
    {
        super(cause);
    }

    public RuntimeIOException(String message, IOException cause)
    {
        super(message, cause);
    }
}
