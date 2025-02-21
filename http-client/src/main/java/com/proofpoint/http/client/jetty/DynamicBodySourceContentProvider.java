package com.proofpoint.http.client.jetty;

import com.proofpoint.http.client.DynamicBodySource;
import com.proofpoint.http.client.DynamicBodySource.Writer;
import com.proofpoint.tracetoken.TraceToken;
import com.proofpoint.tracetoken.TraceTokenScope;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.io.RetainableByteBuffer;
import org.eclipse.jetty.io.content.InputStreamContentSource;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.ExceptionUtil;
import org.eclipse.jetty.util.thread.AutoLock;
import org.eclipse.jetty.util.thread.SerializedInvoker;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerTraceToken;
import static java.lang.Math.max;
import static java.lang.Math.min;

class DynamicBodySourceContentProvider
        implements Request.Content
{
    private static final int BUFFER_SIZE = 4096;
    private static final RetainableByteBuffer INITIAL = RetainableByteBuffer.wrap(ByteBuffer.allocate(0));
    private static final RetainableByteBuffer DONE = RetainableByteBuffer.wrap(ByteBuffer.allocate(0));

    private final ByteBufferPool.Sized bufferPool = new ByteBufferPool.Sized(null, false, BUFFER_SIZE);
    private final DynamicBodySource dynamicBodySource;
    private final Queue<RetainableByteBuffer> chunks = new BlockingArrayQueue<>(4, 64);
    private final AtomicLong bytesWritten;
    private final TraceToken traceToken;
    private final SerializedInvoker invoker = new SerializedInvoker(InputStreamContentSource.class);

    private final AutoLock lock = new AutoLock();
    private Writer writer;
    private Runnable demandCallback;
    private Content.Chunk errorChunk;
    private boolean closed;

    DynamicBodySourceContentProvider(DynamicBodySource dynamicBodySource, AtomicLong bytesWritten)
    {
        this.dynamicBodySource = dynamicBodySource;
        this.bytesWritten = bytesWritten;
        traceToken = getCurrentTraceToken();
    }

    @Override
    public long getLength()
    {
        return dynamicBodySource.getLength();
    }

    @Override
    public Content.Chunk read()
    {
        try (AutoLock ignored = lock.lock()) {
            if (errorChunk != null) {
                return errorChunk;
            }
            if (closed) {
                return Content.Chunk.EOF;
            }
            if (writer == null) {
                try (TraceTokenScope ignored2 = registerTraceToken(traceToken)) {
                    writer = dynamicBodySource.start(new DynamicBodySourceOutputStream(bufferPool, chunks));
                }
                catch (Throwable x) {
                    return failure(x);
                }
            }
        }

        RetainableByteBuffer chunk = chunks.poll();
        if (chunk == null) {
            try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
                while (chunk == null) {
                    try {
                        writer.write();
                    }
                    catch (Throwable x) {
                        return failure(x);
                    }
                    chunk = chunks.poll();
                }
            }
        }

        if (chunk == DONE) {
            close();
            if (errorChunk != null) {
                return errorChunk;
            }
            return Content.Chunk.EOF;
        }
        ByteBuffer buffer = chunk.getByteBuffer();
        bytesWritten.addAndGet(buffer.position());
        buffer.flip();
        return Content.Chunk.asChunk(buffer, false, chunk);
    }

    private void close()
    {
        try (AutoLock ignored = lock.lock()) {
            closed = true;
        }
        if (writer instanceof AutoCloseable closeableWriter) {
            try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
                closeableWriter.close();
            }
            catch (Throwable x) {
                try (AutoLock ignored = lock.lock()) {
                    if (errorChunk == null) {
                        errorChunk = Content.Chunk.from(x);
                    }
                }
            }
        }
        for (RetainableByteBuffer chunk = chunks.poll(); chunk != null; chunk = chunks.poll()) {
            chunk.release();
        }
    }

    @Override
    public void demand(Runnable demandCallback)
    {
        try (AutoLock ignored = lock.lock()) {
            if (this.demandCallback != null) {
                throw new IllegalStateException("demand pending");
            }
            this.demandCallback = demandCallback;
        }
        invoker.run(this::invokeDemandCallback);
    }

    private void invokeDemandCallback()
    {
        Runnable demandCallback;
        try (AutoLock ignored = lock.lock()) {
            demandCallback = this.demandCallback;
            this.demandCallback = null;
        }
        if (demandCallback != null) {
            ExceptionUtil.run(demandCallback, this::fail);
        }
    }

    @Override
    public void fail(Throwable failure)
    {
        failure(failure);
    }

    private Content.Chunk failure(Throwable failure)
    {
        Content.Chunk error;
        try (AutoLock ignored = lock.lock()) {
            error = errorChunk;
            if (error == null) {
                error = errorChunk = Content.Chunk.from(failure);
            }
            close();
        }
        return error;
    }

    private static class DynamicBodySourceOutputStream
            extends OutputStream
    {
        private RetainableByteBuffer lastChunk = INITIAL;
        private final ByteBufferPool.Sized bufferPool;
        private final Queue<RetainableByteBuffer> chunks;

        private DynamicBodySourceOutputStream(ByteBufferPool.Sized bufferPool, Queue<RetainableByteBuffer> chunks)
        {
            this.bufferPool = bufferPool;
            this.chunks = chunks;
        }

        @Override
        public void write(int b)
        {
            if (!chunks.isEmpty() && lastChunk.hasRemaining()) {
                lastChunk.getByteBuffer().put((byte) b);
            }
            else {
                lastChunk = bufferPool.acquire();
                lastChunk.getByteBuffer().clear().put((byte) b);
                chunks.add(lastChunk);
            }
        }

        @Override
        public void write(byte[] b, int off, int len)
        {
            if (!chunks.isEmpty() && lastChunk.hasRemaining()) {
                int toCopy = min(len, lastChunk.remaining());
                lastChunk.getByteBuffer().put(b, off, toCopy);
                if (toCopy == len) {
                    return;
                }
                off += toCopy;
                len -= toCopy;
            }

            lastChunk = bufferPool.acquire(max(BUFFER_SIZE, len), false);
            lastChunk.getByteBuffer().clear().put(b, off, len);
            chunks.add(lastChunk);
        }

        @Override
        public void close()
        {
            lastChunk = DONE;
            chunks.add(DONE);
        }
    }
}
