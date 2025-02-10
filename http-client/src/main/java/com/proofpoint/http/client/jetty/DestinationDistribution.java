package com.proofpoint.http.client.jetty;

import com.proofpoint.stats.Distribution;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.transport.HttpDestination;

class DestinationDistribution
        extends CachedDistribution
{
    interface Processor
    {
        void process(Distribution distribution, HttpDestination destination);
    }

    DestinationDistribution(HttpClient httpClient, Processor processor)
    {
        super(() -> {
            Distribution distribution = new Distribution();
            httpClient.getDestinations().stream()
                    .filter(HttpDestination.class::isInstance)
                    .map(HttpDestination.class::cast)
                    .forEach(destination -> processor.process(distribution, destination));
            return distribution;
        });
    }
}
