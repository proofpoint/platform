package com.proofpoint.event.client;

import java.util.concurrent.Executor;

import com.google.common.util.concurrent.ListenableFuture;
import com.proofpoint.log.Logger;

/**
 * Utility class to send the errors from EventClient.post(...) to a Logger
 */
public class EventResultLogger implements Runnable
{
    private static final Logger log = Logger.get(EventResultLogger.class);
    private static final Executor statusLoggerExecutor = new Executor()
    {
        @Override
        public void execute(Runnable command)
        {
            command.run();
        }
    };
    private final ListenableFuture<Void> eventFuture;

    public EventResultLogger(ListenableFuture<Void> eventFuture)
    {
        this.eventFuture = eventFuture;
        eventFuture.addListener(this, statusLoggerExecutor);
    }

    @Override
    public void run()
    {
        try {
            eventFuture.get();
        }
        catch (Exception err) {
            if (log.isDebugEnabled()) {
                log.warn(err, "Event post failed");
            }
            else {
                log.warn("Event post failed: %s", err.getMessage());
            }
        }
    }
}
