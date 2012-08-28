package com.proofpoint.event.client;

import com.proofpoint.event.api.Event;
import com.proofpoint.event.api.EventSink;
import com.proofpoint.log.Logger;

public class EventLoggerSink
    implements EventSink
{
    private final Logger logger = Logger.get("EventLogger");

    public void post(Event event)
    {
        logger.debug(event.toString());
    }
}
