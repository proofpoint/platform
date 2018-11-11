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
package com.proofpoint.platform.skeleton;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.proofpoint.audit.AuditLogModule;
import com.proofpoint.discovery.client.DiscoveryModule;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.http.server.HttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import com.proofpoint.jmx.JmxHttpModule;
import com.proofpoint.jmx.JmxModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.log.LogJmxModule;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeModule;
import com.proofpoint.reporting.ReportingClientModule;
import com.proofpoint.reporting.ReportingModule;
import org.weakref.jmx.guice.MBeanModule;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;

public final class Main
{
    private static final Logger log = Logger.get(Main.class);

    private Main()
    {
    }

    public static void main(String[] args)
    {
        try {
            Injector injector = bootstrapApplication("skeleton")
                    .withModules(
                            new NodeModule(),
                            new DiscoveryModule(),
                            new HttpServerModule(),
                            new JsonModule(),
                            new JaxrsModule(),
                            new MBeanModule(),
                            new JmxModule(),
                            new JmxHttpModule(),
                            new LogJmxModule(),
                            new AuditLogModule(),
                            new ReportingModule(),
                            new ReportingClientModule(),
                            new MainModule()
                    )
                    .withApplicationDefaults(ImmutableMap.<String, String>builder()
                            .put("http-server.http.enabled", "false")
                            .put("http-server.https.enabled", "true")
                            .put("http-server.https.port", "8443")
                            .build()
                    )
                    .initialize();

            injector.getInstance(Announcer.class).start();
        }
        catch (Throwable e) {
            log.error(e);
            System.exit(1);
        }
    }
}
