package com.proofpoint.concurrent;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.proofpoint.units.Duration;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.propagateIfInstanceOf;
import static com.google.common.collect.Iterables.isEmpty;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public final class MoreFutures
{
    private MoreFutures() { }

    /**
     * Returns a future that can not be completed or canceled.
     */
    public static <V> CompletableFuture<V> unmodifiableFuture(CompletableFuture<V> future)
    {
        return unmodifiableFuture(future, false);
    }

    /**
     * Returns a future that can not be completed or optionally canceled.
     */
    public static <V> CompletableFuture<V> unmodifiableFuture(CompletableFuture<V> future, boolean propagateCancel)
    {
        requireNonNull(future, "future is null");

        Function<Boolean, Boolean> onCancelFunction;
        if (propagateCancel) {
            onCancelFunction = future::cancel;
        }
        else {
            onCancelFunction = mayInterrupt -> false;
        }

        UnmodifiableCompletableFuture<V> unmodifiableFuture = new UnmodifiableCompletableFuture<>(onCancelFunction);
        future.whenComplete((value, exception) -> {
            if (exception != null) {
                unmodifiableFuture.internalCompleteExceptionally(exception);
            }
            else {
                unmodifiableFuture.internalComplete(value);
            }
        });
        return unmodifiableFuture;
    }

    /**
     * Returns a failed future containing the specified throwable.
     */
    public static <V> CompletableFuture<V> failedFuture(Throwable throwable)
    {
        requireNonNull(throwable, "throwable is null");
        CompletableFuture<V> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    /**
     * Waits for the value from the future. If the future is failed, the exception
     * is thrown directly if unchecked or wrapped in a RuntimeException. If the
     * thread is interrupted, the thread interruption flag is set and the original
     * InterruptedException is wrapped in a RuntimeException and thrown.
     */
    public static <V> V getFutureValue(Future<V> future)
    {
        return getFutureValue(future, RuntimeException.class);
    }

    /**
     * Waits for the value from the future. If the future is failed, the exception
     * is thrown directly if it is an instance of the specified exception type or
     * unchecked, or it is wrapped in a RuntimeException. If the thread is
     * interrupted, the thread interruption flag is set and the original
     * InterruptedException is wrapped in a RuntimeException and thrown.
     */
    public static <V, E extends Exception> V getFutureValue(Future<V> future, Class<E> exceptionType)
            throws E
    {
        requireNonNull(future, "future is null");
        requireNonNull(exceptionType, "exceptionType is null");

        try {
            return future.get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            propagateIfInstanceOf(cause, exceptionType);
            throw Throwables.propagate(cause);
        }
    }

    /**
     * Gets the current value of the future without waiting. If the future
     * value is null, an empty Optional is still returned, and in this case the caller
     * must check the future directly for the null value.
     */
    public static <T> Optional<T> tryGetFutureValue(Future<T> future)
    {
        requireNonNull(future, "future is null");
        if (!future.isDone()) {
            return Optional.empty();
        }

        return tryGetFutureValue(future, 0, MILLISECONDS);
    }

    /**
     * Waits for the the value from the future for the specified time.  If the future
     * value is null, an empty Optional is still returned, and in this case the caller
     * must check the future directly for the null value.  If the future is failed,
     * the exception is thrown directly if unchecked or wrapped in a RuntimeException.
     * If the thread is interrupted, the thread interruption flag is set and the original
     * InterruptedException is wrapped in a RuntimeException and thrown.
     */
    public static <V> Optional<V> tryGetFutureValue(Future<V> future, int timeout, TimeUnit timeUnit)
    {
        return tryGetFutureValue(future, timeout, timeUnit, RuntimeException.class);
    }

    /**
     * Waits for the the value from the future for the specified time.  If the future
     * value is null, an empty Optional is still returned, and in this case the caller
     * must check the future directly for the null value.  If the future is failed,
     * the exception is thrown directly if it is an instance of the specified exception
     * type or unchecked, or it is wrapped in a RuntimeException. If the thread is
     * interrupted, the thread interruption flag is set and the original
     * InterruptedException is wrapped in a RuntimeException and thrown.
     */
    public static <V, E extends Exception> Optional<V> tryGetFutureValue(Future<V> future, int timeout, TimeUnit timeUnit, Class<E> exceptionType)
            throws E
    {
        requireNonNull(future, "future is null");
        checkArgument(timeout >= 0, "timeout is negative");
        requireNonNull(timeUnit, "timeUnit is null");
        requireNonNull(exceptionType, "exceptionType is null");

        try {
            return Optional.ofNullable(future.get(timeout, timeUnit));
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            propagateIfInstanceOf(cause, exceptionType);
            throw Throwables.propagate(cause);
        }
        catch (TimeoutException expected) {
            // expected
        }
        return Optional.empty();
    }

    /**
     * Creates a future that completes when the first future completes either normally
     * or exceptionally. Cancellation of the future does not propagate to the supplied
     * futures.
     */
    public static <V> CompletableFuture<V> firstCompletedFuture(Iterable<? extends CompletionStage<? extends V>> futures)
    {
        return firstCompletedFuture(futures, false);
    }

    /**
     * Creates a future that completes when the first future completes either normally
     * or exceptionally. Cancellation of the future will optionally propagate to the
     * supplied futures.
     */
    public static <V> CompletableFuture<V> firstCompletedFuture(Iterable<? extends CompletionStage<? extends V>> futures, boolean propagateCancel)
    {
        requireNonNull(futures, "futures is null");
        checkArgument(!isEmpty(futures), "futures is empty");

        CompletableFuture<V> future = new CompletableFuture<>();
        for (CompletionStage<? extends V> stage : futures) {
            stage.whenComplete((value, exception) -> {
                if (exception != null) {
                    future.completeExceptionally(exception);
                }
                else {
                    future.complete(value);
                }
            });
        }
        if (propagateCancel) {
            future.exceptionally(throwable -> {
                if (throwable instanceof CancellationException) {
                    for (CompletionStage<? extends V> sourceFuture : futures) {
                        if (sourceFuture instanceof Future) {
                            ((Future<?>) sourceFuture).cancel(true);
                        }
                    }
                }
                return null;
            });
        }
        return future;
    }

    /**
     * Returns a new future that is completed when the supplied future completes or
     * when the timeout expires.  If the timeout occurs or the returned CompletableFuture
     * is canceled, the supplied future will be canceled.
     */
    public static <T> CompletableFuture<T> addTimeout(CompletableFuture<T> future, ValueSupplier<T> onTimeout, Duration timeout, ScheduledExecutorService executorService)
    {
        requireNonNull(future, "future is null");
        requireNonNull(onTimeout, "timeoutValue is null");
        requireNonNull(timeout, "timeout is null");
        requireNonNull(executorService, "executorService is null");

        // if the future is already complete, just return it
        if (future.isDone()) {
            return future;
        }

        // create an unmodifiable future that propagates cancel
        // down cast is safe because this is our code
        UnmodifiableCompletableFuture<T> futureWithTimeout = (UnmodifiableCompletableFuture<T>) unmodifiableFuture(future, true);

        // schedule a task to complete the future when the time expires
        ScheduledFuture<?> timeoutTaskFuture = executorService.schedule(new TimeoutFutureTask<>(futureWithTimeout, onTimeout, future), timeout.toMillis(), MILLISECONDS);

        // when future completes, cancel the timeout task
        future.whenCompleteAsync((value, exception) -> timeoutTaskFuture.cancel(false), executorService);

        return futureWithTimeout;
    }

    /**
     * Converts a ListenableFuture to a CompletableFuture. Cancellation of the
     * CompletableFuture will be propagated to the ListenableFuture.
     */
    public static <V> CompletableFuture<V> toCompletableFuture(ListenableFuture<V> listenableFuture)
    {
        requireNonNull(listenableFuture, "listenableFuture is null");

        CompletableFuture<V> future = new CompletableFuture<>();
        future.exceptionally(throwable -> {
            if (throwable instanceof CancellationException) {
                listenableFuture.cancel(true);
            }
            return null;
        });

        Futures.addCallback(listenableFuture, new FutureCallback<V>()
        {
            @Override
            @SuppressFBWarnings("NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE")
            public void onSuccess(V result)
            {
                future.complete(result);
            }

            @Override
            public void onFailure(Throwable t)
            {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * Converts a CompletableFuture to a ListenableFuture. Cancellation of the
     * ListenableFuture will be propagated to the CompletableFuture.
     */
    public static <V> ListenableFuture<V> toListenableFuture(CompletableFuture<V> completableFuture)
    {
        requireNonNull(completableFuture, "completableFuture is null");
        SettableFuture<V> future = SettableFuture.create();
        Futures.addCallback(future, new FutureCallback<V>()
        {
            @Override
            public void onSuccess(V result)
            {
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                if (throwable instanceof CancellationException) {
                    completableFuture.cancel(true);
                }
            }
        });

        completableFuture.whenComplete((value, exception) -> {
            if (exception != null) {
                future.setException(exception);
            }
            else {
                future.set(value);
            }
        });
        return future;
    }

    public interface ValueSupplier<T>
    {
        T get()
                throws Exception;
    }

    private static class UnmodifiableCompletableFuture<V>
            extends CompletableFuture<V>
    {
        private final Function<Boolean, Boolean> onCancel;

        public UnmodifiableCompletableFuture(Function<Boolean, Boolean> onCancel)
        {
            this.onCancel = requireNonNull(onCancel, "onCancel is null");
        }

        void internalComplete(V value)
        {
            super.complete(value);
        }

        void internalCompleteExceptionally(Throwable ex)
        {
            super.completeExceptionally(ex);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            return onCancel.apply(mayInterruptIfRunning);
        }

        @Override
        public boolean complete(V value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean completeExceptionally(Throwable ex)
        {
            if (ex instanceof CancellationException) {
                return cancel(false);
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public void obtrudeValue(V value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void obtrudeException(Throwable ex)
        {
            throw new UnsupportedOperationException();
        }
    }

    private static class TimeoutFutureTask<T>
            implements Runnable
    {
        private final UnmodifiableCompletableFuture<T> settableFuture;
        private final ValueSupplier<T> timeoutValue;
        private final WeakReference<CompletableFuture<T>> futureReference;

        public TimeoutFutureTask(UnmodifiableCompletableFuture<T> settableFuture, ValueSupplier<T> timeoutValue, CompletableFuture<T> future)
        {
            this.settableFuture = settableFuture;
            this.timeoutValue = timeoutValue;

            // the scheduled executor can hold on to the timeout task for a long time, and
            // the future can reference large expensive objects.  Since we are only interested
            // in canceling this future on a timeout, only hold a weak reference to the future
            this.futureReference = new WeakReference<>(future);
        }

        @Override
        public void run()
        {
            if (settableFuture.isDone()) {
                return;
            }

            // run the timeout task and set the result into the future
            try {
                T result = timeoutValue.get();
                settableFuture.internalComplete(result);
            }
            catch (Throwable t) {
                settableFuture.internalCompleteExceptionally(t);
                propagateIfInstanceOf(t, RuntimeException.class);
            }

            // cancel the original future, if it still exists
            Future<T> future = futureReference.get();
            if (future != null) {
                future.cancel(true);
            }
        }
    }
}
