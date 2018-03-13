/*
 * Copyright 2016 Proofpoint, Inc.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation may be placed on either a method with no arguments or a
 * field of type {@link java.util.concurrent.atomic.AtomicReference}. When
 * the object is bound with {@link HealthBinder}, the method will be called
 * or the field will be examined and any non-null value will
 * cause the server to indicate it should be restarted.
 *
 * A value of {@code null} indicates healthy; any other value indicates a
 * critical problem, with the value's {@link #toString()} used as the message.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface HealthCheckRestartDesired
{
    /**
     * @return The base name of the check. This is prepended with the
     * application name. If the object is bound with a name or annotation,
     * that is appended in parentheses to the name of the check.
     */
    String value();
}
