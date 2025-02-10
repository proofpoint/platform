package com.proofpoint.http.client.jetty;

import com.proofpoint.http.client.InputStreamBodySource;
import org.eclipse.jetty.client.InputStreamRequestContent;

import java.util.concurrent.atomic.AtomicLong;

class InputStreamBodySourceContentProvider extends InputStreamRequestContent
{
    private final long length;

    public InputStreamBodySourceContentProvider(InputStreamBodySource inputStreamBodySource, AtomicLong bytesWritten)
    {
        super(new BodySourceInputStream(inputStreamBodySource, bytesWritten), inputStreamBodySource.getBufferSize());
        length = inputStreamBodySource.getLength();
    }

    @Override
    public long getLength()
    {
        return length;
    }
}
