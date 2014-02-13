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
package com.proofpoint.rack;

import ch.qos.logback.core.util.FileUtil;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.DiscoveryModule;
import com.proofpoint.event.client.HttpEventModule;
import com.proofpoint.http.server.HttpServerModule;
import com.proofpoint.jmx.JmxModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeModule;
import com.proofpoint.reporting.ReportingClientModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.tracetoken.TraceTokenModule;
import org.weakref.jmx.guice.MBeanModule;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;

/**
 * This is the default main-class that should be used in a pom in a ruby/rack project that runs via the platform http rack server.
 */
public class Main
{
    static Logger logger = null;
    static Bootstrap app = null;

    public static void main(String[] args)
            throws Exception
    {
        try {
            app = bootstrapApplication("rack").withApplicationModules().withModules(
                    new NodeModule(),
                    new HttpServerModule(),
                    new HttpEventModule(),
                    new ReportingModule(),
                    new ReportingClientModule(),
                    new TraceTokenModule(),
                    new DiscoveryModule(),
                    new JsonModule(),
                    new MBeanModule(),
                    new RackModule(),
                    new JmxModule()
            );

            Injector injector = app.initialize();
            injector.getInstance(Announcer.class).start();
        }
        catch (Exception e) {
            Logger.get(Main.class).error(e);
            System.err.flush();
            System.out.flush();
            System.exit(0);
        }
        catch (Throwable t) {
            System.exit(0);
        }
    }

    public static List<Module> getApplicationModules()
    {
        return app.getModules();
    }

}
