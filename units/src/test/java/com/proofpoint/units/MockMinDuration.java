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
package com.proofpoint.units;

import jakarta.validation.Payload;

import java.lang.annotation.Annotation;

@SuppressWarnings("ClassExplicitlyAnnotation")
class MockMinDuration
        implements MinDuration
{
    private final Duration duration;

    public MockMinDuration(Duration duration)
    {
        this.duration = duration;
    }

    @Override
    public String value()
    {
        return duration.toString();
    }

    @Override
    public String message()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<?>[] groups()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<? extends Payload>[] payload()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<? extends Annotation> annotationType()
    {
        return MinDuration.class;
    }
}
