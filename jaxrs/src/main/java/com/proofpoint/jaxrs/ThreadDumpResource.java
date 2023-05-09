/*
 * Copyright 2016 Proofpoint, Inc.
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
package com.proofpoint.jaxrs;

import com.proofpoint.bootstrap.QuietMode;
import com.proofpoint.bootstrap.StopTraffic;
import com.proofpoint.log.Logger;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

@Path("/admin/jstack")
@AccessDoesNotRequireAuthentication
public class ThreadDumpResource
{
    private static final Logger log = Logger.get(ThreadDumpResource.class);
    private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();

    private final boolean quiet;

    @Inject
    public ThreadDumpResource(@QuietMode boolean quiet)
    {
        this.quiet = quiet;
    }

    @GET
    @Produces(TEXT_PLAIN)
    public String get()
    {
        StringBuilder sb = new StringBuilder();

        long[] deadlockedThreads = THREAD_MX_BEAN.findDeadlockedThreads();
        if (deadlockedThreads != null) {
            sb.append("Deadlock found:\n");
            for (ThreadInfo threadInfo : THREAD_MX_BEAN.getThreadInfo(deadlockedThreads, true, true)) {
                appendThreadInfo(sb, threadInfo);
            }
            sb.append("\nFull list of threads:\n");
        }

        for (ThreadInfo threadInfo : THREAD_MX_BEAN.getThreadInfo(THREAD_MX_BEAN.getAllThreadIds(), true, true)) {
            appendThreadInfo(sb, threadInfo);
        }
        return sb.toString();
    }

    private static void appendThreadInfo(StringBuilder sb, ThreadInfo threadInfo)
    {
        sb.append('"')
                .append(threadInfo.getThreadName())
                .append("\" #")
                .append(threadInfo.getThreadId());

        if (threadInfo.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (threadInfo.isInNative()) {
            sb.append(" (running in native)");
        }
        sb.append("\n   state: ")
                .append(threadInfo.getThreadState());
        String lockName = threadInfo.getLockName();
        if (lockName != null) {
            sb.append(" on ")
                    .append(lockName);
            String lockOwnerName = threadInfo.getLockOwnerName();
            if (lockOwnerName != null) {
                sb.append(" owned by ")
                        .append(threadInfo.getLockOwnerId())
                        .append(" (")
                        .append(lockOwnerName)
                        .append(")");
            }
        }

        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
        MonitorInfo[] monitors = threadInfo.getLockedMonitors();
        for (int i=0; i < stackTrace.length; ++i) {
            StackTraceElement element = stackTrace[i];
            sb.append("\n\tat ")
                        .append(element);
            for (MonitorInfo monitor : monitors) {
                if (monitor.getLockedStackDepth() == i) {
                    sb.append("\n\t  - locked ")
                            .append(monitor);
                }
            }
        }

        LockInfo[] lockedSynchronizers = threadInfo.getLockedSynchronizers();
        if (lockedSynchronizers.length != 0) {
            sb.append("\n\tLocked synchronizers:");
            for (LockInfo lockedSynchronizer : lockedSynchronizers) {
                sb.append("\n\t  - ")
                        .append(lockedSynchronizer);
            }
        }

        sb.append("\n\n");
    }

    @StopTraffic
    public void logThreadDump()
    {
        if (!quiet) {
            log.info("Thread dump at shutdown:\n%s", get());
        }
    }
}
