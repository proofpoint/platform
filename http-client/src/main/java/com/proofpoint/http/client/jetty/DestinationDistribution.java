package com.proofpoint.http.client.jetty;

import com.proofpoint.stats.Distribution;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.PoolingHttpDestination;

class DestinationDistribution
        extends CachedDistribution
{
    interface Processor
    {
        void process(Distribution distribution, PoolingHttpDestination destination);
    }

    DestinationDistribution(HttpClient httpClient, Processor processor)
    {
        super(() -> {
            Distribution distribution = new Distribution();
            httpClient.getDestinations().stream()
                    .filter(PoolingHttpDestination.class::isInstance)
                    .map(PoolingHttpDestination.class::cast)
                    .forEach(destination -> processor.process(distribution, destination));
            return distribution;
        });
    }
}
