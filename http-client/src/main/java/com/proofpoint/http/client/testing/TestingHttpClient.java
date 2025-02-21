package com.proofpoint.http.client.testing;

import com.google.common.util.concurrent.ForwardingListenableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.units.Duration;
import jakarta.annotation.Nonnull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

public class TestingHttpClient
        implements HttpClient
{
    private final AtomicReference<Processor> processor;
    private final ListeningExecutorService executor;

    private final AtomicInteger requestCount = new AtomicInteger();
    private final RequestStats stats = new RequestStats();
    private final AtomicBoolean closed = new AtomicBoolean();

    public TestingHttpClient()
    {
        this(MoreExecutors.newDirectExecutorService());
    }

    public TestingHttpClient(ExecutorService executor)
    {
        this((request) -> {
            throw new UnsupportedOperationException();
        }, executor);
    }

    public TestingHttpClient(Processor processor)
    {
        this(processor, MoreExecutors.newDirectExecutorService());
    }

    public TestingHttpClient(Processor processor, ExecutorService executor)
    {
        this.processor = new AtomicReference<>(processor);
        this.executor = MoreExecutors.listeningDecorator(executor);
    }

    @Override
    public <T, E extends Exception> HttpResponseFuture<T> executeAsync(final Request request, final ResponseHandler<T, E> responseHandler)
    {
        requireNonNull(request, "request is null");
        requireNonNull(responseHandler, "responseHandler is null");
        checkState(!closed.get(), "client is closed");

        AtomicReference<String> state = new AtomicReference<>("SENDING_REQUEST");
        ListenableFuture<T> future = executor.submit(() -> execute(request, responseHandler, state));

        return new TestingHttpResponseFuture<>(future, state);
    }

    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        requireNonNull(request, "request is null");
        requireNonNull(responseHandler, "responseHandler is null");
        checkState(!closed.get(), "client is closed");
        return execute(request, responseHandler, new AtomicReference<>("SENDING_REQUEST"));
    }

    private <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler, AtomicReference<String> state)
            throws E
    {
        state.set("PROCESSING_REQUEST");
        long requestStart = System.nanoTime();
        requestCount.incrementAndGet();
        Response response;
        try {
            response = processor.get().handle(request);
        }
        catch (Throwable e) {
            state.set("FAILED");
            long responseStart = System.nanoTime();
            Duration requestProcessingTime = new Duration(responseStart - requestStart, TimeUnit.NANOSECONDS);
            if (e instanceof Exception x) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                try {
                    return responseHandler.handleException(request, x);
                }
                finally {
                    stats.record(request.getMethod(),
                            0,
                            0,
                            0,
                            requestProcessingTime,
                            Duration.nanosSince(responseStart));
                }
            }
            else {
                stats.record(request.getMethod(),
                        0,
                        0,
                        0,
                        requestProcessingTime,
                        new Duration(0, TimeUnit.NANOSECONDS));
                throw (Error) e;
            }
        }
        checkState(response != null, "response is null");

        // notify handler
        state.set("PROCESSING_RESPONSE");
        long responseStart = System.nanoTime();
        Duration requestProcessingTime = new Duration(responseStart - requestStart, TimeUnit.NANOSECONDS);
        try {
            return responseHandler.handle(request, response);
        }
        finally {
            state.set("DONE");
            stats.record(request.getMethod(),
                    response.getStatusCode(),
                    response.getBytesRead(),
                    response.getBytesRead(),
                    requestProcessingTime,
                    Duration.nanosSince(responseStart));
        }
    }

    /**
     * Change the {@link Processor}. Resets the request count.
     *
     * @param processor The {@link Processor} to use for subsequent requests.
     */
    public void setProcessor(Processor processor)
    {
        this.processor.set(processor);
        requestCount.set(0);
    }

    /**
     * @return The number of requests dispatched.
     */
    public int getRequestCount()
    {
        return requestCount.get();
    }

    @Override
    public RequestStats getStats()
    {
        return stats;
    }

    @Override
    public void close()
    {
        closed.set(true);
    }

    @Override
    public boolean isClosed()
    {
        return closed.get();
    }

    public interface Processor
    {
        @Nonnull
        Response handle(Request request)
                throws Exception;
    }

    private static class TestingHttpResponseFuture<T>
            extends ForwardingListenableFuture<T>
            implements HttpResponseFuture<T>
    {
        private final AtomicReference<String> state;
        private final ListenableFuture<T> future;

        private TestingHttpResponseFuture(ListenableFuture<T> future, AtomicReference<String> state)
        {
            this.future = future;
            this.state = state;
        }

        @Override
        public ListenableFuture<T> delegate()
        {
            return future;
        }

        @Override
        public String getState()
        {
            return state.get();
        }
    }
}
