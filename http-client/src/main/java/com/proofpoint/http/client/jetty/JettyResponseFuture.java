package com.proofpoint.http.client.jetty;

import com.google.common.util.concurrent.AbstractFuture;
import com.proofpoint.http.client.HttpClient.HttpResponseFuture;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.tracetoken.TraceToken;
import com.proofpoint.tracetoken.TraceTokenScope;
import org.eclipse.jetty.client.Response;

import java.io.InputStream;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerTraceToken;
import static java.util.Objects.requireNonNull;

class JettyResponseFuture<T, E extends Exception>
        extends AbstractFuture<T>
        implements HttpResponseFuture<T>
{
    private enum JettyAsyncHttpState
    {
        WAITING_FOR_CONNECTION,
        PROCESSING_RESPONSE,
        DONE,
        FAILED,
        CANCELED
    }

    private JettyHttpClient jettyHttpClient;
    private final long requestStart = System.nanoTime();
    private final AtomicReference<JettyAsyncHttpState> state = new AtomicReference<>(JettyAsyncHttpState.WAITING_FOR_CONNECTION);
    private final Request request;
    private final org.eclipse.jetty.client.Request jettyRequest;
    private final ResponseHandler<T, E> responseHandler;
    private final AtomicLong bytesWritten;
    private final RequestStats stats;
    private final TraceToken traceToken;

    JettyResponseFuture(JettyHttpClient jettyHttpClient, Request request, org.eclipse.jetty.client.Request jettyRequest, ResponseHandler<T, E> responseHandler, AtomicLong bytesWritten, RequestStats stats)
    {
        this.jettyHttpClient = requireNonNull(jettyHttpClient, "jettyHttpClientf is null");
        this.request = requireNonNull(request, "request is null");
        this.jettyRequest = requireNonNull(jettyRequest, "jettyRequest is null");
        this.responseHandler = requireNonNull(responseHandler, "responseHandler is null");
        this.bytesWritten = requireNonNull(bytesWritten, "bytesWritten is null");
        this.stats = requireNonNull(stats, "stats is null");
        traceToken = getCurrentTraceToken();
    }

    @Override
    public String getState()
    {
        return state.get().toString();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        try {
            state.set(JettyAsyncHttpState.CANCELED);
            jettyRequest.abort(new CancellationException());
            return super.cancel(mayInterruptIfRunning);
        }
        catch (Throwable e) {
            try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
                setException(e);
            }
            return true;
        }
    }

    void completed(Response response, InputStream content)
    {
        if (state.get() == JettyAsyncHttpState.CANCELED) {
            return;
        }

        try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
            T value;
            try {
                value = processResponse(response, content);
            }
            catch (Throwable e) {
                // this will be an instance of E from the response handler or an Error
                storeException(e);
                return;
            }
            state.set(JettyAsyncHttpState.DONE);
            set(value);
        }
    }

    private T processResponse(Response response, InputStream content)
            throws E
    {
        // this time will not include the data fetching portion of the response,
        // since the response is fully cached in memory at this point
        long responseStart = System.nanoTime();

        state.set(JettyAsyncHttpState.PROCESSING_RESPONSE);
        JettyResponse jettyResponse = null;
        T value;
        try {
            jettyResponse = new JettyResponse(response, content);
            value = responseHandler.handle(request, jettyResponse);
        }
        finally {
            JettyHttpClient.recordRequestComplete(stats, request, requestStart, bytesWritten.get(), jettyResponse, responseStart);
        }
        return value;
    }

    void failed(Throwable throwable)
    {
        if (state.get() == JettyAsyncHttpState.CANCELED) {
            return;
        }
        try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
            // give handler a chance to rewrite the exception or return a value instead
            if (throwable instanceof Exception) {
                try {
                    if (throwable instanceof RejectedExecutionException) {
                        jettyHttpClient.maybeLogJettyState();
                    }
                    T value = responseHandler.handleException(request, (Exception) throwable);
                    // handler returned a value, store it in the future
                    state.set(JettyAsyncHttpState.DONE);
                    set(value);
                    return;
                }
                catch (Throwable newThrowable) {
                    throwable = newThrowable;
                }
            }

            // at this point "throwable" will either be an instance of E
            // from the response handler or not an instance of Exception
            storeException(throwable);
        }
    }

    private void storeException(Throwable throwable)
    {
        if (throwable instanceof CancellationException) {
            state.set(JettyAsyncHttpState.CANCELED);
        }
        else {
            state.set(JettyAsyncHttpState.FAILED);
        }
        if (throwable == null) {
            throwable = new Throwable("Throwable is null");
        }

        setException(throwable);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("requestStart", requestStart)
                .add("state", state)
                .add("request", request)
                .toString();
    }
}
