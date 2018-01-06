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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.client.jetty.JettyIoPool;
import com.proofpoint.http.client.jetty.JettyIoPoolConfig;
import com.proofpoint.reporting.ReportExporter;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.CaseFormat.LOWER_HYPHEN;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;

class JettyIoPoolManager
{
    private final List<JettyHttpClient> clients = new ArrayList<>();
    private final String name;
    private final Class<? extends Annotation> annotation;
    private final AtomicBoolean destroyed = new AtomicBoolean();
    private JettyIoPool pool;
    private Injector injector;

    JettyIoPoolManager(String name, Class<? extends Annotation> annotation)
    {
        this.name = name;
        this.annotation = annotation;
    }

    void addClient(JettyHttpClient client)
    {
        clients.add(client);
    }

    boolean isDestroyed()
    {
        return destroyed.get();
    }

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = injector;
    }

    @PreDestroy
    public void destroy()
    {
        // clients must be destroyed before the pools or
        // you will create a several second busy wait loop
        clients.forEach(JettyHttpClient::close);
        if (pool != null) {
            pool.close();
            pool = null;
        }
        destroyed.set(true);
    }

    JettyIoPool get()
    {
        if (pool == null) {
            JettyIoPoolConfig config = injector.getInstance(keyFromNullable(JettyIoPoolConfig.class, annotation));
            ReportExporter reportExporter = injector.getInstance(ReportExporter.class);
            pool = new JettyIoPool(name, config);
            reportExporter.export(pool, false, "HttpClient.IoPool." + LOWER_HYPHEN.to(UPPER_CAMEL, name), ImmutableMap.of());
        }
        return pool;
    }

    private static <T> Key<T> keyFromNullable(Class<T> type, Class<? extends Annotation> annotation)
    {
        return (annotation != null) ? Key.get(type, annotation) : Key.get(type);
    }
}
