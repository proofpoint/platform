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

import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.util.UUID;

import static java.lang.Thread.currentThread;

public final class TraceTokenManager
{
    private static final ThreadLocal<TokenState> token = new ThreadLocal<>();

    private TraceTokenManager()
    {}

    public static TraceTokenScope registerRequestToken(@Nullable String token)
    {
        TokenState oldToken = TraceTokenManager.token.get();

        String oldThreadName;
        if (oldToken == null) {
            oldThreadName = currentThread().getName();
        }
        else {
            oldThreadName = oldToken.getOldThreadName();
        }

        if (token == null) {
            TraceTokenManager.token.set(null);
            currentThread().setName(oldThreadName);
        }
        else {
            TraceTokenManager.token.set(new AutoValue_TraceTokenManager_TokenState(token, oldThreadName));
            currentThread().setName(oldThreadName + " " + token);
        }

        if (oldToken == null) {
            return new TraceTokenScope(null);
        }
        else {
            return new TraceTokenScope(oldToken.getToken());
        }
    }

    @Nullable
    public static String getCurrentRequestToken()
    {
        TokenState tokenState = token.get();
        if (tokenState == null) {
            return null;
        }
        return tokenState.getToken();
    }

    public static String createAndRegisterNewRequestToken()
    {
        String newToken = UUID.randomUUID().toString();
        registerRequestToken(newToken);

        return newToken;
    }

    public static void clearRequestToken()
    {
        TokenState oldToken = TraceTokenManager.token.get();
        token.remove();
        if (oldToken != null) {
            currentThread().setName(oldToken.getOldThreadName());
        }
    }

    @AutoValue
    abstract static class TokenState
    {
        abstract String getToken();

        abstract String getOldThreadName();
    }
}
