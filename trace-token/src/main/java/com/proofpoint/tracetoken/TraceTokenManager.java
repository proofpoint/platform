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
package com.proofpoint.tracetoken;

import com.google.common.collect.ImmutableMap;
import jakarta.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

/**
 * Utility class for managing the trace token, a request identifier and set of
 * properties associated with a thread while the thread is handling the request.
 */
public final class TraceTokenManager
{
    private static final ThreadLocal<TokenState> token = new ThreadLocal<>();

    private TraceTokenManager()
    {
    }

    /**
     * Associate a given trace token id, with no other properties, with the
     * current thread.
     *
     * @param tokenId The tokenId to associate with the current thread, or null to
     *                remove the thread's token.
     * @return a {@link TraceTokenScope} which may be used to restore the thread's
     * previous association. Intended to be used with try-with-resources:
     * <code>
     * try (TraceTokenScope ignored = registerRequestToken(traceTokenId)) {
     *     // process request
     * }
     * </code>
     */
    public static TraceTokenScope registerRequestToken(@Nullable String tokenId)
    {
        if (tokenId == null) {
            return registerTraceToken(null);
        }
        return registerTraceToken(new TraceToken(ImmutableMap.of("id", tokenId)));
    }

    /**
     * Associate a given trace token with the current thread.
     *
     * @param token The {@link TraceToken} to associate with the current thread, or null to
     *              remove the thread's token.
     * @return a {@link TraceTokenScope} which may be used to restore the thread's
     * previous association. Intended to be used with try-with-resources:
     * <code>
     * try (TraceTokenScope ignored = registerTraceToken(traceToken)) {
     *     // process request
     * }
     * </code>
     */
    public static TraceTokenScope registerTraceToken(@Nullable TraceToken token)
    {
        TokenState oldTokenState = TraceTokenManager.token.get();

        String oldThreadName;
        if (oldTokenState == null) {
            oldThreadName = currentThread().getName();
        }
        else {
            oldThreadName = oldTokenState.oldThreadName();
        }

        if (token == null) {
            TraceTokenManager.token.set(null);
            currentThread().setName(oldThreadName);
        }
        else {
            TraceTokenManager.token.set(new TokenState(token, oldThreadName));
            currentThread().setName(oldThreadName + " " + token);
        }

        if (oldTokenState == null) {
            return new TraceTokenScope(null);
        }
        else {
            return new TraceTokenScope(oldTokenState.token());
        }
    }

    /**
     * @return The current thread's trace token in string form, or null if no token.
     * @deprecated Use {@link #getCurrentTraceToken()}.
     */
    @Deprecated
    @Nullable
    public static String getCurrentRequestToken()
    {
        TokenState tokenState = token.get();
        if (tokenState == null) {
            return null;
        }
        return tokenState.token().toString();
    }

    /**
     * @return The current thread's trace token, or null if no token.
     */
    @Nullable
    public static TraceToken getCurrentTraceToken()
    {
        TokenState tokenState = token.get();
        if (tokenState == null) {
            return null;
        }
        return tokenState.token();
    }

    /**
     * Create and register a new trace token with a random id.
     *
     * @param properties Additional properties to include in the token.
     * @return The id of the created token.
     */
    public static String createAndRegisterNewRequestToken(String... properties)
    {
        checkArgument((properties.length % 2) == 0, "odd number of elements in properties");
        String newToken = UUID.randomUUID().toString();
        registerRequestToken(newToken);
        if (properties.length != 0) {
            addTraceTokenProperties(properties);
        }

        return newToken;
    }

    /**
     * Remove the thread's token.
     */
    public static void clearRequestToken()
    {
        TokenState oldToken = TraceTokenManager.token.get();
        token.remove();
        if (oldToken != null) {
            currentThread().setName(oldToken.oldThreadName());
        }
    }

    /**
     * Add properties to the current thread's trace token. If there is
     * currently no trace token, does nothing.
     *
     * @param properties Properties to add or replace.
     * @return a {@link TraceTokenScope} which may be used to restore the thread's
     * previous set of properties.
     */
    public static TraceTokenScope addTraceTokenProperties(String... properties)
    {
        TokenState tokenState = token.get();

        if (tokenState == null) {
            return new TraceTokenScope(null);
        }

        Map<String, String> map = new LinkedHashMap<>(tokenState.token());

        checkArgument((properties.length % 2) == 0, "odd number of elements in properties");
        for (int i = 0; i < properties.length; i += 2) {
            requireNonNull(properties[i], "property key is null");
            requireNonNull(properties[i + 1], "property value is null");
            map.put(properties[i], properties[i + 1]);
        }

        return registerTraceToken(new TraceToken(map));
    }

    record TokenState(TraceToken token, String oldThreadName)
    {
        TokenState
        {
            requireNonNull(token, "token is null");
            requireNonNull(oldThreadName, "oldThreadName is null");
        }
    }
}
