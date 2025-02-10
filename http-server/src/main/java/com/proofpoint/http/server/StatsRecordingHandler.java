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
package com.proofpoint.http.server;

import com.proofpoint.units.Duration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;

import javax.net.ssl.SSLSession;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

public class StatsRecordingHandler
        implements RequestLog
{
    private static final String REQUEST_SSL_SESSION_ATTRIBUTE = "org.eclipse.jetty.servlet.request.ssl_session";

    private final RequestStats stats;
    private final DetailedRequestStats detailedRequestStats;

    public StatsRecordingHandler(RequestStats stats, DetailedRequestStats detailedRequestStats)
    {
        this.stats = requireNonNull(stats, "stats is null");
        this.detailedRequestStats = requireNonNull(detailedRequestStats, "detailedRequestStats is null");
    }

    @Override
    public void log(Request request, Response response)
    {
        Duration requestTime = new Duration(max(0, System.currentTimeMillis() - Request.getTimeStamp(request)), TimeUnit.MILLISECONDS);
        SSLSession sslSession = (SSLSession) request.getAttribute(REQUEST_SSL_SESSION_ATTRIBUTE);

        stats.record(Request.getContentBytesRead(request), Response.getContentBytesWritten(response), requestTime);
        detailedRequestStats.requestTimeByCode(response.getStatus(), response.getStatus() / 100).add(requestTime);

        if (sslSession != null) {
            detailedRequestStats.tlsRequest(sslSession.getProtocol(), sslSession.getCipherSuite()).add(1);
        }
    }
}
