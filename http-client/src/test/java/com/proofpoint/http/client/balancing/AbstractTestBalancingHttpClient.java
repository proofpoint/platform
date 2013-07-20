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
package com.proofpoint.http.client.balancing;

import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import org.testng.annotations.Test;

import java.net.URI;

import static com.proofpoint.http.client.Request.Builder.preparePut;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public abstract class AbstractTestBalancingHttpClient<T extends HttpClient>
{
    protected HttpServiceBalancer serviceBalancer;
    protected HttpServiceAttempt serviceAttempt1;
    protected HttpServiceAttempt serviceAttempt2;
    protected HttpServiceAttempt serviceAttempt3;
    protected T balancingHttpClient;
    protected BodyGenerator bodyGenerator;
    protected Request request;
    protected TestingClient httpClient;
    protected Response response;

    protected interface TestingClient
        extends HttpClient
    {
        TestingClient expectCall(String uri, Response response);

        TestingClient expectCall(String uri, Exception exception);

        void assertDone();
    }

    protected abstract T createBalancingHttpClient();

    @Test
    public void testSuccessfulQuery()
            throws Exception
    {
        httpClient.expectCall("http://s1.example.com/v1/service", response);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1).getUri();
        verify(serviceAttempt1).markGood();
        verify(response).getStatusCode();
        verify(responseHandler).handle(any(Request.class), same(response));
        verifyNoMoreInteractions(serviceAttempt1, bodyGenerator, response, responseHandler);
    }

    @Test
    public void testSuccessfulQueryNullPath()
            throws Exception
    {
        httpClient.expectCall("http://s1.example.com/", response);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response))).thenReturn("test response");

        request = preparePut().setUri(new URI(null, null, null, null)).setBodyGenerator(bodyGenerator).build();
        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1).getUri();
        verify(serviceAttempt1).markGood();
        verify(response).getStatusCode();
        verify(responseHandler).handle(any(Request.class), same(response));
        verifyNoMoreInteractions(serviceAttempt1, bodyGenerator, response, responseHandler);
    }
}
