package com.proofpoint.http.client.jetty;

import com.proofpoint.http.client.InputStreamBodySource;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

class BodySourceInputStream extends InputStream
{
    private final InputStream delegate;
    private final AtomicLong bytesWritten;

    BodySourceInputStream(InputStreamBodySource bodySource, AtomicLong bytesWritten)
    {
        delegate = bodySource.getInputStream();
        this.bytesWritten = bytesWritten;
    }

    @Override
    public int read()
            throws IOException
    {
        // We guarantee we don't call the int read() method of the delegate.
        throw new UnsupportedOperationException();
    }

    @Override
    public int read(byte[] b)
            throws IOException
    {
        int read = delegate.read(b);
        if (read > 0) {
            bytesWritten.addAndGet(read);
        }
        return read;
    }

    @Override
    public int read(byte[] b, int off, int len)
            throws IOException
    {
        int read = delegate.read(b, off, len);
        if (read > 0) {
            bytesWritten.addAndGet(read);
        }
        return read;
    }

    @Override
    public long skip(long n)
            throws IOException
    {
        return delegate.skip(n);
    }

    @Override
    public int available()
            throws IOException
    {
        return delegate.available();
    }

    @Override
    public void close()
    {
        // We guarantee we don't call this
        throw new UnsupportedOperationException();
    }

    @Override
    public void mark(int readlimit)
    {
        // We guarantee we don't call this
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset()
            throws IOException
    {
        // We guarantee we don't call this
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean markSupported()
    {
        return false;
    }
}
