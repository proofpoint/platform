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
package com.proofpoint.event.client;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.net.MediaType;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.HttpServiceSelector;
import com.proofpoint.discovery.client.ServiceType;
import com.proofpoint.http.client.balancing.ServiceUnavailableException;
import com.proofpoint.http.client.AsyncHttpClient;
import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.tracetoken.TraceTokenManager;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.http.client.Request.Builder.preparePost;

public class HttpEventClient
        implements EventClient
{
    private static final Logger log = Logger.get(HttpEventClient.class);
    private static final MediaType MEDIA_TYPE_JSON = MediaType.create("application", "json");

    private final HttpServiceSelector serviceSelector;
    private final JsonEventWriter eventWriter;
    private final AsyncHttpClient httpClient;
    private final NodeInfo nodeInfo;
    private final TraceTokenManager traceTokenManager;

    @Inject
    public HttpEventClient(
            @ServiceType("collector") HttpServiceSelector serviceSelector,
            JsonEventWriter eventWriter,
            NodeInfo nodeInfo,
            @ForEventClient AsyncHttpClient httpClient,
            TraceTokenManager traceTokenManager)
    {
        this.serviceSelector = checkNotNull(serviceSelector, "serviceSelector is null");
        this.eventWriter = checkNotNull(eventWriter, "eventWriter is null");
        this.nodeInfo = checkNotNull(nodeInfo, "nodeInfo is null");
        this.httpClient = checkNotNull(httpClient, "httpClient is null");
        this.traceTokenManager = checkNotNull(traceTokenManager, "traceTokenManager is null");
    }

    @Flatten
    @Managed
    public RequestStats getStats()
    {
        return httpClient.getStats();
    }

    @SafeVarargs
    @Override
    public final <T> CheckedFuture<Void, RuntimeException> post(T... event)
            throws IllegalArgumentException
    {
        checkNotNull(event, "event is null");
        return post(Arrays.asList(event));
    }

    @Override
    public <T> CheckedFuture<Void, RuntimeException> post(final Iterable<T> events)
            throws IllegalArgumentException
    {
        checkNotNull(events, "eventsSupplier is null");
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
    public <T> CheckedFuture<Void, RuntimeException> post(EventGenerator<T> eventGenerator)
    {
        checkNotNull(eventGenerator, "eventGenerator is null");
        String token = traceTokenManager.getCurrentRequestToken();

        List<URI> uris = serviceSelector.selectHttpService();

        if (uris.isEmpty()) {
            return Futures.<Void, RuntimeException>immediateFailedCheckedFuture(new ServiceUnavailableException("type=[" + serviceSelector.getType() + "], pool=[" + serviceSelector.getPool() + "]"));
        }

        // todo this doesn't really work due to returning the future which can fail without being retried
        Request request = preparePost()
                .setUri(uris.get(0).resolve("/v2/event"))
                .setHeader("User-Agent", nodeInfo.getNodeId())
                .setHeader("Content-Type", MEDIA_TYPE_JSON.toString())
                .setBodyGenerator(new JsonEntityWriter<>(eventWriter, eventGenerator, token))
                .build();
        return httpClient.executeAsync(request, new EventResponseHandler(serviceSelector.getType(), serviceSelector.getPool()));
    }

    private static class JsonEntityWriter<T>
            implements BodyGenerator
    {
        private final JsonEventWriter eventWriter;
        private final EventGenerator<T> events;
        private final String token;

        public JsonEntityWriter(JsonEventWriter eventWriter, EventGenerator<T> events, @Nullable String token)
        {
            this.eventWriter = checkNotNull(eventWriter, "eventWriter is null");
            this.events = checkNotNull(events, "events is null");
            this.token = token;
        }


        @Override
        public void write(OutputStream out)
                throws Exception
        {
            eventWriter.writeEvents(events, token, out);
        }
    }

    private static class EventResponseHandler implements ResponseHandler<Void, RuntimeException>
    {
        private final String type;
        private final String pool;

        public EventResponseHandler(String type, String pool)
        {
            this.type = checkNotNull(type, "type is null");
            this.pool = checkNotNull(pool, "pool is null");
        }

        @Override
        public EventSubmissionFailedException handleException(Request request, Exception exception)
        {
            log.debug("Posting event to %s failed", request.getUri());
            return new EventSubmissionFailedException(type, pool, ImmutableMap.of(request.getUri(), exception));
        }

        @Override
        public Void handle(Request request, Response response)
        {
            int statusCode = response.getStatusCode();
            if (statusCode >= 200 && statusCode <= 299) {
                return null;
            }

            try {
                InputStream inputStream = response.getInputStream();
                String responseBody = CharStreams.toString(new InputStreamReader(inputStream));
                log.debug("Posting event to %s failed: status_code=%d status_line=%s body=%s", request.getUri(), statusCode, response.getStatusMessage(), responseBody);
            }
            catch (IOException bodyError) {
                log.debug("Posting event to %s failed: status_code=%d status_line=%s error=%s",
                        request.getUri(),
                        statusCode,
                        response.getStatusMessage(),
                        bodyError.getMessage());
            }
            return null;
        }
    }
}
