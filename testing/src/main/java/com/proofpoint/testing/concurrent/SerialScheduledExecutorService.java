/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.testing.concurrent;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.Futures;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implements ScheduledExecutorService with a controllable time elapse. Tasks are run
 * in the context of the thread advancing time.
 *
 * Tasks are modelled as instantaneous events; tasks scheduled to be run at the same
 * instant will be run in the order of their registration.
 */
public class SerialScheduledExecutorService
        implements ScheduledExecutorService
{
    private final PriorityQueue<SerialScheduledFuture<?>> tasks = new PriorityQueue<SerialScheduledFuture<?>>();
    private boolean isShutdown = false;

    @Override
    public void execute(Runnable runnable)
    {
        runnable.run();
    }

    @Override
    public void shutdown()
    {
        isShutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow()
    {
        shutdown();
        // Note: This doesn't seem to be quite right. It seems like I should return the unexecuted tasks that
        // were scheduled on the ScheduledExecutorService, but I don't know how to turn a Callable into a
        // Runnable (which is required since future tasks can be scheduled as Callables).
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown()
    {
        return isShutdown;
    }

    @Override
    public boolean isTerminated()
    {
        return isShutdown();
    }

    @Override
    public boolean awaitTermination(long l, TimeUnit timeUnit)
            throws InterruptedException
    {
        return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> tCallable)
    {
        try {
            return Futures.immediateFuture(tCallable.call());
        }
        catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    @Override
    public <T> Future<T> submit(Runnable runnable, T t)
    {
        try {
            runnable.run();
            return Futures.immediateFuture(t);
        }
        catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    @Override
    public Future<?> submit(Runnable runnable)
    {
        return submit(runnable, null);
    }


    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> callables)
            throws InterruptedException
    {
        ImmutableList.Builder<Future<T>> resultBuilder = ImmutableList.builder();
        for (Callable<T> callable : callables) {
            try {
                resultBuilder.add(Futures.immediateFuture(callable.call()));
            }
            catch (Exception e) {
                resultBuilder.add(Futures.<T>immediateFailedFuture(e));
            }
        }
        return resultBuilder.build();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> callables, long l, TimeUnit timeUnit)
            throws InterruptedException
    {
        return invokeAll(callables);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> callables)
            throws InterruptedException, ExecutionException
    {
        Preconditions.checkNotNull(callables, "callables is null");
        Preconditions.checkArgument(!callables.isEmpty(), "callables is empty");
        try {
            return callables.iterator().next().call();
        }
        catch (Exception e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> callables, long l, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException
    {
        return invokeAny(callables);
    }

    /**
     * Advance time by the given quantum.
     *
     * Scheduled tasks due for execution will be executed in the caller's thread.
     *
     * @param quantum  the amount of time to advance
     * @param timeUnit the unit of the quantum amount
     */
    public void elapseTime(long quantum, TimeUnit timeUnit)
    {
        if (isShutdown) {
            throw new IllegalStateException("Trying to elapse time after shutdown");
        }
        elapseTime(millis(quantum, timeUnit));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable runnable, long l, TimeUnit timeUnit)
    {
        SerialScheduledFuture<?> future = new SerialScheduledFuture<Void>(new FutureTask<Void>(runnable, null), millis(l, timeUnit));
        tasks.add(future);
        return future;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> vCallable, long l, TimeUnit timeUnit)
    {
        SerialScheduledFuture<V> future = new SerialScheduledFuture<V>(new FutureTask<V>(vCallable), millis(l, timeUnit));
        tasks.add(future);
        return future;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long l, long l1, TimeUnit timeUnit)
    {
        SerialScheduledFuture<?> future = new RecurringRunnableSerialScheduledFuture<Void>(runnable, null, millis(l, timeUnit), millis(l1, timeUnit));
        tasks.add(future);
        return future;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable runnable, long l, long l1, TimeUnit timeUnit)
    {
        SerialScheduledFuture<?> future = new RecurringRunnableSerialScheduledFuture<Void>(runnable, null, millis(l, timeUnit), millis(l1, timeUnit));
        tasks.add(future);
        return future;
    }

    class SerialScheduledFuture<T>
        implements ScheduledFuture<T>
    {
        long remainingDelayMillis;
        FutureTask<T> task;

        public SerialScheduledFuture(FutureTask<T> task, long delayMillis)
        {
            this.task = task;
            this.remainingDelayMillis = delayMillis;
        }

        public long remainingMillis()
        {
            return remainingDelayMillis;
        }

        // wind time off the clock, return the amount of used time in millis
        public long elapseTime(long quantumMillis)
        {
            if (task.isDone() || task.isCancelled()) {
                return 0;
            }

            if (remainingDelayMillis <= quantumMillis) {
                task.run();
                return remainingDelayMillis;
            }

            remainingDelayMillis -= quantumMillis;
            return quantumMillis;
        }

        public boolean isRecurring()
        {
            return false;
        }

        public void restartDelayTimer()
        {
            throw new UnsupportedOperationException("Can't restart a non-recurring task");
        }

        @Override
        public long getDelay(TimeUnit timeUnit)
        {
            return timeUnit.convert(remainingDelayMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed delayed)
        {
            if (delayed instanceof SerialScheduledFuture) {
                SerialScheduledFuture other = (SerialScheduledFuture) delayed;
                return Longs.compare(this.remainingDelayMillis, other.remainingDelayMillis);
            }
            return Longs.compare(this.remainingMillis(), delayed.getDelay(TimeUnit.MILLISECONDS));
        }

        @Override
        public boolean cancel(boolean b)
        {
            return task.cancel(b);
        }

        @Override
        public boolean isCancelled()
        {
            return task.isCancelled();
        }

        @Override
        public boolean isDone()
        {
            return task.isDone();
        }

        @Override
        public T get()
                throws InterruptedException, ExecutionException
        {
            if (isCancelled()) {
                throw new CancellationException();
            }

            if (!isDone()) {
                throw new IllegalStateException("Called get() before result was available in SerialScheduledFuture");
            }

            return task.get();
        }

        @Override
        public T get(long l, TimeUnit timeUnit)
                throws InterruptedException, ExecutionException, TimeoutException
        {
            return get();
        }
    }

    class RecurringRunnableSerialScheduledFuture<T>
        extends SerialScheduledFuture<T>
    {
        private final long recurringDelayMillis;
        private final Runnable runnable;
        private final T value;

        RecurringRunnableSerialScheduledFuture(Runnable runnable, T value, long initialDelayMillis, long recurringDelayMillis)
        {
            super(new FutureTask<T>(runnable, value), initialDelayMillis);
            this.runnable = runnable;
            this.value = value;
            this.recurringDelayMillis = recurringDelayMillis;
        }

        @Override
        public boolean isRecurring()
        {
            return true;
        }

        @Override
        public void restartDelayTimer()
        {
            task = new FutureTask<T>(runnable, value);
            remainingDelayMillis = recurringDelayMillis;
        }
    }

    private void elapseTime(long quantum)
    {
        List<SerialScheduledFuture<?>> toRequeue = Lists.newArrayList();

        while (tasks.peek() != null) {
            SerialScheduledFuture<?> current = tasks.poll();

            if (current.isCancelled()) {
                // Drop cancelled tasks
                continue;
            }

            if (current.isDone()) {
                // This isn't right - there shouldn't be done tasks in the queue
                throw new IllegalStateException("Found a done task in the queue (contrary to expectation)");
            }

            // Try to elapse the time quantum off the current item
            long used = current.elapseTime(quantum);

            // If the item isn't done yet, we'll need to put it back in the queue
            if (!current.isDone()) {
                toRequeue.add(current);
                continue;
            }

            if (used < quantum) {
                // Partially used the quantum. Elapse the used portion off the rest of the queue so that we can reinsert
                // this item in its correct spot (if necessary) before continuing with the rest of the quantum. This is
                // because tasks may execute more than once during a single call to elapse time.
                elapseTime(used);
                rescheduleTaskIfRequired(tasks, current);
                quantum -= used;
            }
            else {
                // Completely used the quantum, once we're done with this pass through the queue, may want need to add it back
                rescheduleTaskIfRequired(toRequeue, current);
            }
        }
        for (SerialScheduledFuture<?> future : toRequeue) {
            tasks.add(future);
        }
    }

    private static void rescheduleTaskIfRequired(Collection<SerialScheduledFuture<?>> tasks, SerialScheduledFuture<?> task)
    {
        if (task.isRecurring()) {
            task.restartDelayTimer();
            tasks.add(task);
        }
    }

    private static long millis(long quantum, TimeUnit timeUnit)
    {
        return TimeUnit.MILLISECONDS.convert(quantum, timeUnit);
    }
}
