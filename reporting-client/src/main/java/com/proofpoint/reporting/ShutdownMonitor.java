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
package com.proofpoint.reporting;

import com.proofpoint.bootstrap.StopTraffic;

import java.util.concurrent.atomic.AtomicBoolean;

public class ShutdownMonitor
{
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    @HealthCheckRemoveFromRotation("server shutdown state")
    public String getShutdownState()
    {
        if (isShuttingDown.get()) {
            return "Server is shutting down";
        }
        return null;
    }

    @StopTraffic
    public void stopTraffic()
    {
        isShuttingDown.set(true);
    }
}
