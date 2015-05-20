/*
 * Copyright 2010 Proofpoint, Inc.
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

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import org.weakref.jmx.Managed;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Map;

import static com.google.common.base.Functions.toStringFunction;

public class LoggingMBean
{
    private final Logging logging;

    @Inject
    public LoggingMBean(Logging logging)
    {
        this.logging = logging;
    }

    @Managed
    @SuppressWarnings("MethodMayBeStatic")
    public String getLevel(String loggerName)
    {
        return logging.getLevel(loggerName).toString();
    }

    @Managed
    @SuppressWarnings("MethodMayBeStatic")
    public void setLevel(String loggerName, String newLevel)
    {
        logging.setLevel(loggerName, Level.valueOf(newLevel.toUpperCase(Locale.US)));
    }

    @Managed
    public String getRootLevel()
    {
        return logging.getRootLevel().toString();
    }

    @Managed
    public void setRootLevel(String newLevel)
    {
        logging.setRootLevel(Level.valueOf(newLevel.toUpperCase(Locale.US)));
    }

    @Managed
    @SuppressWarnings("MethodMayBeStatic")
    public Map<String, String> getAllLevels()
    {
        return ImmutableSortedMap.copyOf(Maps.transformValues(logging.getAllLevels(), toStringFunction()));
    }
}
