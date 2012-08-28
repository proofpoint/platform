package com.proofpoint.event.client;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.event.api.Event;
import com.proofpoint.event.api.EventCategory;
import com.proofpoint.event.api.EventSource;
import com.proofpoint.event.api.EventSourceBase;
import com.proofpoint.event.api.ExceptionEvent;
import com.proofpoint.event.api.ExceptionEventSource;
import com.proofpoint.event.api.OperationEvent;
import com.proofpoint.event.api.OperationEventSource;
import com.proofpoint.event.api.SimpleEvent;
import com.proofpoint.event.api.SimpleEventSource;
import com.proofpoint.units.Duration;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

public class TestGenericEvents
    implements Module
{
    private InMemoryEventSink eventSink;

    public void configure(Binder binder)
    {
        EventBinder.eventBinder(binder).bindEventClient(SimpleEvent.class);
        EventBinder.eventBinder(binder).bindEventClient(ExceptionEvent.class);
        EventBinder.eventBinder(binder).bindEventClient(OperationEvent.class);

        binder.bind(InMemoryEventSink.class).in(Scopes.SINGLETON);
        EventBinder.eventBinder(binder).bindEventSink(InMemoryEventSink.class);

        binder.bind(EventLoggerSink.class).in(Scopes.SINGLETON);
        EventBinder.eventBinder(binder).bindEventSink(EventLoggerSink.class);

        binder.bind(EventDispatcher.class).to(EventDispatcherImpl.class).in(Scopes.SINGLETON);
        binder.requestStaticInjection(EventSourceBase.class);
    }

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Injector injector = Guice.createInjector(this);
        eventSink = injector.getInstance(InMemoryEventSink.class);
    }

    @Test
    public void basicTest()
    {
        debugEvent.raise("foo");
        operationEvent.raise(true, 200, Duration.valueOf("3s"), "bla");
        errorEvent.raise(new Exception("phew"));

        List<Event> events = eventSink.getEvents();
        Assert.assertEquals(events.size(), 3);
        Assert.assertEquals(events.get(0).getClass(), SimpleEvent.class);
        Assert.assertEquals(events.get(1).getClass(), OperationEvent.class);
        Assert.assertEquals(events.get(2).getClass(), ExceptionEvent.class);
    }

    @EventSource(name = "test:type=sample,name=debug", categories = { EventCategory.DEBUG }, description = "Debug event")
    private static SimpleEventSource debugEvent = SimpleEventSource.create();

    @EventSource(name = "test:type=sample,name=operation", categories = { EventCategory.INFO }, description = "Operation event")
    private static OperationEventSource operationEvent = OperationEventSource.create();

    @EventSource(name = "test:type=sample,name=error", categories = { EventCategory.ERROR }, description = "Error event")
    private static ExceptionEventSource errorEvent = ExceptionEventSource.create();
}
