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
package com.proofpoint.bootstrap;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The StopTraffic annotation is used on a method that needs to be executed
 * on shutdown before stopping acceptance of external requests into the
 * service. This is intended to remove the service from load balancing pools.
 * The method on which the StopTraffic annotation is applied MUST fulfill
 * all of the following criteria:
 * <ul>
 *     <li>The method MUST NOT have any parameters.</li>
 *     <li>The return type of the method MUST be void.</li>
 *     <li>The method MUST NOT throw a checked exception.</li>
 *     <li>The method on which StopTraffic is applied MAY be public, protected,
 *     package private or private.</li>
 *     <li>The method MUST NOT be static except for the application client.</li>
 *     <li>The method MAY be final.</li>
 * </ul>
 * @see jakarta.annotation.PreDestroy
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface StopTraffic
{
}
