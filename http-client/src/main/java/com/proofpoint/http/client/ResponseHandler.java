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

public interface ResponseHandler<T, E extends Exception>
{
    /**
     * Map an exception that was thrown during processing of a request.
     *
     * @param request The request
     * @param exception The exception that was thrown
     * @return The value to return to the caller
     * @throws E The exception to propagate to the caller
     */
    T handleException(Request request, Exception exception)
            throws E;

    /**
     * Convert a response into a return value.
     *
     * @param request The request
     * @param response The response
     * @return The value to return to the caller
     * @throws E The exception to propagate to the caller
     */
    T handle(Request request, Response response)
            throws E;
}
