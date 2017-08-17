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
package com.proofpoint.platform.sample;

import com.google.inject.Injector;
import com.proofpoint.audit.AuditLogModule;
import com.proofpoint.discovery.client.DiscoveryModule;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.http.server.HttpServerModule;
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
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;

public class Main
{
    private static final Logger log = Logger.get(Main.class);

    public static void main(String[] args)
            throws Exception
    {
        try {
            Injector injector = bootstrapApplication("sample-server")
                    .withModules(
                            new NodeModule(),
                            new DiscoveryModule(),
                            new HttpServerModule(),
                            new JsonModule(),
                            explicitJaxrsModule(),
                            new MBeanModule(),
                            new JmxModule(),
                            new JmxHttpModule(),
                            new LogJmxModule(),
                            new AuditLogModule(),
                            new ReportingModule(),
                            new ReportingClientModule(),
                            new MainModule()
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
