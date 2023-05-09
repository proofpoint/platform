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
package com.proofpoint.reporting;

import com.google.inject.Inject;
import com.proofpoint.stats.CounterStat;
import jakarta.annotation.PreDestroy;
import org.weakref.jmx.Nested;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

class LogCounter
{
    private static final java.util.logging.Logger ROOT = java.util.logging.Logger.getLogger("");

    private final CounterStat logErrors = new CounterStat();
    private final Handler handler;

    @Inject
    LogCounter() {
        handler = new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                    logErrors.add(1);
                }
            }

            @Override
            public void flush()
            {
            }

            @Override
            public void close()
            {
            }
        };
        ROOT.addHandler(handler);
    }

    @Nested
    public CounterStat getLogErrors() {
        return logErrors;
    }

    @PreDestroy
    public void close() {
        ROOT.removeHandler(handler);
    }
}
