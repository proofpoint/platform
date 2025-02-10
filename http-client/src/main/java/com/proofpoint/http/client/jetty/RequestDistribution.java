package com.proofpoint.http.client.jetty;

import com.proofpoint.stats.Distribution;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.transport.HttpDestination;

import java.util.List;

class RequestDistribution
        extends CachedDistribution
{
    interface Processor
    {
        void process(Distribution distribution, JettyRequestListener listener, long now);
    }

    RequestDistribution(HttpClient httpClient, Processor processor)
    {
        super(() -> {
            long now = System.nanoTime();
            Distribution distribution = new Distribution();
            httpClient.getDestinations().stream()
                    .filter(HttpDestination.class::isInstance)
                    .map(HttpDestination.class::cast)
                    .map(JettyHttpClient::getRequestListenersForDestination)
                    .flatMap(List::stream)
                    .forEach(listener -> processor.process(distribution, listener, now));
            return distribution;
        });
    }
}
