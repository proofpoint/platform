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

import com.proofpoint.units.Duration;

import java.util.concurrent.TimeUnit;

class RetryException extends Exception
{
    static final Duration NO_SUGGESTED_BACKOFF = new Duration(0, TimeUnit.MILLISECONDS);
    private final String failureCategory;
    private final Duration suggestedBackoff;

    RetryException(String failureCategory)
    {
        this(failureCategory, NO_SUGGESTED_BACKOFF);
    }

    RetryException(String failureCategory, Duration suggestedBackoff)
    {
        this.failureCategory = failureCategory;
        this.suggestedBackoff = suggestedBackoff;
    }

    RetryException(Exception cause)
    {
        super(cause);
        failureCategory = cause.getClass().getSimpleName();
        suggestedBackoff = NO_SUGGESTED_BACKOFF;
    }

    RetryException(Exception cause, String failureCategory)
    {
        super(cause);
        this.failureCategory = failureCategory;
        suggestedBackoff = NO_SUGGESTED_BACKOFF;
    }

    RetryException(Exception cause, Exception failureException)
    {
        super(cause);
        failureCategory = failureException.getClass().getSimpleName();
        suggestedBackoff = NO_SUGGESTED_BACKOFF;
    }

    String getFailureCategory()
    {
        return failureCategory;
    }

    Duration getSuggestedBackoff() { return suggestedBackoff; }
}
