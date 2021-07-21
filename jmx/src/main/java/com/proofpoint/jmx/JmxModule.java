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
package com.proofpoint.jmx;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;

import static com.proofpoint.configuration.ConfigBinder.bindConfig;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class JmxModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bind(MBeanServer.class).toInstance(ManagementFactory.getPlatformMBeanServer());
        bindConfig(binder).bind(JmxConfig.class);

        newExporter(binder).export(StackTraceMBean.class).withGeneratedName();
        binder.bind(StackTraceMBean.class).in(Scopes.SINGLETON);

        binder.bind(JmxAgent9.class).in(Scopes.SINGLETON);
        binder.bind(JmxAgent.class).to(JmxAgent9.class);
    }
}
