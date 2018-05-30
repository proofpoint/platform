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

package com.proofpoint.log;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

class LogLevelsFileMonitor {
    private static final Logger logger = Logger.get(LogLevelsFileMonitor.class);

    private final Logging logging;
    private final Timer timer = new Timer("Scan thread", true);
    private final LoggingConfiguration config;

    protected LogLevelsFileMonitor(LoggingConfiguration logConfig) {
        logging =Logging.initialize();
        config = logConfig;
        timer.scheduleAtFixedRate(new ScanTask(), config.getScanPeriodInMiliseconds(), config.getScanPeriodInMiliseconds() );

    }


    private class ScanTask extends TimerTask {
        private long previousLastmod;
        public void run() {
            String fileName = config.getLevelsFile();
            if (fileName != null) {
                File levelsFile = new File(fileName);
                long lastModified = levelsFile.lastModified();
                if (lastModified > previousLastmod) {
                    try {
                        logging.setLevels(levelsFile);

                    } catch(IOException e) {
                        logger.error(e, "Problem reading log levels file.  Skipping this update");
                    }
                    finally {
                        previousLastmod = lastModified;
                    }
                }
            }
        }

    }
}
