package com.proofpoint.http.client.jetty;

import com.proofpoint.stats.Distribution;
import org.eclipse.jetty.client.DuplexConnectionPool;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.transport.HttpDestination;

import java.util.Objects;

class ConnectionPoolDistribution
        extends CachedDistribution
{
    interface Processor
    {
        void process(Distribution distribution, DuplexConnectionPool pool);
    }

    ConnectionPoolDistribution(HttpClient httpClient, Processor processor)
    {
        super(() -> {
            Distribution distribution = new Distribution();
            httpClient.getDestinations().stream()
                    .filter(HttpDestination.class::isInstance)
                    .map(HttpDestination.class::cast)
                    .map(HttpDestination::getConnectionPool)
                    .filter(Objects::nonNull)
                    .map(DuplexConnectionPool.class::cast)
                    .forEach(pool -> processor.process(distribution, pool));
            return distribution;
        });
    }
}
