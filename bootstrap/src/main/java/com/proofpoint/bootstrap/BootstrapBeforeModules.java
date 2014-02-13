package com.proofpoint.bootstrap;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import sun.security.util.Debug;

import java.io.*;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class BootstrapBeforeModules
{
    private final String applicationName;
    private boolean initializeLogging = true;
    private List<Module> modules = null;
    private final String ApplicationModulesConfigFile = "etc/application-modules.config";

    protected BootstrapBeforeModules(String applicationName)
    {
        this.applicationName = checkNotNull(applicationName, "applicationName is null");
        this.modules = new ArrayList<Module>();
    }

    @Beta
    public BootstrapBeforeModules doNotInitializeLogging()
    {
        this.initializeLogging = false;
        return this;
    }

    public Bootstrap withModules(Module... modules)
    {
        addModules(ImmutableList.copyOf(modules));
        return build();
    }

    public Bootstrap withModules(Iterable<? extends Module> modules)
    {
        addModules(modules);
        return build();
    }

    public BootstrapBeforeModules withModulesFromStream(InputStream in)
            throws Exception
    {
        boolean success = true;

        for (String moduleName: getModuleNamesFromStream(in)) {
            success = addModule(moduleName) && success;
        }

        if (!success) {
            throw new RuntimeException("Failed to load one or more modules from stream");
        }

        return this;
    }

    public BootstrapBeforeModules withModulesFromFile(String fileName)
            throws Exception
    {
        try (InputStream in = getFileStreamIfExists(fileName)) {
            return withModulesFromStream(in);
        }
    }

    public BootstrapBeforeModules withApplicationModules()
            throws Exception
    {
        return withModulesFromFile(ApplicationModulesConfigFile);
    }

    public Bootstrap build()
    {
        return new Bootstrap(applicationName, modules, initializeLogging);
    }

    private void addModules(Iterable<? extends Module> modules)
    {
        for (Module m: modules) {
            addModule(m);
        }
    }

    private void addModule(Module module)
    {
        modules.add(module);
    }

    private boolean addModule(String moduleName)
    {
        boolean success = false;

        try
        {
            Module m = (Module)Class.forName(moduleName).newInstance();
            debug("Successfully loaded module %s", moduleName);
            addModule(m);
            success = true;
        }
        catch (Exception x)
        {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);

            x.printStackTrace(pw);
            error(
                    "Failed to load application module %s: %s\n%s",
                    moduleName,
                    x.getMessage(),
                    sw.toString()
            );
        }

        return success;
    }

    /*
    Loads the list of module names generated from the application's pom file
    during the build process.
     */
    private Iterable<String> getModuleNamesFromStream(InputStream in)
            throws Exception
    {
        ArrayList<String> dependencyList = new ArrayList<String>();

        if (in != null) {
            debug("Loading additional modules from stream");
            Scanner scanner = new Scanner(in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.length() > 0) dependencyList.add(line);
            }
        }
        else
        {
            debug("The module stream in null. No additional modules will be loaded.");
        }

        return dependencyList;
    }

    private InputStream getFileStreamIfExists(String fileName)
            throws Exception
    {
        File configFile = new File(fileName);

        if (configFile.isFile()) {
            return new FileInputStream(configFile);
        } else {
            return null;
        }
    }

    private void debug(String msg, Object... args) {
        System.out.format(msg, args); //TODO
    }

    private void error(String msg, Object... args) {
        System.err.format(msg, args); //TODO
    }
}
