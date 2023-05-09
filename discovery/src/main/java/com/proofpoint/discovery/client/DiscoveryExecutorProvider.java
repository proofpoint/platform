/*
 * Copyright 2022 Proofpoint, Inc.
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
package com.proofpoint.discovery.client;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Provider;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static com.google.common.base.Preconditions.checkState;
import static com.proofpoint.concurrent.Threads.daemonThreadsNamed;

public class DiscoveryExecutorProvider
        implements Provider<ScheduledExecutorService>
{
    private ScheduledExecutorService executor;

    @Override
    public ScheduledExecutorService get()
    {
        checkState(executor == null, "provider already used");
        executor = new ScheduledThreadPoolExecutor(5, daemonThreadsNamed("Discovery-%s"));
        return executor;
    }

    @PreDestroy
    public void destroy()
    {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
