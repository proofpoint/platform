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

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;
import com.proofpoint.configuration.DefunctConfig;
import com.proofpoint.configuration.LegacyConfig;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.DataSize.Unit;

import javax.validation.constraints.Min;

@DefunctConfig("log.max-size-in-bytes")
public class LoggingConfiguration
{
    private boolean consoleEnabled = true;
    private String logPath = null;
    private String bootstrapLogPath = null;
    private DataSize maxSegmentSize = new DataSize(100, Unit.MEGABYTE);
    private int maxHistory = 30;
    private int queueSize = 0;
    private String levelsFile = null;
    private DataSize maxTotalSize = new DataSize(1, Unit.GIGABYTE);

    public boolean isConsoleEnabled()
    {
        return consoleEnabled;
    }

    @Config("log.enable-console")
    public LoggingConfiguration setConsoleEnabled(boolean consoleEnabled)
    {
        this.consoleEnabled = consoleEnabled;
        return this;
    }

    public String getLogPath()
    {
        return logPath;
    }

    @LegacyConfig("log.output-file")
    @Config("log.path")
    public LoggingConfiguration setLogPath(String logPath)
    {
        this.logPath = logPath;
        return this;
    }

    public String getBootstrapLogPath()
    {
        return bootstrapLogPath;
    }

    @Config("log.bootstrap.path")
    public LoggingConfiguration setBootstrapLogPath(String bootstrapLogPath)
    {
        this.bootstrapLogPath = bootstrapLogPath;
        return this;
    }

    public DataSize getMaxSegmentSize()
    {
        return maxSegmentSize;
    }

    @Config("log.max-size")
    @ConfigDescription("Maximum size of a single log file")
    public LoggingConfiguration setMaxSegmentSize(DataSize maxSegmentSize)
    {
        this.maxSegmentSize = maxSegmentSize;
        return this;
    }

    public int getMaxHistory()
    {
        return maxHistory;
    }

    @Config("log.max-history")
    public LoggingConfiguration setMaxHistory(int maxHistory)
    {
        this.maxHistory = maxHistory;
        return this;
    }

    @Min(0)
    public int getQueueSize()
    {
        return queueSize;
    }

    @Config("log.queue-size")
    public LoggingConfiguration setQueueSize(int queueSize)
    {
        this.queueSize = queueSize;
        return this;
    }

    public String getLevelsFile()
    {
        return levelsFile;
    }

    @Config("log.levels-file")
    public LoggingConfiguration setLevelsFile(String levelsFile)
    {
        this.levelsFile = levelsFile;
        return this;
    }

    public DataSize getMaxTotalSize()
    {
        return maxTotalSize;
    }

    @Config("log.max-total-size")
    @ConfigDescription("Maximum size of all archived log files")
    public LoggingConfiguration setMaxTotalSize(DataSize maxTotalSize)
    {
        this.maxTotalSize = maxTotalSize;
        return this;
    }
}

