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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestShutdownMonitor
{
    @Test
    public void testInitialState()
    {
        ShutdownMonitor shutdownMonitor = new ShutdownMonitor();
        assertNull(shutdownMonitor.getShutdownState());
    }

    @Test
    public void testShutdown()
    {
        ShutdownMonitor shutdownMonitor = new ShutdownMonitor();
        shutdownMonitor.stopTraffic();
        assertEquals(shutdownMonitor.getShutdownState(), "Server is shutting down");
    }
}
