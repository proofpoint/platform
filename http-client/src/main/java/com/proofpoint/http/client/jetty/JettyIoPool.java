package com.proofpoint.http.client.jetty;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.Scheduler;

import java.io.Closeable;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

public class JettyIoPool
        implements Closeable
{
    private final String name;
    private final QueuedThreadPool executor;
    private final ByteBufferPool byteBufferPool;
    private final Scheduler scheduler;
    private final QueuedThreadPool bodyGeneratorExecutor;
    private final Scheduler bodyGeneratorScheduler;

    public JettyIoPool(String name, JettyIoPoolConfig config)
    {
        this.name = name;
        try {
            String baseName = "http-client-" + name;

            JettyThreadPool threadPool = jettyThreadPool(baseName, config.getMaxThreads(), config.getMinThreads());
            executor = threadPool;
            scheduler = threadPool.getScheduler();

            threadPool = jettyThreadPool("http-client-bodygenerator" + name, config.getBodyGeneratorMaxThreads(), config.getBodyGeneratorMinThreads());
            bodyGeneratorExecutor = threadPool;
            bodyGeneratorScheduler = threadPool.getScheduler();

            byteBufferPool = new MappedByteBufferPool();
        }
        catch (Exception e) {
            close();
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void close()
    {
        try {
            closeQuietly(executor);
        }
        finally {
            closeQuietly(scheduler);
        }
        try {
            closeQuietly(bodyGeneratorExecutor);
        }
        finally {
            closeQuietly(bodyGeneratorScheduler);
        }
    }

    private static void closeQuietly(LifeCycle service)
    {
        try {
            if (service != null) {
                service.stop();
            }
        }
        catch (Exception ignored) {
        }
    }

    public String getName()
    {
        return name;
    }

    public Executor getExecutor()
    {
        return executor;
    }

    public Executor getBodyGeneratorExecutor()
    {
        return bodyGeneratorExecutor;
    }

    public ByteBufferPool setByteBufferPool()
    {
        return byteBufferPool;
    }

    public Scheduler setScheduler()
    {
        return scheduler;
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("name", name)
                .toString();
    }

    // TODO: Jetty should have support for setting ThreadGroup
    private static class JettyThreadPool
            extends QueuedThreadPool
    {
        private final ThreadGroup threadGroup;
        private final Scheduler scheduler;

        private JettyThreadPool(String baseName, int maxThreads, int minThreads)
        {
            super(maxThreads, minThreads);
            this.threadGroup = new ThreadGroup(checkNotNull(baseName, "baseName is null"));
            scheduler = new JettyScheduler(threadGroup, baseName + "-scheduler");
        }

        public Scheduler getScheduler()
        {
            return scheduler;
        }

        @Override
        protected Thread newThread(Runnable runnable)
        {
            return new Thread(threadGroup, runnable);
        }
    }

    private static JettyThreadPool jettyThreadPool(String baseName, int maxThreads, int minThreads)
            throws Exception
    {
        JettyThreadPool threadPool = new JettyThreadPool(baseName, maxThreads, minThreads);
        threadPool.setName(baseName);
        threadPool.setDaemon(true);
        threadPool.start();
        threadPool.setStopTimeout(2000);
        threadPool.scheduler.start();
        return threadPool;
    }

    // TODO: Jetty should have support for setting ThreadGroup
    // forked from org.eclipse.jetty.util.thread.ScheduledExecutorScheduler
    private static class JettyScheduler
            extends AbstractLifeCycle
            implements Scheduler
    {
        private final String name;
        private final ThreadGroup threadGroup;
        private volatile ScheduledThreadPoolExecutor scheduler;

        JettyScheduler(ThreadGroup threadGroup, String name)
        {
            this.threadGroup = checkNotNull(threadGroup, "threadGroup is null");
            this.name = checkNotNull(name, "name is null");
        }

        @Override
        protected void doStart()
                throws Exception
        {
            scheduler = new ScheduledThreadPoolExecutor(1, new ThreadFactory()
            {
                @Override
                public Thread newThread(Runnable runnable)
                {
                    Thread thread = new Thread(threadGroup, runnable);
                    thread.setName(name + "-" + thread.getId());
                    thread.setDaemon(true);
                    return thread;
                }
            });
            scheduler.setRemoveOnCancelPolicy(true);
            super.doStart();
        }

        @Override
        protected void doStop()
                throws Exception
        {
            scheduler.shutdownNow();
            super.doStop();
            scheduler = null;
        }

        @Override
        public Task schedule(Runnable task, long delay, TimeUnit unit)
        {
            return new ScheduledFutureTask(scheduler.schedule(task, delay, unit));
        }

        private static class ScheduledFutureTask
                implements Task
        {
            private final ScheduledFuture<?> scheduledFuture;

            ScheduledFutureTask(ScheduledFuture<?> scheduledFuture)
            {
                this.scheduledFuture = checkNotNull(scheduledFuture, "scheduledFuture is null");
            }

            @Override
            public boolean cancel()
            {
                return scheduledFuture.cancel(false);
            }
        }
    }
}
