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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.URI;

import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.Request.Builder.preparePut;
import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;

public class TestRequest
{
    @Test
    public void testEquivalence()
    {
        BodySource bodySource = createBodySource();

        equivalenceTester()
                .addEquivalentGroup(
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).build(),
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).setFollowRedirects(false).build(),
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).setPreserveAuthorizationOnRedirect(false).build())
                .addEquivalentGroup(
                        preparePut().setUri(createUriA()).addHeaders(createHeadersA()).setPreserveAuthorizationOnRedirect(true).build())
                .addEquivalentGroup(
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).setBodySource(bodySource).build(),
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).setBodySource(bodySource).setFollowRedirects(false).build())
                .addEquivalentGroup(
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersB()).setBodySource(bodySource).build())
                .addEquivalentGroup(
                        prepareGet().setUri(createUriB()).addHeaders(createHeadersA()).setBodySource(bodySource).build())
                .addEquivalentGroup(
                        preparePut().setUri(createUriA()).addHeaders(createHeadersA()).build(),
                        preparePut().setUri(createUriA()).addHeaders(createHeadersA()).build())
                .addEquivalentGroup(
                        preparePut().setUri(createUriB()).addHeaders(createHeadersA()).build())
                .addEquivalentGroup(
                        preparePut().setUri(createUriA()).addHeaders(createHeadersB()).build())
                .addEquivalentGroup(
                        preparePut().setUri(createUriA()).addHeaders(createHeadersA()).setBodySource(bodySource).build(),
                        preparePut().setUri(createUriA()).addHeaders(createHeadersA()).setBodySource(bodySource).build())
                .addEquivalentGroup(
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).setBodySource(createBodySource()).build())
                .addEquivalentGroup(
                        preparePut().setUri(createUriA()).addHeaders(createHeadersA()).setBodySource(createBodySource()).build())
                .addEquivalentGroup(
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).setFollowRedirects(true).build(),
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).setFollowRedirects(true).build()
                )
                .check();
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Cannot make requests to HTTP port 0")
    public void testCannotMakeRequestToIllegalPort()
    {
        new Request(URI.create("http://example.com:0/"), "GET", createHeadersA(), createBodySource(), false, false);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "uri does not have a host: http:///foo")
    public void testInvalidUriMissingHost()
    {
        new Request(URI.create("http:///foo"), "GET", createHeadersA(), createBodySource(), false, false);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "uri scheme must be http or https: gopher://example.com")
    public void testInvalidUriScheme()
    {
        new Request(URI.create("gopher://example.com"), "GET", createHeadersA(), createBodySource(), false, false);
    }

    private static URI createUriA()
    {
        return URI.create("http://example.com");
    }

    private static URI createUriB()
    {
        return URI.create("http://example.net");
    }

    private static ListMultimap<String, String> createHeadersA()
    {
        return ImmutableListMultimap.<String, String>builder()
                .put("foo", "bar")
                .put("abc", "xyz")
                .build();
    }

    private static ListMultimap<String, String> createHeadersB()
    {
        return ImmutableListMultimap.<String, String>builder()
                .put("foo", "bar")
                .put("abc", "xyz")
                .put("qqq", "www")
                .put("foo", "zzz")
                .build();
    }

    public static BodySource createBodySource()
    {
        return new DynamicBodySource()
        {
            @Override
            public Writer start(OutputStream out) throws Exception
            {
                throw new UnsupportedOperationException();
            }
        };
    }
}
