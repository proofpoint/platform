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

import com.google.common.io.CharStreams;
import com.google.common.net.MediaType;
import com.google.common.util.concurrent.ListenableFuture;
import com.proofpoint.http.client.DynamicBodySource;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.UnexpectedResponseException;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.tracetoken.TraceToken;
import jakarta.inject.Inject;
import org.weakref.jmx.Flatten;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.proofpoint.http.client.Request.Builder.preparePost;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class HttpEventClient
        implements EventClient
{
    private static final Logger log = Logger.get(HttpEventClient.class);
    private static final MediaType MEDIA_TYPE_JSON = MediaType.create("application", "json");
    private static final EventResponseHandler EVENT_RESPONSE_HANDLER = new EventResponseHandler();

    private final JsonEventWriter eventWriter;
    private final HttpClient httpClient;
    private final NodeInfo nodeInfo;

    @Inject
    public HttpEventClient(
            JsonEventWriter eventWriter,
            NodeInfo nodeInfo,
            @ForEventClient HttpClient httpClient)
    {
        this.eventWriter = requireNonNull(eventWriter, "eventWriter is null");
        this.nodeInfo = requireNonNull(nodeInfo, "nodeInfo is null");
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
    }

    @Flatten
    public RequestStats getStats()
    {
        return httpClient.getStats();
    }

    @SafeVarargs
    @Override
    public final <T> ListenableFuture<Void> post(T... event)
            throws IllegalArgumentException
    {
        requireNonNull(event, "event is null");
        return post(Arrays.asList(event));
    }

    @Override
    public <T> ListenableFuture<Void> post(final Iterable<T> events)
            throws IllegalArgumentException
    {
        requireNonNull(events, "eventsSupplier is null");
        TraceToken token = getCurrentTraceToken();

        Request request = preparePost()
                .setUri(URI.create("v2/event"))
                .setHeader("User-Agent", nodeInfo.getNodeId())
                .setHeader("Content-Type", MEDIA_TYPE_JSON.toString())
                .setBodySource((DynamicBodySource) out -> eventWriter.createEventWriter(events.iterator(), token, out))
                .build();
        return httpClient.executeAsync(request, EVENT_RESPONSE_HANDLER);
    }

    private static class EventResponseHandler implements ResponseHandler<Void, Exception>
    {
        @Override
        public Void handleException(Request request, Exception exception)
                throws Exception
        {
            log.debug(exception, "Posting event to %s failed", request.getUri());
            throw exception;
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
                String responseBody = CharStreams.toString(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                log.debug("Posting event to %s failed: status_code=%d status_line=%s body=%s", request.getUri(), statusCode, response.getStatusMessage(), responseBody);
            }
            catch (IOException bodyError) {
                log.debug("Posting event to %s failed: status_code=%d status_line=%s error=%s",
                        request.getUri(),
                        statusCode,
                        response.getStatusMessage(),
                        bodyError.getMessage());
            }
            throw new UnexpectedResponseException(format("Posting event to %s failed", request.getUri()), request, response);
        }
    }
}
