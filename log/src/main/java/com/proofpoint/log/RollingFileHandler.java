package com.proofpoint.log;

import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.encoder.EncoderBase;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.Duration;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static com.proofpoint.log.Logging.createFileAppender;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.ErrorManager.CLOSE_FAILURE;
import static java.util.logging.ErrorManager.FORMAT_FAILURE;
import static java.util.logging.ErrorManager.WRITE_FAILURE;

final class RollingFileHandler
        extends Handler
{
    private final Appender<String> fileAppender;

    RollingFileHandler(String filename, int maxHistory, int queueSize, DataSize maxFileSize, DataSize maxTotalSize)
    {
        setFormatter(new StaticFormatter());

        fileAppender = createFileAppender(filename, maxHistory, queueSize, new Duration(10, SECONDS), maxFileSize, maxTotalSize, new StringEncoder(), new ContextBase());
    }

    @Override
    public void publish(LogRecord record)
    {
        if (!isLoggable(record)) {
            return;
        }

        String message;
        try {
            message = getFormatter().format(record);
        }
        catch (Exception e) {
            // catch any exception to assure logging always works
            reportError(null, e, FORMAT_FAILURE);
            return;
        }

        try {
            fileAppender.doAppend(message);
        }
        catch (Exception e) {
            // catch any exception to assure logging always works
            reportError(null, e, WRITE_FAILURE);
        }
    }

    @Override
    public void flush()
    {
    }

    @Override
    public void close()
    {
        try {
            fileAppender.stop();
        }
        catch (Exception e) {
            // catch any exception to assure logging always works
            reportError(null, e, CLOSE_FAILURE);
        }
    }

    private static final class StringEncoder
            extends EncoderBase<String>
    {
        @Override
        public byte[] headerBytes()
        {
            return null;
        }

        @Override
        public byte[] encode(String event)
        {
            return event.getBytes(UTF_8);
        }

        @Override
        public byte[] footerBytes()
        {
            return null;
        }
    }
}
