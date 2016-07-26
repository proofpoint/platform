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
package com.proofpoint.jaxrs;

import com.google.inject.multibindings.Multibinder;

import static java.util.Objects.requireNonNull;

public class PrefixedJaxrsBinder
{
    private final Multibinder<Class<?>> applicationPrefixedBinder;
    private final Class<?> type;

    PrefixedJaxrsBinder(Multibinder<Class<?>> applicationPrefixedBinder, Class<?> type)
    {
        this.applicationPrefixedBinder = requireNonNull(applicationPrefixedBinder, "applicationPrefixedBinder is null");
        this.type = requireNonNull(type, "type is null");
    }

    /**
     * See the EDSL description at {@link JaxrsBinder}.
     */
    public void withApplicationPrefix()
    {
        applicationPrefixedBinder.addBinding().toInstance(type);
    }
}
