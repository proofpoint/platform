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

public class AbstractTestBalancingHttpClient<T extends HttpClient>
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

    interface TestingClient
        extends HttpClient
    {
        TestingClient expectCall(String uri, Response response);

        TestingClient expectCall(String uri, Exception exception);

        void assertDone();
    }
}
