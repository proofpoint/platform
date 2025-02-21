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

import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AsyncAppenderBase;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.util.FileSize;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Multimap;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.proofpoint.configuration.PropertiesBuilder;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.Duration;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Multimaps.synchronizedMultimap;
import static com.proofpoint.log.Level.fromJulLevel;
import static com.proofpoint.units.DataSize.Unit.MEGABYTE;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Initializes the logging subsystem.
 * <p>
 * java.util.Logging, System.out and System.err are tunneled through the logging system.
 * <p>
 * System.out and System.err are assigned to loggers named "stdout" and "stderr", respectively.
 */
public class Logging
{
    private static final Logger log = Logger.get(Logging.class);
    private static final String ROOT_LOGGER_NAME = "";
    private static final java.util.logging.Logger ROOT = java.util.logging.Logger.getLogger("");
    private static final java.util.logging.Logger BOOTSTRAP_LOGGER = java.util.logging.Logger.getLogger("Bootstrap");

    private static final String TEMP_FILE_EXTENSION = ".tmp";
    private static final String LOG_FILE_EXTENSION = ".log";
    private static final FileSize BUFFER_SIZE_IN_BYTES = new FileSize(new DataSize(1, MEGABYTE).toBytes());

    private static Logging instance;

    // hard reference to loggers for which we set the level
    private final Map<String, java.util.logging.Logger> loggers = new ConcurrentHashMap<>();
    private final Multimap<java.util.logging.Logger, Handler> testingHandlers = synchronizedMultimap(ArrayListMultimap.create());

    @GuardedBy("this")
    private OutputStreamHandler consoleHandler;

    /**
     * Sets up default logging:
     * <p>
     * - INFO level
     * - Log entries are written to stderr
     *
     * @return the logging system singleton
     */
    @SuppressFBWarnings("MS_EXPOSE_REP")
    public static synchronized Logging initialize()
    {
        if (instance == null) {
            instance = new Logging();
        }

        return instance;
    }

    private Logging()
    {
        ROOT.setLevel(Level.INFO.toJulLevel());
        for (Handler handler : ROOT.getHandlers()) {
            ROOT.removeHandler(handler);
        }

        rewireStdStreams();
    }

    // Protect against finalizer attacks, as constructor can throw exception.
    @SuppressWarnings("deprecation")
    @Override
    protected final void finalize()
    {
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    private void rewireStdStreams()
    {
        logConsole(new NonCloseableOutputStream(System.err));
        log.info("Logging to stderr");

        redirectStdStreams();
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    private static void redirectStdStreams()
    {
        try {
            System.setOut(new PrintStream(new LoggingOutputStream(Logger.get("stdout")), true, "UTF-8"));
            System.setErr(new PrintStream(new LoggingOutputStream(Logger.get("stderr")), true, "UTF-8"));
        }
        catch (UnsupportedEncodingException ignored) {
        }
    }

    private synchronized void logConsole(OutputStream stream)
    {
        consoleHandler = new OutputStreamHandler(stream);
        ROOT.addHandler(consoleHandler);
    }

    public synchronized void disableConsole()
    {
        log.info("Disabling stderr output");
        ROOT.removeHandler(consoleHandler);
        consoleHandler = null;
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void logToFile(String logPath, int maxHistory, int queueSize, DataSize maxSize, DataSize maxTotalSize)
    {
        log.info("Logging to %s", logPath);

        RollingFileHandler rollingFileHandler = new RollingFileHandler(logPath, maxHistory, queueSize, maxSize, maxTotalSize);
        ROOT.addHandler(rollingFileHandler);
    }

    /**
     * Add a {@link LogTester} to the logger named after a class' fully qualified name.
     *
     * @param clazz the class
     * @param logTester the LogTester
     */
    @VisibleForTesting
    public static void addLogTester(Class<?> clazz, LogTester logTester)
    {
        addLogTester(clazz.getName(), logTester);
    }

    /**
     * Add a {@link LogTester} to a named logger. Intended for writing unit tests that verify logging.
     *
     * @param name the name of the logger
     * @param logTester the LogTester
     */
    @VisibleForTesting
    public static void addLogTester(String name, LogTester logTester)
    {
        checkState(instance != null, "Logging is not initialized");
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(name);
        Handler handler = new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                String message = record.getMessage();
                if (message.endsWith("\n")) {
                    message = message.substring(0, message.length() - 1);
                }
                logTester.log(fromJulLevel(record.getLevel()), message, Optional.ofNullable(record.getThrown()));
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
        instance.testingHandlers.put(logger, handler);
        logger.addHandler(handler);
    }

    /**
     * Remove all installed {@link LogTester}s
     */
    @VisibleForTesting
    public static void resetLogTesters()
    {
        if (instance == null) {
            return;
        }

        for (Entry<java.util.logging.Logger, Handler> entry : instance.testingHandlers.entries()) {
            entry.getKey().removeHandler(entry.getValue());
        }
        instance.testingHandlers.clear();
    }

    public static <T> Appender<T> createFileAppender(String logPath, int maxHistory, DataSize maxFileSize, DataSize maxTotalSize, Encoder<T> encoder, Context context) {
        return createFileAppender(logPath, maxHistory, 0, new Duration(0, SECONDS), maxFileSize, maxTotalSize, encoder, context);
    }

    public static <T> Appender<T> createFileAppender(String logPath, int maxHistory, int queueSize, Duration flushInterval, DataSize maxFileSize, DataSize maxTotalSize, Encoder<T> encoder, Context context)
    {
        recoverTempFiles(logPath);

        RollingFileAppender<T> fileAppender;
        if (queueSize > 0) {
            fileAppender = new FlushingFileAppender<>(flushInterval);
            fileAppender.setBufferSize(BUFFER_SIZE_IN_BYTES);
            fileAppender.setImmediateFlush(false);
        }
        else {
            fileAppender = new RollingFileAppender<>();
        }
        SizeAndTimeBasedRollingPolicy<T> rollingPolicy = new SizeAndTimeBasedRollingPolicy<>();

        rollingPolicy.setContext(context);
        rollingPolicy.setFileNamePattern(logPath + "-%d{yyyy-MM-dd}.%i.log.gz");
        rollingPolicy.setMaxHistory(maxHistory);
        rollingPolicy.setTotalSizeCap(new FileSize(maxTotalSize.toBytes()));
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setMaxFileSize(new FileSize(maxFileSize.toBytes()));

        fileAppender.setContext(context);
        fileAppender.setFile(logPath);
        fileAppender.setAppend(true);
        fileAppender.setEncoder(encoder);
        fileAppender.setRollingPolicy(rollingPolicy);

        AsyncAppenderBase<T> asyncAppender = null;
        if (queueSize > 0) {
            asyncAppender = new AsyncAppenderBase<>();
            asyncAppender.setContext(context);
            asyncAppender.setQueueSize(queueSize);
            asyncAppender.addAppender(fileAppender);
        }

        rollingPolicy.start();
        fileAppender.start();
        if (queueSize > 0) {
            asyncAppender.start();
            return asyncAppender;
        }
        else {
            return fileAppender;
        }
    }

    public Level getRootLevel()
    {
        return getLevel(ROOT_LOGGER_NAME);
    }

    public void setRootLevel(Level newLevel)
    {
        setLevel(ROOT_LOGGER_NAME, newLevel);
    }

    public void setLevels(File file)
            throws IOException
    {
        setLevels(new PropertiesBuilder().withPropertiesFile(file.getPath()).throwOnError().getProperties());
    }

    @SuppressWarnings("MethodMayBeStatic")
    public Level getLevel(String loggerName)
    {
        return getEffectiveLevel(java.util.logging.Logger.getLogger(loggerName));
    }

    private static Level getEffectiveLevel(java.util.logging.Logger logger)
    {
        java.util.logging.Level level = logger.getLevel();
        if (level == null) {
            java.util.logging.Logger parent = logger.getParent();
            if (parent != null) {
                return getEffectiveLevel(parent);
            }
        }
        if (level == null) {
            return Level.OFF;
        }
        return fromJulLevel(level);
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void setLevel(String loggerName, Level level)
    {
        loggers.computeIfAbsent(loggerName, java.util.logging.Logger::getLogger)
                .setLevel(level.toJulLevel());
    }

    @SuppressWarnings("MethodMayBeStatic")
    public Map<String, Level> getAllLevels()
    {
        ImmutableSortedMap.Builder<String, Level> levels = ImmutableSortedMap.naturalOrder();
        for (String loggerName : Collections.list(LogManager.getLogManager().getLoggerNames())) {
            java.util.logging.Level level = java.util.logging.Logger.getLogger(loggerName).getLevel();
            if (level != null) {
                levels.put(loggerName, fromJulLevel(level));
            }
        }
        return levels.build();
    }

    public void setLevels(Map<String, String> properties)
    {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String loggerName = entry.getKey().toString();
            Level level = Level.valueOf(entry.getValue().toString().toUpperCase(Locale.US));

            setLevel(loggerName, level);
        }
    }

    private static void recoverTempFiles(String logPath)
    {
        // Logback has a tendency to leave around temp files if it is interrupted.
        // These .tmp files are log files that are about to be compressed.
        // This method recovers them so that they aren't orphaned.

        File logPathFile = new File(logPath).getParentFile();
        File[] tempFiles = logPathFile.listFiles((dir, name) -> name.endsWith(TEMP_FILE_EXTENSION));

        if (tempFiles == null) {
            return;
        }

        for (File tempFile : tempFiles) {
            String newName = tempFile.getName().substring(0, tempFile.getName().length() - TEMP_FILE_EXTENSION.length());
            File newFile = new File(tempFile.getParent(), newName + LOG_FILE_EXTENSION);

            if (!tempFile.renameTo(newFile)) {
                log.warn("Could not rename temp file [%s] to [%s]", tempFile, newFile);
            }
        }
    }

    public void configure(LoggingConfiguration config)
            throws IOException
    {
        if (config.getLogPath() == null && !config.isConsoleEnabled()) {
            throw new IllegalArgumentException("No log file is configured (log.path) and logging to console is disabled (log.enable-console)");
        }

        if (config.getLogPath() != null) {
            logToFile(config.getLogPath(), config.getMaxHistory(), config.getQueueSize(), config.getMaxSegmentSize(), config.getMaxTotalSize());
        }

        if (!config.isConsoleEnabled()) {
            disableConsole();
        }

        if (config.getLevelsFile() != null) {
            setLevels(new File(config.getLevelsFile()));
        }

        if (config.getBootstrapLogPath() != null) {
            setupBootstrapLog(config.getBootstrapLogPath());
        }
    }

    private static void setupBootstrapLog(String logPath)
            throws IOException
    {
        Path path = Paths.get(logPath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        BufferedWriter writer = Files.newBufferedWriter(path, WRITE, CREATE, TRUNCATE_EXISTING);
        BOOTSTRAP_LOGGER.addHandler(new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                try {
                    writer.write(record.getMessage());
                    writer.newLine();
                    writer.flush();
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void flush()
            {
                try {
                    writer.flush();
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void close()
            {
                try {
                    writer.close();
                }
                catch (IOException ignored) {
                }
            }
        });
    }

    public static void addShutdownLatchToWaitFor(CountDownLatch latch)
    {
        LogManager logManager = LogManager.getLogManager();
        if (logManager instanceof ShutdownWaitingLogManager) {
            ((ShutdownWaitingLogManager) logManager).addWaitFor(latch);
        } else {
            log.warn("LogManager is not a ShutdownWaitingLogManager, so shutdown hooks might not be able to log. Please run java with -Djava.util.logging.manager=%s",
                    ShutdownWaitingLogManager.class.getTypeName());
        }
    }

    private static class FlushingFileAppender<T>
            extends RollingFileAppender<T>
    {
        private final AtomicLong lastFlushed = new AtomicLong(System.nanoTime());
        private final long flushIntervalNanos;

        private FlushingFileAppender(Duration flushInterval)
        {
            this.flushIntervalNanos = flushInterval.roundTo(NANOSECONDS);
        }

        @Override
        protected void subAppend(T event)
        {
            super.subAppend(event);

            long now = System.nanoTime();
            long last = lastFlushed.get();
            if (((now - last) > flushIntervalNanos) && lastFlushed.compareAndSet(last, now)) {
                flush();
            }
        }

        @SuppressWarnings("Duplicates")
        private void flush()
        {
            try {
                getOutputStream().flush();
            }
            catch (IOException e) {
                started = false;
                addStatus(new ErrorStatus("IO failure in appender", this, e));
            }
        }
    }
}
