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
package com.proofpoint.log;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.logging.LogManager;

public class ShutdownWaitingLogManager
    extends LogManager
{
    private final Set<Thread> shutdownHooksToWaitFor = new HashSet<>();
    private final Set<CountDownLatch> latchesToWaitFor = new HashSet<>();

    @Override
    public void reset()
            throws SecurityException
    {
        try {
            synchronized (latchesToWaitFor) {
                for (CountDownLatch latch : latchesToWaitFor) {
                    latch.await();
                }
            }
            synchronized (shutdownHooksToWaitFor) {
                for (Thread thread : shutdownHooksToWaitFor) {
                    thread.join();
                }
                shutdownHooksToWaitFor.clear();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        super.reset();
    }

    public void addWaitFor(CountDownLatch latch) {
        synchronized (latchesToWaitFor) {
            latchesToWaitFor.add(latch);
        }
    }

    /**
     * @deprecated Use {@link #addWaitFor(CountDownLatch)} instead.
     */
    @Deprecated
    public void addWaitForShutdownHook(Thread shutdownHook)
    {
        synchronized (shutdownHooksToWaitFor) {
            shutdownHooksToWaitFor.add(shutdownHook);
        }
    }

    /**
     * @deprecated Use a {@link CountDownLatch} instead.
     */
    @Deprecated
    public void removeWaitForShutdownHook(Thread shutdownHook)
    {
        synchronized (shutdownHooksToWaitFor) {
            shutdownHooksToWaitFor.remove(shutdownHook);
        }
    }
}
