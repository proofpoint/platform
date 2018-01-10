package com.proofpoint.http.client.jetty;

import com.proofpoint.reporting.Gauge;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

class IoPoolStats
{
    private final QueuedThreadPool executor;

    IoPoolStats(QueuedThreadPool executor)
    {
        this.executor = executor;
    }

    @Gauge
    public int getFreeThreadCount() {
        return (executor.getMaxThreads() - executor.getThreads()) + executor.getIdleThreads() ;
    }

}
