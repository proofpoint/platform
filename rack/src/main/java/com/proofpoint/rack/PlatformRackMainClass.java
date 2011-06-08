package com.proofpoint.rack;

import com.google.inject.Injector;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.http.server.HttpServerModule;
import com.proofpoint.jmx.JmxModule;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeModule;
import org.weakref.jmx.guice.MBeanModule;

/**
 * This is the default main-class that should be used in a pom in a ruby/rack project that runs via the platform http rack server.
 */
public class PlatformRackMainClass
{
    public static void main(String[] args)
            throws Exception
    {
        Bootstrap app = new Bootstrap(
                new NodeModule(),
                new HttpServerModule(),
                new MBeanModule(),
                new RackModule(),
                new JmxModule());

        try {
            Injector injector = app.strictConfig().initialize();
        }
        catch (Exception e) {
            Logger.get(PlatformRackMainClass.class).error(e);
            System.err.flush();
            System.out.flush();
            System.exit(0);
        }
        catch (Throwable t) {
            System.exit(0);
        }
    }
}
