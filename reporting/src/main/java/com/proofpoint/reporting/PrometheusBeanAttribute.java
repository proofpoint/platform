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

import jakarta.annotation.Nullable;

import javax.management.MBeanException;
import javax.management.ReflectionException;

import static java.util.Objects.requireNonNull;

interface PrometheusBeanAttribute
{
    String getName();

    ValueAndTimestamp getValue(@Nullable Object target)
            throws MBeanException, ReflectionException;

    record ValueAndTimestamp(PrometheusValue value, @Nullable Long timestamp)
    {
        public ValueAndTimestamp
        {
            requireNonNull(value, "value is null");
        }

        @Nullable
        static ValueAndTimestamp valueAndTimestamp(@Nullable PrometheusValue value, @Nullable Long timestamp)
        {
            if (value != null) {
                return new ValueAndTimestamp(value, timestamp);
            }
            return null;
        }
    }
}
