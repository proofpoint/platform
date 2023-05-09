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
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Encoder;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.tracetoken.TraceTokenManager.registerRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerTraceToken;
import static java.util.Objects.requireNonNull;

class TraceTokenFilter
        implements Filter
{
    private static final JsonCodec<TraceToken> TRACE_TOKEN_JSON_CODEC = jsonCodec(TraceToken.class);
    private static final Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder();
    private static final ThreadLocal<SecureRandom> SECURE_RANDOM = ThreadLocal.withInitial(SecureRandom::new);

    private final ClientAddressExtractor clientAddressExtractor;
    private final String tokenPrefix;

    TraceTokenFilter(InetAddress internalIp, ClientAddressExtractor clientAddressExtractor)
    {
        tokenPrefix = encodeAddress(requireNonNull(internalIp, "internalIp is null"));
        this.clientAddressExtractor = requireNonNull(clientAddressExtractor, "clientAddressExtractor is null");
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
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String token = request.getHeader("X-Proofpoint-TraceToken");
        if (token == null || token.isEmpty()) {
            registerNewRequestToken(request);
        }
        else if (token.charAt(0) == '{') {
            try {
                registerTraceToken(TRACE_TOKEN_JSON_CODEC.fromJson(token));
            }
            catch (RuntimeException e) {
                registerNewRequestToken(request);
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

    private static String encodeAddress(InetAddress inetAddress)
    {
        byte[] address = inetAddress.getAddress();

        if (address.length > 6) {
            address = Arrays.copyOfRange(address, address.length - 6, address.length);
        }
        else if (address.length == 4 && address[0] == 10) {
            address = Arrays.copyOfRange(address, 1, 4);
        }

        String encoded = BASE64_URL_ENCODER.encodeToString(address);
        if (encoded.endsWith("==")) {
            return encoded.replace("==", "=");
        }
        else if (encoded.endsWith("=")) {
            return encoded;
        }
        else {
            return encoded + "=";
        }
    }

    private void registerNewRequestToken(HttpServletRequest request)
            throws UnknownHostException
    {
        byte[] randomBytes = new byte[15];
        SECURE_RANDOM.get().nextBytes(randomBytes);
        registerRequestToken(tokenPrefix
                + encodeAddress(InetAddress.getByName(clientAddressExtractor.clientAddressFor(request)))
                + BASE64_URL_ENCODER.encodeToString(randomBytes)
        );
    }
}
