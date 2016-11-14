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
package com.proofpoint.bootstrap;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;
import com.proofpoint.units.Duration;

import static java.util.concurrent.TimeUnit.SECONDS;

public class LifeCycleConfig
{
    private Duration stopTrafficDelay = new Duration(0, SECONDS);

    @Config("lifecycle.stop-traffic.delay")
    @ConfigDescription("Amount of time to wait on shutdown after remove from load balancers before stop taking requests")
    public LifeCycleConfig setStopTrafficDelay(Duration stopTrafficDelay)
    {
        this.stopTrafficDelay = stopTrafficDelay;
        return this;
    }

    public Duration getStopTrafficDelay()
    {
        return stopTrafficDelay;
    }
}
