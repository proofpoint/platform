package com.proofpoint.http.server;

import com.proofpoint.units.Duration;
import org.eclipse.jetty.server.Request;
import org.mockito.MockedStatic;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.Map;

import static com.proofpoint.http.server.RequestTimingEventHandler.REQUEST_ENDED_ATTRIBUTE;
import static com.proofpoint.http.server.RequestTimingEventHandler.REQUEST_HANDLE_ENDED_ATTRIBUTE;
import static com.proofpoint.http.server.RequestTimingEventHandler.REQUEST_HANDLE_STARTED_ATTRIBUTE;
import static com.proofpoint.http.server.RequestTimingEventHandler.RESPONSE_CONTENT_WRITE_BEGIN_ATTRIBUTE;
import static com.proofpoint.http.server.RequestTimingEventHandler.RESPONSE_CONTENT_WRITE_END_ATTRIBUTE;
import static com.proofpoint.http.server.RequestTimingEventHandler.timings;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class TestRequestTimingEventHandler
{
    private static final Object MARKER = new Object();

    @Test
    public void testExtractTimings()
    {
        try (MockedStatic<Request> ignored = mockStatic(Request.class, RETURNS_DEEP_STUBS)) {
            Request request = mock(Request.class, RETURNS_DEEP_STUBS);
            Instant now = Instant.now();
            long startNanos = MILLISECONDS.toNanos(now.toEpochMilli());

            when(Request.getTimeStamp(request)).thenReturn(now.toEpochMilli());
            when(request.getBeginNanoTime()).thenReturn(startNanos);
            when(request.getHeadersNanoTime()).thenReturn(startNanos + 100);

            when(request.asAttributeMap()).thenReturn(Map.of(
                    REQUEST_HANDLE_STARTED_ATTRIBUTE, startNanos + 110,
                    REQUEST_ENDED_ATTRIBUTE, startNanos + 160,
                    RESPONSE_CONTENT_WRITE_BEGIN_ATTRIBUTE + "." + (startNanos + 200), MARKER,
                    RESPONSE_CONTENT_WRITE_END_ATTRIBUTE + "." + (startNanos + 210), MARKER,
                    RESPONSE_CONTENT_WRITE_BEGIN_ATTRIBUTE + "." + (startNanos + 220), MARKER,
                    RESPONSE_CONTENT_WRITE_END_ATTRIBUTE + "." + (startNanos + 250), MARKER,
                    REQUEST_HANDLE_ENDED_ATTRIBUTE, startNanos + 500));

            RequestTiming timings = timings(request);

            assertEquals(timings.requestStarted(), now.truncatedTo(MILLIS));
            assertEquals(timings.dispatchToHandling(), Duration.valueOf("10.00ns"));
            assertEquals(timings.dispatchToRequestEnd(), Duration.valueOf("60.00ns"));
            assertEquals(timings.firstToLastResponseContent(), Duration.valueOf("50.00ns"));
            assertEquals(timings.timeToCompletion(), Duration.valueOf("500.00ns"));
        }
    }
}
