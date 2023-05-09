/*
 * Copyright 2013 Proofpoint, Inc.
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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.function.Predicate;

class AdminFilter implements Filter
{
    private static final String ADMIN_PATH = "/admin";
    private static final String ADMIN_PATH_PREFIX = "/admin/";
    private static final Predicate<String> IS_ADMIN_PATH_PREDICATE = input -> {
        if (input == null) {
            return false;
        }

        return input.equals(ADMIN_PATH) || input.startsWith(ADMIN_PATH_PREFIX);
    };
    private final Predicate<String> forThisPortPredicate;

    AdminFilter(boolean isAdmin)
    {
        if (isAdmin) {
            forThisPortPredicate = IS_ADMIN_PATH_PREDICATE;
        }
        else {
            forThisPortPredicate = IS_ADMIN_PATH_PREDICATE.negate();
        }
    }

    @Override
    public void init(FilterConfig filterConfig)
    {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String path = request.getPathInfo();
        if (forThisPortPredicate.test(path)) {
            chain.doFilter(servletRequest, servletResponse);
        } else {
            HttpServletResponse response = (HttpServletResponse) servletResponse;
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    public void destroy()
    {
    }
}
