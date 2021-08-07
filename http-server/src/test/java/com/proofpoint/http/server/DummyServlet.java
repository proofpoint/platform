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

import com.proofpoint.tracetoken.TraceToken;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_OK;

class DummyServlet
        extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        response.setStatus(SC_OK);
        if (request.getUserPrincipal() != null) {
            response.getOutputStream().write(request.getUserPrincipal().getName().getBytes(UTF_8));
        }
        TraceToken token = getCurrentTraceToken();
        if (token != null) {
            response.addHeader("X-Trace-Token-Was", token.toString());
        }
        response.setHeader("X-Protocol", request.getProtocol());
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws IOException
    {
        resp.setStatus(SC_OK);
        req.getInputStream().transferTo(resp.getOutputStream());
    }
}
