/*
 * Copyright 2017 Proofpoint, Inc.
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
package com.proofpoint.http.client;

import jakarta.inject.Provider;

import java.util.concurrent.ScheduledExecutorService;

import static com.proofpoint.concurrent.Threads.daemonThreadsNamed;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

class RetryExecutorProvider
    implements Provider<ScheduledExecutorService>
{
    @Override
    public ScheduledExecutorService get()
    {
        return newSingleThreadScheduledExecutor(daemonThreadsNamed("http-client-balancer-retry"));
    }
}
