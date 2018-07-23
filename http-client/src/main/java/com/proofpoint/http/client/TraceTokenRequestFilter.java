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
package com.proofpoint.http.client;

import com.proofpoint.json.JsonCodec;
import com.proofpoint.tracetoken.TraceToken;

import static com.proofpoint.http.client.Request.Builder.fromRequest;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static java.util.Objects.requireNonNull;

public class TraceTokenRequestFilter
        implements HttpRequestFilter
{
    public static final String TRACETOKEN_HEADER = "X-Proofpoint-Tracetoken";
    private static final JsonCodec<TraceToken> TRACE_TOKEN_JSON_CODEC = jsonCodec(TraceToken.class).withoutPretty();

    @Override
    public Request filterRequest(Request request)
    {
        requireNonNull(request, "request is null");

        TraceToken token = getCurrentTraceToken();
        if (token == null) {
            return request;
        }

        String tokenString;
        if (token.size() == 1) {
            tokenString = token.toString();
        }
        else {
            tokenString = TRACE_TOKEN_JSON_CODEC.toJson(token);
        }

        return fromRequest(request)
                .addHeader(TRACETOKEN_HEADER, tokenString)
                .build();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode()
    {
        return 1;
    }
}
