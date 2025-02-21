/*
 * Copyright 2017 Proofpoint, Inc.
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
package com.proofpoint.audit;

import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Types;

import static java.util.Objects.requireNonNull;

/**
 * Binds {@link AuditLogger} implementations.
 *
 * <h2>The AuditLogger Binding EDSL</h2>
 *
 * <pre>
 *     auditLoggerBinder(binder).bind(RecordClass.class);</pre>
 * <p>
 * Binds an implementation of {@code AuditLogger<RecordClass>}.
 */
public class AuditLoggerBinder
{
    private final Binder binder;

    private AuditLoggerBinder(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder is null").skipSources(getClass());
    }

    /**
     * Creates a new {@link AuditLoggerBinder}. See the EDSL examples at {@link AuditLoggerBinder}.
     *
     * @param binder The Guice {@link Binder} to use.
     */
    public static AuditLoggerBinder auditLoggerBinder(Binder binder)
    {
        return new AuditLoggerBinder(binder);
    }

    /**
     * See the EDSL description at {@link AuditLoggerBinder}.
     */
    public <T> AnnotatedAuditLoggerBinder bind(Class<T> recordClass)
    {

        TypeLiteral<AuditLogger<T>> loggerType = (TypeLiteral<AuditLogger<T>>) TypeLiteral.get(Types.newParameterizedType(AuditLogger.class, recordClass));
        binder.bind(loggerType).toProvider(new AuditLoggerProvider<>(recordClass));

        return new AnnotatedAuditLoggerBinder();
    }
}
