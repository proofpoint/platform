/*
 * Copyright 2012 Proofpoint, Inc.
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
package com.proofpoint.launcher;

import com.google.common.base.Joiner;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.airlift.command.Arguments;
import io.airlift.command.Cli;
import io.airlift.command.Command;
import io.airlift.command.Help;
import io.airlift.command.Option;
import io.airlift.command.OptionType;
import io.airlift.command.ParseException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.locks.LockSupport;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

@SuppressFBWarnings(value = "DM_EXIT", justification = "Need to return specific exit codes")
public final class Main
{
    private static final int STATUS_GENERIC_ERROR = 1;
    private static final int STATUS_INVALID_ARGS = 2;
    private static final int STATUS_UNSUPPORTED = 3;
    private static final int STATUS_CONFIG_MISSING = 6;

    // Specific to the "status" command
    private static final int STATUS_NOT_RUNNING = 3;

    private Main()
    {
    }

    public static void main(String[] args)
    {
        Cli<Runnable> cli = Cli.buildCli("launcher", Runnable.class)
                .withDescription("The service launcher")
                .withCommands(Help.class, StartCommand.class, StartClientCommand.class,
                        RunCommand.class, RunClientCommand.class,
                        RestartCommand.class, TryRestartCommand.class, ForceReloadCommand.class,
                        StatusCommand.class, StopCommand.class, KillCommand.class)
                .build();

        Runnable parse;
        try {
            parse = cli.parse(args);
        }
        catch (ParseException e) {
            parse = new ParseError(e, cli);
        }
        parse.run();
    }

    abstract static class LauncherCommand implements Runnable
    {
        final String installPath;

        @Option(type = OptionType.GLOBAL, name = {"-v", "--verbose"}, description = "Run verbosely")
        public boolean verbose = false;

        @Option(type = OptionType.GLOBAL, name = "--node-config", description = "Path to node properties file. Defaults to INSTALL_PATH/etc/node.properties")
        public String nodePropertiesPath = null;

        @Option(type = OptionType.GLOBAL, name = "--jvm-properties", description = "Path to jvm configuration file. Defaults to INSTALL_PATH/etc/jvm.properties")
        public String jvmPropertiesPath = null;

        @Option(type = OptionType.GLOBAL, name = "--jvm-config", description = "Path to legacy jvm config file. Defaults to INSTALL_PATH/etc/jvm.config")
        public String jvmConfigPath = null;

        @Option(type = OptionType.GLOBAL, name = "--config", description = "Optional path to configuration file.")
        public String configPath = null;

        @Option(type = OptionType.GLOBAL, name = "--secrets-config", description = "Optional path to configuration file containing secrets.")
        public String secretsConfigPath = null;

        @Option(type = OptionType.GLOBAL, name = "--data", description = "Path to data directory. Defaults to INSTALL_PATH")
        public String dataDir = null;

        @Option(type = OptionType.GLOBAL, name = "--pid-file", description = "Path to pid file. Defaults to DATA_DIR/var/run/launcher.pid")
        public String pidFilePath = null;
        public String legacyPidFilePath = null;

        @Option(type = OptionType.GLOBAL, name = "--log-file", description = "Path to log file. Defaults to DATA_DIR/var/log/launcher.log")
        public String logPath = null;

        @Option(type = OptionType.GLOBAL, name = "--log-levels-file", description = "Optional path to log config file.")
        public String logLevelsPath = null;

        @Option(type = OptionType.GLOBAL, name = "--bootstrap-log-file", description = "Path to bootstrap log file. Defaults to DATA_DIR/var/log/bootstrap.log")
        public String bootstrapLogPath = null;

        @Option(type = OptionType.GLOBAL, name = "-D", description = "Set a Java System property")
        public final List<String> property = new ArrayList<>();

        final Properties systemProperties = new Properties();
        final List<String> launcherArgs = new ArrayList<>();
        private int stopTimeoutSeconds = 60;

        LauncherCommand()
        {
            URL launcherResource = Main.class.getProtectionDomain().getCodeSource().getLocation();
            if (launcherResource == null) {
                System.err.print("Unable to get path of launcher jar\n");
                System.exit(STATUS_GENERIC_ERROR);
            }

            try {
                installPath = new File(launcherResource.toURI()).getParentFile().getParent();
            }
            catch (URISyntaxException e) {
                // Can't happen
                throw new RuntimeException(e);
            }
        }

        // Copies default truststore to accessable path. Adds k8s ca root cert to truststore. Returns path of new truststore or null
        private String addKubernetesToTrustStore()
        {
            File certFile = new File("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt");

            // Check if the K8S cert exists. If exists, configure new truststore
            if (certFile.exists() && certFile.isFile()) {

                // It is not necessarily possible to find the path of current truststore used.
                // Will request user to set java.home and access default truststore path
                String javaHome = System.getProperty("java.home");
                if (javaHome == null) {
                    System.out.println("System Property java.home not set.");
                    System.exit(STATUS_GENERIC_ERROR);
                }

                File sourceTS = new File(javaHome + "/lib/security/cacerts");
                File destinationTS = new File(dataDir + "/var/cacerts");
                String alias = "kubernetes-root-ca-cert";
                char[] password = "changeit".toCharArray();

                try (FileInputStream sourceIs = new FileInputStream(sourceTS);
                     FileOutputStream destinationOs = new FileOutputStream(destinationTS);
                     FileInputStream certIs = new FileInputStream(certFile)) {

                    //Load default Keystore
                    KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                    keystore.load(sourceIs, password);

                    //Get cert and add.
                    BufferedInputStream bis = new BufferedInputStream(certIs);
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    Certificate cert = cf.generateCertificate(bis);
                    keystore.setCertificateEntry(alias, cert);

                    // Save the new truststore contents
                    keystore.store(destinationOs, password);

                }
                catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
                    throw new RuntimeException("Configuring TLS TrustStore", e);
                }
                if (verbose) {
                    System.out.println("Created new TrustStore with Kubernetes Certificate");
                }
                return destinationTS.getAbsolutePath();
            }
            else {
                return null;
            }
        }

        abstract void execute();

        @Override
        public final void run()
        {
            if (verbose) {
                launcherArgs.add("-v");
                Processes.setVerbose(true);
            }
            if (nodePropertiesPath == null) {
                nodePropertiesPath = installPath + "/etc/node.properties";
            }
            else {
                launcherArgs.add("--node-config");
                launcherArgs.add(new File(nodePropertiesPath).getAbsolutePath());
            }
            if (jvmPropertiesPath == null) {
                jvmPropertiesPath = installPath + "/etc/jvm.properties";
            }
            else {
                launcherArgs.add("--jvm-properties");
                launcherArgs.add(new File(jvmPropertiesPath).getAbsolutePath());
            }
            if (jvmConfigPath == null) {
                jvmConfigPath = installPath + "/etc/jvm.config";
            }
            else {
                launcherArgs.add("--jvm-config");
                launcherArgs.add(new File(jvmConfigPath).getAbsolutePath());
            }
            if (configPath != null) {
                launcherArgs.add("--config");
                launcherArgs.add(new File(configPath).getAbsolutePath());
            }
            if (secretsConfigPath != null) {
                launcherArgs.add("--secrets-config");
                launcherArgs.add(new File(secretsConfigPath).getAbsolutePath());
            }
            if (dataDir == null) {
                dataDir = installPath;
            }
            else {
                launcherArgs.add("--data");
                launcherArgs.add(new File(dataDir).getAbsolutePath());
            }
            if (logLevelsPath != null) {
                launcherArgs.add("--log-levels-file");
                launcherArgs.add(new File(logLevelsPath).getAbsolutePath());
            }

            try (InputStream nodeFile = new FileInputStream(nodePropertiesPath)) {
                systemProperties.load(nodeFile);
            }
            catch (FileNotFoundException ignore) {
            }
            catch (IOException e) {
                throw new RuntimeException("Error reading node properties file: " + e);
            }

            for (String s : property) {
                launcherArgs.add("-D");
                launcherArgs.add(s);
                String[] split = s.split("=", 2);
                String key = split[0];
                if (key.equals("config")) {
                    System.out.println("Config can not be passed in a -D argument. Use --config instead");
                    System.exit(STATUS_INVALID_ARGS);
                }

                if (key.equals("secrets-config")) {
                    System.out.println("secrets-config can not be passed in a -D argument. Use --secrets-config instead");
                    System.exit(STATUS_INVALID_ARGS);
                }

                String value = "";
                if (split.length > 1) {
                    value = split[1];
                }
                systemProperties.setProperty(key, value);
            }

            dataDir = firstNonNull(systemProperties.getProperty("node.data-dir"), dataDir);

            if (pidFilePath == null) {
                pidFilePath = dataDir + "/var/run/platform.pid";
                legacyPidFilePath = dataDir + "/var/run/launcher.pid";
            }
            else {
                launcherArgs.add("--pid-file");
                launcherArgs.add(new File(pidFilePath).getAbsolutePath());
            }
            if (logPath == null) {
                logPath = dataDir + "/var/log/launcher.log";
            }
            else {
                launcherArgs.add("--log-file");
                launcherArgs.add(new File(logPath).getAbsolutePath());
            }
            if (bootstrapLogPath == null) {
                bootstrapLogPath = dataDir + "/var/log/bootstrap.log";
            }
            else {
                launcherArgs.add("--bootstrap-log-file");
                launcherArgs.add(new File(bootstrapLogPath).getAbsolutePath());
            }

            String stopTimeoutString = systemProperties.getProperty("launcher.stop-timeout-seconds");
            if (stopTimeoutString != null) {
                try {
                    stopTimeoutSeconds = Integer.parseUnsignedInt(stopTimeoutString);
                    if (stopTimeoutSeconds == 0) {
                        throw new NumberFormatException();
                    }
                }
                catch (NumberFormatException e) {
                    System.out.println("Value of launcher.stop-timeout-seconds property not a positive integer");
                    System.exit(STATUS_INVALID_ARGS);
                }
            }

            if (verbose) {
                for (String key : systemProperties.stringPropertyNames()) {
                    System.out.println(key + "=" + systemProperties.getProperty(key));
                }
            }

            execute();
        }

        @SuppressFBWarnings(value = "OS_OPEN_STREAM", justification = "false positive")
        protected void start(List<String> args, boolean daemon)
        {
            if ("root".equals(System.getProperty("user.name"))) {
                System.err.println("Cannot run as root");
                System.exit(STATUS_GENERIC_ERROR);
            }

            PidFile pidFile = new PidFile(pidFilePath);

            PidStatus pidStatus = pidFile.getStatus();
            if (pidStatus.held) {
                String msg = "Already running";
                if (pidStatus.pid != 0) {
                    msg += " as " + pidStatus.pid;
                }
                System.err.println(msg);
                System.exit(0);
            }

            Collection<String> jvmConfigArgs = new ArrayList<>();
            try (InputStream jvmPropertiesFile = new FileInputStream(jvmPropertiesPath)) {
                Properties jvmProperties = new Properties();
                jvmProperties.load(jvmPropertiesFile);
                // move all unlocking arguments to the front of the list
                for (Entry<Object, Object> entry : jvmProperties.entrySet()) {
                    if (entry.getKey().toString().startsWith("-XX:+Unlock")) {
                        jvmConfigArgs.add(entry.getKey().toString() + entry.getValue().toString());
                    }
                }
                for (Entry<Object, Object> entry : jvmProperties.entrySet()) {
                    if ("-classpath".equals(entry.getKey())) {
                        jvmConfigArgs.add(entry.getKey().toString());
                        jvmConfigArgs.add(entry.getValue().toString());
                    }
                    else if (!entry.getKey().toString().startsWith("-XX:+Unlock")) {
                        jvmConfigArgs.add(entry.getKey().toString() + entry.getValue().toString());
                    }
                }
            }
            catch (FileNotFoundException ignore) {
                // Fall back to jvm.config
                try (BufferedReader jvmReader = new BufferedReader(new InputStreamReader(new FileInputStream(jvmConfigPath), UTF_8))) {
                    String line;
                    boolean allowSpaces = false;
                    while ((line = jvmReader.readLine()) != null) {
                        if (!line.matches("\\s*(?:#.*)?")) {
                            line = line.trim();
                            if (!allowSpaces && line.matches(".*[ '\"\\\\].*")) {
                                System.err.println("JVM config file line contains space or other shell metacharacter: " + line);
                                System.err.println("JVM config file format is one argument per line, no shell quoting.");
                                System.err.println("To indicate you know what you're doing, add before this line the comment line:");
                                System.err.println("# allow spaces");
                                System.exit(STATUS_GENERIC_ERROR);
                            }

                            jvmConfigArgs.add(line);
                        }
                        else if (line.matches("(?i)\\s*#\\s*allow\\s+spaces\\s*")) {
                            allowSpaces = true;
                        }
                    }
                }
                catch (FileNotFoundException e) {
                    System.err.println("JVM config file is missing: " + jvmConfigPath);
                    System.exit(STATUS_CONFIG_MISSING);
                }
                catch (IOException e) {
                    System.err.println("Error reading JVM config file: " + e);
                    System.exit(STATUS_CONFIG_MISSING);
                }
            }
            catch (IOException e) {
                System.err.println("Error reading JVM properties file: " + e);
                System.exit(STATUS_CONFIG_MISSING);
            }

            boolean isJava8 = System.getProperty("java.version").startsWith("1.8.");
            boolean gcSpecified = jvmConfigArgs.stream()
                    .anyMatch(arg -> arg.equals("-XX:+UseG1GC")
                            || arg.equals("-XX:+UseParallelGC")
                            || arg.equals("-XX:+UseConcMarkSweepGC"));

            List<String> javaArgs = new ArrayList<>();
            javaArgs.add("java");
            if (isJava8 && !gcSpecified) {
                javaArgs.add("-XX:+UseConcMarkSweepGC");
                javaArgs.add("-XX:+ExplicitGCInvokesConcurrent");
            }
            javaArgs.add("-XX:+HeapDumpOnOutOfMemoryError");
            javaArgs.add("-XX:HeapDumpPath=var");
            if (isJava8) {
                javaArgs.add("-XX:+AggressiveOpts");
            }
            if (jvmConfigArgs.stream().noneMatch(s -> s.startsWith("-XX:OnOutOfMemoryError=") || "-XX:CrashOnOutOfMemoryError".equals(s))) {
                javaArgs.add("-XX:+ExitOnOutOfMemoryError");
            }
            javaArgs.add("-Djava.util.logging.manager=com.proofpoint.log.ShutdownWaitingLogManager");

            // Add the trust store. If the trustStore arg is passed from config file, ignore this new truststore.
            if (launcherArgs.stream().noneMatch(s -> s.startsWith("-Djavax.net.ssl.trustStore="))) {
                String trustStorePath = addKubernetesToTrustStore();
                if (trustStorePath != null) {
                    javaArgs.add("-Djavax.net.ssl.trustStore=" + trustStorePath);
                }
            }

            javaArgs.addAll(jvmConfigArgs);

            if (isJava8 && jvmConfigArgs.stream().noneMatch(s -> s.startsWith("-Xmx"))) {
                if (jvmConfigArgs.stream().noneMatch(s -> s.equals("-XX:+UnlockExperimentalVMOptions"))) {
                    javaArgs.add("-XX:+UnlockExperimentalVMOptions");
                }
                if (jvmConfigArgs.stream().noneMatch(s -> s.equals("-XX:+UseCGroupMemoryLimitForHeap"))) {
                    javaArgs.add("-XX:+UseCGroupMemoryLimitForHeap");
                }
            }

            for (String key : systemProperties.stringPropertyNames()) {
                javaArgs.add("-D" + key + "=" + systemProperties.getProperty(key));
            }
            if (configPath != null) {
                javaArgs.add("-Dconfig=" + configPath);
            }
            if (secretsConfigPath != null) {
                javaArgs.add("-Dsecrets-config=" + secretsConfigPath);
            }
            if (daemon) {
                javaArgs.add("-Dlog.path=" + logPath);
                javaArgs.add("-Dlog.enable-console=false");
            }
            if (logLevelsPath != null && new File(logLevelsPath).exists()) {
                javaArgs.add("-Dlog.levels-file=" + logLevelsPath);
            }
            javaArgs.add("-Dlog.bootstrap.path=" + bootstrapLogPath);
            javaArgs.add("-jar");
            javaArgs.add(installPath + "/lib/launcher.jar");
            javaArgs.addAll(launcherArgs);
            if (daemon) {
                checkLibraryPath(javaArgs);
                javaArgs.add("start-client");
            }
            else {
                javaArgs.add("run-client");
            }
            javaArgs.addAll(args);

            if (verbose) {
                System.out.println(Joiner.on(' ').join(javaArgs));
            }

            Process child = null;
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(javaArgs)
                        .directory(new File(dataDir))
                        .redirectInput(Processes.NULL_FILE);
                if (daemon) {
                    processBuilder = processBuilder
                            .redirectOutput(Processes.NULL_FILE)
                            .redirectError(Processes.NULL_FILE);
                }
                else {
                    processBuilder = processBuilder
                            .redirectOutput(Redirect.INHERIT)
                            .redirectError(Redirect.INHERIT);
                }
                child = processBuilder
                        .start();
            }
            catch (IOException e) {
                e.printStackTrace();
                System.exit(STATUS_GENERIC_ERROR);
            }

            if (!daemon) {
                Process childCopy = child;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    childCopy.destroy();
                    try {
                        childCopy.waitFor();
                    }
                    catch (InterruptedException ignored) {
                    }
                }));
                try {
                    System.exit(child.waitFor());
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            do {
                try {
                    int status = child.exitValue();
                    if (status == 0) {
                        status = STATUS_GENERIC_ERROR;
                    }
                    System.err.println("Failed to start");
                    System.exit(status);
                }
                catch (IllegalThreadStateException ignored) {
                }
                pidStatus = pidFile.waitRunning();
                if (!pidStatus.held) {
                    if (verbose) {
                        System.out.println("Waiting for child to lock pid file");
                    }
                    LockSupport.parkNanos(100_000_000);
                }
            } while (!pidStatus.held);

            System.out.println("Started as " + pidStatus.pid);
            System.exit(0);
        }

        private void checkLibraryPath(List<String> javaArgs)
        {
            String pathArg = javaArgs.stream()
                    .filter((arg) -> arg.startsWith("-Djava.library.path="))
                    .reduce((a, b) -> b)
                    .orElse(null);
            if (pathArg == null) {
                return;
            }

            String defaultPath = System.getProperty("java.library.path", "");
            if (pathArg.contains(defaultPath)) {
                return;
            }

            System.out.printf("WARNING: The java.library.path property is being set to a value that does not%n" +
                    "contain the default library path. This is likely to cause a failure to start.%n" +
                    "The default path is %s%n", defaultPath);
        }

        @SuppressFBWarnings(value = "OS_OPEN_STREAM", justification = "false positive")
        protected void invokeMain(List<String> args, boolean daemon)
        {
            if (!installPath.equals(dataDir)) {
                // symlink etc directory into data directory
                // this is needed to support programs that reference etc/xyz from within their config files (e.g., log.levels-file=etc/log.properties)
                try {
                    Files.delete(Paths.get(dataDir, "etc"));
                }
                catch (IOException ignored) {
                }
                try {
                    Files.createSymbolicLink(Paths.get(dataDir, "etc"), Paths.get(installPath, "etc"));
                }
                catch (IOException ignored) {
                }
            }

            String mainClassName;
            JarFile jarFile = null;
            try {
                Manifest manifest = null;
                try {
                    jarFile = new JarFile(installPath + "/lib/main.jar");
                    manifest = jarFile.getManifest();
                }
                catch (IOException e) {
                    System.err.println("Unable to open main jar manifest: " + e + "\n");
                    System.exit(STATUS_GENERIC_ERROR);
                }

                if (manifest == null) {
                    System.err.println("Manifest missing from main jar");
                    System.exit(STATUS_GENERIC_ERROR);
                }

                mainClassName = manifest.getMainAttributes().getValue("Main-Class");
                if (mainClassName == null) {
                    System.err.println("Unable to get Main-Class attribute from main jar manifest");
                    System.exit(STATUS_GENERIC_ERROR);
                }
            }
            finally {
                try {
                    if (jarFile != null) {
                        jarFile.close();
                    }
                }
                catch (IOException ignored) {
                }
            }

            Class<?> mainClass = null;
            try {
                mainClass = Class.forName(mainClassName);
            }
            catch (ClassNotFoundException e) {
                System.err.println("Unable to load class " + mainClassName + ": " + e);
                System.exit(STATUS_GENERIC_ERROR);
            }
            Method mainClassMethod = null;
            try {
                mainClassMethod = mainClass.getMethod("main", String[].class);
            }
            catch (NoSuchMethodException e) {
                System.err.println("Unable to find main method: " + e);
                System.exit(STATUS_GENERIC_ERROR);
            }

            String mainVersion = mainClass.getPackage().getImplementationVersion();
            if (mainVersion != null) {
                System.setProperty("launcher.main.version", mainVersion);
            }

            if (daemon) {
                Processes.detach();
            }

            try {
                mainClassMethod.invoke(null, (Object) args.toArray(new String[0]));
            }
            catch (Throwable e) {
                System.err.println("Main method " + mainClassMethod.getDeclaringClass() + "." + mainClassMethod.getName() + " threw " + e);
                System.exit(STATUS_GENERIC_ERROR);
            }
        }

        static class KillStatus
        {
            public final int exitCode;
            public final String msg;

            public KillStatus(int exitCode, String msg)
            {
                this.exitCode = exitCode;
                this.msg = requireNonNull(msg, "msg is null");
            }
        }

        KillStatus killProcess(boolean graceful)
        {
            PidStatusSource pidFile = new PidFile(pidFilePath);

            for (int pidTriesLeft = 10; pidTriesLeft > 0; --pidTriesLeft) {
                PidStatus pidStatus = pidFile.getStatus();
                if (!pidStatus.held) {
                    if (legacyPidFilePath != null) {
                        pidFile = new LegacyPidFile(legacyPidFilePath);
                        legacyPidFilePath = null;
                        continue;
                    }
                    return new KillStatus(0, "Not running\n");
                }
                if (pidStatus.pid != 0) {
                    int pid = pidStatus.pid;
                    Processes.kill(pid, graceful);
                    for (int waitTriesLeft = stopTimeoutSeconds * 10; waitTriesLeft > 0; --waitTriesLeft) {
                        pidStatus = pidFile.getStatus();
                        if (!pidStatus.held || pidStatus.pid != pid) {
                            return new KillStatus(0, (graceful ? "Stopped " : "Killed ") + pid + "\n");
                        }
                        if (waitTriesLeft == 1 && graceful) {
                            waitTriesLeft = 10;
                            graceful = false;
                            Processes.kill(pid, graceful);
                        }
                        LockSupport.parkNanos(100_000_000);
                    }
                    return new KillStatus(STATUS_GENERIC_ERROR, "Process " + pid + " refuses to die\n");
                }
                LockSupport.parkNanos(100_000_000);
            }
            return new KillStatus(STATUS_GENERIC_ERROR, "Unable to get server pid\n");
        }
    }

    @Command(name = "start", description = "Start server")
    public static class StartCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> args = new ArrayList<>();

        @Override
        public void execute()
        {
            start(args, true);
        }
    }

    @Command(name = "run", description = "Start server in foreground")
    public static class RunCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> args = new ArrayList<>();

        @Override
        public void execute()
        {
            start(args, false);
        }
    }

    @Command(name = "start-client", description = "Internal use only", hidden = true)
    public static class StartClientCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> args = new ArrayList<>();

        @SuppressWarnings("StaticNonFinalField")
        private static PidFile pidFile = null; // static so it doesn't destruct and drop lock when main thread exits

        @Override
        @SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
        public void execute()
        {
            pidFile = new PidFile(pidFilePath);

            try {
                pidFile.indicateStarting();
            }
            catch (AlreadyRunningError e) {
                System.err.println(e.getMessage());
                System.exit(0);
            }

            invokeMain(args, true);

            pidFile.indicateRunning();
        }
    }

    @Command(name = "run-client", description = "Internal use only", hidden = true)
    public static class RunClientCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> args = new ArrayList<>();

        @Override
        public void execute()
        {
            invokeMain(args, false);
        }
    }

    @Command(name = "status", description = "Check status of server")
    public static class StatusCommand extends LauncherCommand
    {
        @Override
        public void execute()
        {
            PidFile pidFile = new PidFile(pidFilePath);

            PidStatus pidStatus = pidFile.getStatus();
            if (pidStatus.held) {
                Integer pid = pidStatus.pid;
                String msg = "Starting";

                pidStatus = pidFile.getRunning();
                if (pidStatus.held) {
                    msg = "Running";
                    if (pidStatus.pid != 0) {
                        pid = pidStatus.pid;
                    }
                }
                if (pid != 0) {
                    msg += " as " + pid;
                }
                System.out.println(msg);
                System.exit(0);
            }

            if (legacyPidFilePath != null) {
                pidStatus = new LegacyPidFile(legacyPidFilePath).getStatus();
                if (pidStatus.held) {
                    System.out.println("Running as " + pidStatus.pid);
                    System.exit(0);
                }
            }

            System.out.println("Not running");
            System.exit(STATUS_NOT_RUNNING);
        }
    }

    @Command(name = "restart", description = "Restart server gracefully")
    public static class RestartCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> args = new ArrayList<>();

        @Override
        public void execute()
        {
            KillStatus killStatus = killProcess(true);
            if (killStatus.exitCode != 0) {
                System.out.println(killStatus.msg);
                System.exit(killStatus.exitCode);
            }

            start(args, true);
        }
    }

    @Command(name = "try-restart", description = "Restart server gracefully if it is already running")
    public static class TryRestartCommand extends LauncherCommand
    {
        @Arguments(description = "Arguments to pass to server")
        public final List<String> args = new ArrayList<>();

        @Override
        public void execute()
        {
            PidFile pidFile = new PidFile(pidFilePath);

            PidStatus pidStatus = pidFile.getStatus();
            if (!pidStatus.held) {
                System.out.println("Not running");
                System.exit(0);
            }

            KillStatus killStatus = killProcess(true);
            if (killStatus.exitCode != 0) {
                System.out.println(killStatus.msg);
                System.exit(killStatus.exitCode);
            }

            start(args, true);
        }
    }

    @SuppressWarnings("EmptyClass")
    @Command(name = "force-reload", description = "Cause server configuration to be reloaded")
    public static class ForceReloadCommand extends TryRestartCommand
    {
    }

    @Command(name = "stop", description = "Stop server gracefully")
    public static class StopCommand extends LauncherCommand
    {
        @Override
        public void execute()
        {
            KillStatus killStatus = killProcess(true);
            System.out.println(killStatus.msg);
            System.exit(killStatus.exitCode);
        }
    }

    @Command(name = "kill", description = "Hard stop of server")
    public static class KillCommand extends LauncherCommand
    {
        @Override
        public void execute()
        {
            KillStatus killStatus = killProcess(false);
            System.out.println(killStatus.msg);
            System.exit(killStatus.exitCode);
        }
    }

    @Command(name = "ParseError")
    public static class ParseError implements Runnable
    {
        private final ParseException e;
        private final Cli<Runnable> cli;

        public ParseError(ParseException e, Cli<Runnable> cli)
        {
            this.e = e;
            this.cli = cli;
        }

        @Override
        public void run()
        {
            final int status;
            if (e.getMessage().equals("No command specified")) {
                status = STATUS_UNSUPPORTED;
            }
            else {
                status = STATUS_INVALID_ARGS;
            }

            System.err.println(e.getMessage());
            System.err.print("\n");
            cli.parse("help").run();

            System.exit(status);
        }
    }
}
