/*
 * Copyright 2018 Proofpoint, Inc.
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

import com.google.common.collect.ImmutableList;
import org.eclipse.jetty.server.Request;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class TestClientAddressExtractor
{
    Request request;

    @BeforeMethod
    public void setup()
    {
        request = mock(Request.class);
    }

    @Test
    public void testIgnoreForwardedFor()
    {
        when(request.getRemoteAddr()).thenReturn("9.9.9.9");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("1.1.1.1, 2.2.2.2", "3.3.3.3, 4.4.4.4")));

        assertEquals(new ClientAddressExtractor().clientAddressFor(request), "9.9.9.9");
    }

    @Test
    public void testUseForwardedFor()
    {
        when(request.getRemoteAddr()).thenReturn("10.10.10.10");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("1.1.1.1, 2.2.2.2", "3.3.3.3, 4.4.4.4")));

        assertEquals(new ClientAddressExtractor().clientAddressFor(request), "4.4.4.4");
    }

    @Test
    public void testUseForwardedForTwoHops()
    {
        when(request.getRemoteAddr()).thenReturn("10.10.10.10");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("1.1.1.1, 2.2.2.2", "3.3.3.3, 10.11.12.13")));

        assertEquals(new ClientAddressExtractor().clientAddressFor(request), "3.3.3.3");
    }

    @Test
    public void testUseForwardedForThreeHops()
    {
        when(request.getRemoteAddr()).thenReturn("10.10.10.10");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("1.1.1.1, 2.2.2.2", "10.14.15.16, 10.11.12.13")));

        assertEquals(new ClientAddressExtractor().clientAddressFor(request), "2.2.2.2");
    }

    @Test
    public void testUseForwardedForInternal()
    {
        when(request.getRemoteAddr()).thenReturn("10.10.10.10");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("10.11.12.13")));

        assertEquals(new ClientAddressExtractor().clientAddressFor(request), "10.11.12.13");
    }

    @Test
    public void testUseForwardedForInternalTwoHops()
    {
        when(request.getRemoteAddr()).thenReturn("10.10.10.10");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("10.14.15.16, 10.11.12.13")));

        assertEquals(new ClientAddressExtractor().clientAddressFor(request), "10.14.15.16");
    }

    @Test
    public void testInvalidIpAddress()
    {
        when(request.getRemoteAddr()).thenReturn("10.10.10.10");
        when(request.getHeaders("X-FORWARDED-FOR")).thenReturn(Collections.enumeration(ImmutableList.of("notanaddr, 10.14.15.16, 10.11.12.13")));

        assertEquals(new ClientAddressExtractor().clientAddressFor(request), "10.14.15.16");
    }
}
