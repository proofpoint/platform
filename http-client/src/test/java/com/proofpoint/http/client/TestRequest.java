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
import com.proofpoint.testing.EquivalenceTester;
import org.testng.annotations.Test;

import java.net.URI;

public class TestRequest
{
    @Test
    public void testEquivalence()
    {
        BodySource bodySource = createBodySource();

        EquivalenceTester.<Request>equivalenceTester()
                .addEquivalentGroup(
                        new Request(createUri1(), "GET", createHeaders1(), null),
                        new Request(createUri1(), "GET", createHeaders1(), null, false))
                .addEquivalentGroup(
                        new Request(createUri1(), "GET", createHeaders1(), bodySource),
                        new Request(createUri1(), "GET", createHeaders1(), bodySource, false))
                .addEquivalentGroup(
                        new Request(createUri1(), "GET", createHeaders2(), bodySource))
                .addEquivalentGroup(
                        new Request(createUri2(), "GET", createHeaders1(), bodySource))
                .addEquivalentGroup(
                        new Request(createUri1(), "PUT", createHeaders1(), null),
                        new Request(createUri1(), "PUT", createHeaders1(), null))
                .addEquivalentGroup(
                        new Request(createUri2(), "PUT", createHeaders1(), null))
                .addEquivalentGroup(
                        new Request(createUri1(), "PUT", createHeaders2(), null))
                .addEquivalentGroup(
                        new Request(createUri1(), "PUT", createHeaders1(), bodySource),
                        new Request(createUri1(), "PUT", createHeaders1(), bodySource))
                .addEquivalentGroup(
                        new Request(createUri1(), "GET", createHeaders1(), createBodySource()))
                .addEquivalentGroup(
                        new Request(createUri1(), "PUT", createHeaders1(), createBodySource()))
                .addEquivalentGroup(
                        new Request(createUri1(), "GET", createHeaders1(), null, true),
                        new Request(createUri1(), "GET", createHeaders1(), null, true)
                )
                .check();
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Cannot make requests to HTTP port 0")
    public void testCannotMakeRequestToIllegalPort()
            throws Exception
    {
        new Request(URI.create("http://example.com:0/"), "GET", createHeaders1(), createBodySource());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "uri does not have a host: http:///foo")
    public void testInvalidUriMissingHost()
            throws Exception
    {
        new Request(URI.create("http:///foo"), "GET", createHeaders1(), createBodySource());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "uri scheme must be http or https: gopher://example.com")
    public void testInvalidUriScheme()
            throws Exception
    {
        new Request(URI.create("gopher://example.com"), "GET", createHeaders1(), createBodySource());
    }

    private URI createUri1()
    {
        return URI.create("http://example.com");
    }

    private URI createUri2()
    {
        return URI.create("http://example.net");
    }

    private ListMultimap<String, String> createHeaders1()
    {
        return ImmutableListMultimap.of("foo", "bar", "abc", "xyz");
    }

    private ListMultimap<String, String> createHeaders2()
    {
        return ImmutableListMultimap.of("foo", "bar", "abc", "xyz", "qqq", "www", "foo", "zzz");
    }

    public static BodySource createBodySource()
    {
        return new BodySource()
        {
        };
    }
}
