package com.proofpoint.http.client.jetty;

import com.proofpoint.http.client.InputStreamBodySource;
import org.eclipse.jetty.client.util.InputStreamContentProvider;

import java.util.concurrent.atomic.AtomicLong;

class InputStreamBodySourceContentProvider extends InputStreamContentProvider
{
    private final long length;

    public InputStreamBodySourceContentProvider(InputStreamBodySource inputStreamBodySource, AtomicLong bytesWritten)
    {
        super(new BodySourceInputStream(inputStreamBodySource, bytesWritten), inputStreamBodySource.getBufferSize(), false);
        length = inputStreamBodySource.getLength();
    }

    @Override
    public long getLength()
    {
        return length;
    }
}
