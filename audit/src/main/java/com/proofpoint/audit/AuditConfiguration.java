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
package com.proofpoint.audit;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.DataSize.Unit;

public class AuditConfiguration
{
    private boolean logEnable = true;
    private String logPath = "var/log/audit.log";
    private DataSize maxSegmentSize = new DataSize(100, Unit.MEGABYTE);
    private int maxHistory = 30;
    private DataSize maxTotalSize = new DataSize(1, Unit.GIGABYTE);


    public boolean isLogEnable()
    {
        return logEnable;
    }

    @Config("audit.log.enable")
    public AuditConfiguration setLogEnable(boolean logEnable)
    {
        this.logEnable = logEnable;
        return this;
    }

    public String getLogPath()
    {
        return logPath;
    }

    @Config("audit.log.path")
    public AuditConfiguration setLogPath(String logPath)
    {
        this.logPath = logPath;
        return this;
    }

    public DataSize getMaxSegmentSize()
    {
        return maxSegmentSize;
    }

    @Config("audit.log.max-size")
    @ConfigDescription("Maximum size of a single audit log file")
    public AuditConfiguration setMaxSegmentSize(DataSize maxSegmentSize)
    {
        this.maxSegmentSize = maxSegmentSize;
        return this;
    }

    public int getMaxHistory()
    {
        return maxHistory;
    }

    @Config("audit.log.max-history")
    public AuditConfiguration setMaxHistory(int maxHistory)
    {
        this.maxHistory = maxHistory;
        return this;
    }

    public DataSize getMaxTotalSize()
    {
        return maxTotalSize;
    }

    @Config("audit.log.max-total-size")
    @ConfigDescription("Maximum size of all archived log files")
    public AuditConfiguration setMaxTotalSize(DataSize maxTotalSize)
    {
        this.maxTotalSize = maxTotalSize;
        return this;
    }
}

