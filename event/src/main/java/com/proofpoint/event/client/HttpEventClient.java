package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Request;
import com.ning.http.client.Request.EntityWriter;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.proofpoint.discovery.client.HttpServiceSelector;
import com.proofpoint.discovery.client.ServiceType;
import com.proofpoint.log.Logger;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.module.SimpleModule;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class HttpEventClient implements EventClient
{
    private static final Logger log = Logger.get(HttpEventClient.class);

    private final ObjectMapper objectMapper;
    private final HttpServiceSelector serviceSelector;
    private final AsyncHttpClient client;
    private final Set<Class<?>> registeredTypes;
    private final AtomicBoolean eventServiceIsUp = new AtomicBoolean(true);

    @Inject
    public HttpEventClient(HttpEventClientConfig config,
            @ServiceType("event") HttpServiceSelector serviceSelector,
            ObjectMapper objectMapper,
            @ForEventClient AsyncHttpClient client,
            Set<EventTypeMetadata<?>> eventTypes)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(serviceSelector, "serviceSelector is null");
        Preconditions.checkNotNull(objectMapper, "objectMapper is null");
        Preconditions.checkNotNull(client, "client is null");
        Preconditions.checkNotNull(eventTypes, "types is null");

        this.serviceSelector = serviceSelector;
        this.objectMapper = objectMapper;
        this.client = client;

        ImmutableSet.Builder<Class<?>> typeRegistrations = ImmutableSet.builder();

        SimpleModule eventModule = new SimpleModule("MyModule", new Version(1, 0, 0, null));
        for (EventTypeMetadata<?> eventType : eventTypes) {
            eventModule.addSerializer(EventJsonSerializer.createEventJsonSerializer(eventType, config.getJsonVersion()));
            typeRegistrations.add(eventType.getEventClass());
        }
        objectMapper.registerModule(eventModule);
        this.registeredTypes = typeRegistrations.build();
    }

    @Override
    public <T> Future<Void> post(T... event)
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(event, "event is null");
        return post(Arrays.asList(event));
    }

    @Override
    public <T> Future<Void> post(final Iterable<T> events)
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(events, "eventsSupplier is null");
        return post(new EventGenerator<T>()
        {
            @Override
            public void generate(EventPoster<T> eventPoster)
                    throws IOException
            {
                for (T event : events) {
                    eventPoster.post(event);
                }
            }
        });
    }

    @Override
    public <T> Future<Void> post(EventGenerator<T> eventGenerator)
    {
        Preconditions.checkNotNull(eventGenerator, "eventGenerator is null");

        // todo this doesn't really work due to returning the future which can fail without being retried
        // also this code tries all servers instead of a fixed number
        for (URI uri : serviceSelector.selectHttpService()) {
            try {
                String uriString = uri.toString();
                if (eventServiceIsUp.compareAndSet(false, true)) {
                    log.info("Posting to event collection service");
                }
                Request request = new RequestBuilder("POST")
                        .setUrl(uriString)
                        .setHeader("Content-Type", "application/json")
                        .setBody(new JsonEntityWriter<T>(objectMapper, registeredTypes, eventGenerator))
                        .build();
                return new FutureResponse(client.prepareRequest(request).execute(), uriString);
            }
            catch (Exception e) {
                log.warn(e, "Posting event failed");
            }
        }

        if (eventServiceIsUp.compareAndSet(true, false)) {
            log.warn("No event collection service");
        }
        else {
            log.debug("No event collection service");
        }
        return Futures.immediateFuture(null);
    }

    private static class FutureResponse implements Future<Void>, Runnable
    {
        private static final Executor statusLoggerExecutor = new Executor()
        {
            @Override
            public void execute(Runnable command)
            {
                command.run();
            }
        };
        
        private final ListenableFuture<Response> delegate;
        private final String uri;

        public FutureResponse(ListenableFuture<Response> delegate, String uri)
        {
            this.delegate = delegate;
            this.uri = uri;
            delegate.addListener(this, statusLoggerExecutor);
        }

        @Override
        public Void get()
                throws InterruptedException, ExecutionException
        {
            delegate.get();
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException
        {
            delegate.get(timeout, unit);
            return null;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled()
        {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone()
        {
            return delegate.isDone();
        }
        
        //Invoked as a result of ListenableFuture.addListener(...)
        //Expected to execute quickly on a Future<Response> that has completed.
        @Override
        public void run()
        {
            try {
                Response response = delegate.get();
                int statusCode = response.getStatusCode();
                if (statusCode != HttpServletResponse.SC_OK && statusCode != HttpServletResponse.SC_ACCEPTED) {
                    try {
                        log.warn("Posting event to %s failed: status_code=%d status_line=%s body=%s", uri, statusCode, response.getStatusText(), response.getResponseBody());
                    }
                    catch (IOException bodyError) {
                        log.warn("Posting event to %s failed: status_code=%d status_line=%s error=%s", uri, statusCode, response.getStatusText(), bodyError.getMessage());
                    }
                }
            }
            catch (ExecutionException executionException) {
                if (log.isDebugEnabled()) {
                    log.warn(executionException, "Posting event to %s failed", uri);
                }
                else {
                    log.warn("Posting event to %s failed: %s", uri, executionException.getCause().getMessage());
                }
            }
            catch (Exception unexpectedError) {
                log.warn(unexpectedError, "Posting event to %s failed", uri);
            }
        }

        @Override
        public String toString()
        {
            return "Event post to " + uri + (isDone() ? " (done)" : "");
        }
    }

    private static class JsonEntityWriter<T> implements EntityWriter
    {
        private final ObjectMapper objectMapper;
        private final Set<Class<?>> registeredTypes;
        private final EventGenerator<T> events;

        public JsonEntityWriter(ObjectMapper objectMapper, Set<Class<?>> registeredTypes, EventGenerator<T> events)
        {
            Preconditions.checkNotNull(objectMapper, "objectMapper is null");
            Preconditions.checkNotNull(registeredTypes, "registeredTypes is null");
            Preconditions.checkNotNull(events, "events is null");
            this.objectMapper = objectMapper;
            this.registeredTypes = registeredTypes;
            this.events = events;
        }

        @Override
        public void writeEntity(final OutputStream out)
                throws IOException
        {
            JsonFactory jsonFactory = objectMapper.getJsonFactory();
            final JsonGenerator jsonGenerator = jsonFactory.createJsonGenerator(out, JsonEncoding.UTF8);

            jsonGenerator.writeStartArray();

            events.generate(new EventPoster<T>()
            {

                @Override
                public void post(T event)
                        throws IOException
                {
                    if (!registeredTypes.contains(event.getClass())) {
                        throw new RuntimeException(
                                String.format("Event type %s has not been registered as an event",
                                        event.getClass().getSimpleName()));
                    }
                    jsonGenerator.writeObject(event);
                }
            });

            jsonGenerator.writeEndArray();
            jsonGenerator.flush();
        }
    }
}
