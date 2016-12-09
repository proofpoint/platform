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

import com.proofpoint.json.JsonCodec;
import com.proofpoint.tracetoken.TraceToken;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.tracetoken.TraceTokenManager.createAndRegisterNewRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerTraceToken;

class TraceTokenFilter
        implements Filter
{
    private static final JsonCodec<TraceToken> TRACE_TOKEN_JSON_CODEC = jsonCodec(TraceToken.class);

    @Override
    public void init(FilterConfig filterConfig)
            throws ServletException
    {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String token = request.getHeader("X-Proofpoint-TraceToken");
        if (token == null || token.isEmpty()) {
            createAndRegisterNewRequestToken();
        }
        else if (token.charAt(0) == '{') {
            try {
                registerTraceToken(TRACE_TOKEN_JSON_CODEC.fromJson(token));
            }
            catch (RuntimeException e) {
                createAndRegisterNewRequestToken();
            }
        }
        else {
            registerRequestToken(token);
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy()
    {
    }
}
