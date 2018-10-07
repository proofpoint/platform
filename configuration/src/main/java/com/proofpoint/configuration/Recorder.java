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
package com.proofpoint.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.proofpoint.configuration.Replayer.Call;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

class Recorder<T>
{
    private final Class<T> configClass;
    private AtomicReference<ImmutableList.Builder<Replayer.Call>> callBuilder = new AtomicReference<>(ImmutableList.builder());
    private AtomicBoolean objectCreated = new AtomicBoolean();

    Recorder(Class<T> configClass)
    {
        this.configClass = configClass;
    }

    Replayer<T> getReplayer() {
        Builder<Call> builder = callBuilder.getAndSet(null);
        checkState(builder != null, "may only be called once");
        return new Replayer<>(builder.build());
    }

    @SuppressWarnings("unchecked")
    T getRecordingObject()
    {
        checkState(!objectCreated.getAndSet(true), "may only be called once");
        return (T) Enhancer.create(configClass, (InvocationHandler) (o, method, objects) -> {
            Builder<Call> callBuilder = this.callBuilder.get();
            checkState(callBuilder != null, "may not record new calls to config object after binding Module returns");
            if (method.getAnnotation(Config.class) == null) {
                throw new UnsupportedOperationException("may only invoke methods with @Config annotations");
            }
            if (objects.length != 1) {
                throw new UnsupportedOperationException("may only invoke single-parameter methods");
            }
            if (!method.getReturnType().isAssignableFrom(configClass) && method.getReturnType() != void.class) {
                throw new UnsupportedOperationException("may only invoke methods returning " + configClass.getSimpleName() + " or void");
            }
            requireNonNull(objects[0]);
            callBuilder.add(new Call(method, objects[0]));
            return method.getReturnType() == void.class ? null : o;
        });
    }
}
