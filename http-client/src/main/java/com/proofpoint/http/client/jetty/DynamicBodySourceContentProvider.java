package com.proofpoint.http.client.jetty;

import com.google.common.collect.AbstractIterator;
import com.proofpoint.http.client.DynamicBodySource;
import com.proofpoint.http.client.DynamicBodySource.Writer;
import com.proofpoint.tracetoken.TraceToken;
import com.proofpoint.tracetoken.TraceTokenScope;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.util.BlockingArrayQueue;

import java.io.Closeable;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerTraceToken;
import static java.lang.Math.max;
import static java.lang.Math.min;

class DynamicBodySourceContentProvider
        implements ContentProvider
{
    private static final ByteBuffer INITIAL = ByteBuffer.allocate(0);
    private static final ByteBuffer DONE = ByteBuffer.allocate(0);

    private final DynamicBodySource dynamicBodySource;
    private final AtomicLong bytesWritten;
    private final TraceToken traceToken;

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
    public Iterator<ByteBuffer> iterator()
    {
        final Queue<ByteBuffer> chunks = new BlockingArrayQueue<>(4, 64);

        Writer writer;
        try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
            writer = dynamicBodySource.start(new DynamicBodySourceOutputStream(chunks));
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }

        return new DynamicBodySourceIterator(chunks, writer, bytesWritten, traceToken);
    }

    private static class DynamicBodySourceOutputStream
            extends OutputStream
    {
        private static int BUFFER_SIZE = 4096;
        private ByteBuffer lastChunk = INITIAL;
        private final Queue<ByteBuffer> chunks;

        private DynamicBodySourceOutputStream(Queue<ByteBuffer> chunks)
        {
            this.chunks = chunks;
        }

        @Override
        public void write(int b)
        {
            if (!chunks.isEmpty() && lastChunk.hasRemaining()) {
                lastChunk.put((byte)b);
            }
            else {
                lastChunk = ByteBuffer.allocate(BUFFER_SIZE);
                lastChunk.put((byte)b);
                chunks.add(lastChunk);
            }
        }

        @Override
        public void write(byte[] b, int off, int len)
        {
            if (!chunks.isEmpty() && lastChunk.hasRemaining()) {
                int toCopy = min(len, lastChunk.remaining());
                lastChunk.put(b, off, toCopy);
                if (toCopy == len) {
                    return;
                }
                off += toCopy;
                len -= toCopy;
            }

            lastChunk = ByteBuffer.allocate(max(BUFFER_SIZE, len));
            lastChunk.put(b, off, len);
            chunks.add(lastChunk);

        }

        @Override
        public void close()
        {
            lastChunk = DONE;
            chunks.add(DONE);
        }
    }

    private static class DynamicBodySourceIterator extends AbstractIterator<ByteBuffer>
            implements Closeable
    {
        private final Queue<ByteBuffer> chunks;
        private final Writer writer;
        private final AtomicLong bytesWritten;
        private final TraceToken traceToken;

        @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
        DynamicBodySourceIterator(Queue<ByteBuffer> chunks, Writer writer, AtomicLong bytesWritten, TraceToken traceToken)
        {
            this.chunks = chunks;
            this.writer = writer;
            this.bytesWritten = bytesWritten;
            this.traceToken = traceToken;
        }

        @Override
        @SuppressWarnings("ReferenceEquality") // Reference equality to DONE is intentional
        protected ByteBuffer computeNext()
        {
            ByteBuffer chunk = chunks.poll();
            if (chunk == null) {
                try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
                    while (chunk == null) {
                        try {
                            writer.write();
                        }
                        catch (Exception e) {
                            throwIfUnchecked(e);
                            throw new RuntimeException(e);
                        }
                        chunk = chunks.poll();
                    }
                }
            }

            if (chunk == DONE) {
                return endOfData();
            }
            bytesWritten.addAndGet(chunk.position());
            ((Buffer)chunk).flip();
            return chunk;
        }

        @Override
        public void close()
        {
            if (writer instanceof AutoCloseable) {
                try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
                    ((AutoCloseable)writer).close();
                }
                catch (Exception e) {
                    throwIfUnchecked(e);
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
