package com.proofpoint.http.client.jetty;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.proofpoint.http.client.GatheringByteArrayInputStream;
import com.proofpoint.http.client.ResponseTooLargeException;
import com.proofpoint.units.DataSize;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpHeader;

import javax.annotation.concurrent.ThreadSafe;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.proofpoint.units.DataSize.Unit.KILOBYTE;
import static com.proofpoint.units.DataSize.Unit.MEGABYTE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

@ThreadSafe
class BufferingResponseListener
        implements Response.Listener
{
    private static final long BUFFER_MAX_BYTES = new DataSize(1, MEGABYTE).toBytes();
    private static final long BUFFER_MIN_BYTES = new DataSize(1, KILOBYTE).toBytes();
    private final JettyResponseFuture<?, ?> future;
    private final int maxLength;

    @GuardedBy("this")
    private byte[] currentBuffer = new byte[0];
    @GuardedBy("this")
    private int currentBufferPosition;
    @GuardedBy("this")
    private List<byte[]> buffers = new ArrayList<>();
    @GuardedBy("this")
    private long size;

    BufferingResponseListener(JettyResponseFuture<?, ?> future, int maxLength)
    {
        this.future = requireNonNull(future, "future is null");
        checkArgument(maxLength > 0, "maxLength must be greater than zero");
        this.maxLength = maxLength;
    }

    @Override
    public synchronized void onHeaders(Response response)
    {
        long length = response.getHeaders().getLongField(HttpHeader.CONTENT_LENGTH.asString());
        if (length > maxLength) {
            response.abort(new ResponseTooLargeException());
        }
    }

    @Override
    public synchronized void onContent(Response response, ByteBuffer content)
    {
        int length = content.remaining();
        size += length;
        if (size > maxLength) {
            response.abort(new ResponseTooLargeException());
            return;
        }

        while (length > 0) {
            if (currentBufferPosition >= currentBuffer.length) {
                allocateCurrentBuffer();
            }
            int readLength = min(length, currentBuffer.length - currentBufferPosition);
            content.get(currentBuffer, currentBufferPosition, readLength);
            length -= readLength;
            currentBufferPosition += readLength;
        }
    }

    @Override
    public synchronized void onComplete(Result result)
    {
        Throwable throwable = result.getFailure();
        if (throwable != null) {
            future.failed(throwable);
        }
        else {
            currentBuffer = new byte[0];
            currentBufferPosition = 0;
            future.completed(result.getResponse(), new GatheringByteArrayInputStream(buffers, size));
            buffers = new ArrayList<>();
            size = 0;
        }
    }

    private synchronized void allocateCurrentBuffer()
    {
        checkState(currentBufferPosition >= currentBuffer.length, "there is still remaining space in currentBuffer");

        currentBuffer = new byte[(int) min(BUFFER_MAX_BYTES, max(2 * currentBuffer.length, BUFFER_MIN_BYTES))];
        buffers.add(currentBuffer);
        currentBufferPosition = 0;
    }
}
