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
package com.proofpoint.reporting;

import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;

interface PrometheusBeanAttribute
{
    String getName();

    String getType();

    ValueAndTimestamp getValue(@Nullable Object target)
            throws AttributeNotFoundException, MBeanException, ReflectionException;

    @AutoValue
    abstract class ValueAndTimestamp
    {
        static ValueAndTimestamp valueAndTimestamp(@Nullable Object value, @Nullable Long timestamp)
        {
            return new AutoValue_PrometheusBeanAttribute_ValueAndTimestamp(value, timestamp);
        }

        @Nullable
        abstract Object getValue();

        @Nullable
        abstract Long getTimestamp();
    }
}
