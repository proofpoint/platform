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

import com.google.common.io.ByteStreams;
import com.proofpoint.tracetoken.TraceToken;
import com.proofpoint.tracetoken.TraceTokenManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;

class DummyServlet
        extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException
    {
        resp.setStatus(HttpServletResponse.SC_OK);
        if (req.getUserPrincipal() != null) {
            resp.getOutputStream().write(req.getUserPrincipal().getName().getBytes());
        }
        TraceToken token = getCurrentTraceToken();
        if (token != null) {
            resp.addHeader("X-Trace-Token-Was", token.toString());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException
    {
        resp.setStatus(HttpServletResponse.SC_OK);
        ByteStreams.copy(req.getInputStream(), resp.getOutputStream());
    }
}
