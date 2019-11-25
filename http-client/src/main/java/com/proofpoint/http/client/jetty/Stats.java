/*
 * Copyright 2018 Proofpoint, Inc.
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
package com.proofpoint.http.client.jetty;

import com.google.auto.value.AutoValue;
import com.proofpoint.http.client.RequestStats;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.weakref.jmx.Nested;

@AutoValue
abstract class Stats
    extends RequestStats
{
    @Nested
    abstract IoPoolStats getIoPool();

    static Stats stats(QueuedThreadPool executor) {
        return new AutoValue_Stats(new IoPoolStats(executor));
    }
}
