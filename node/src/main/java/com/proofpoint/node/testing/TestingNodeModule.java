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
package com.proofpoint.node.testing;

import com.google.common.base.Optional;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.proofpoint.node.NodeConfig;
import com.proofpoint.node.NodeInfo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class TestingNodeModule
        implements Module
{
    // avoid having an accidental dependency on the environment name
    private static final AtomicLong nextId = new AtomicLong(ThreadLocalRandom.current().nextInt(1000000));

    private final String environment;
    private final Optional<String> pool;

    public TestingNodeModule()
    {
        this(Optional.absent());
    }

    public TestingNodeModule(Optional<String> environment)
    {
        this(environment.or("test" + nextId.getAndIncrement()));
    }

    public TestingNodeModule(String environment)
    {
        this(environment, Optional.absent());
    }

    public TestingNodeModule(String environment, Optional<String> pool)
    {
        checkArgument(!isNullOrEmpty(environment), "environment is null or empty");
        this.environment = environment;
        this.pool = requireNonNull(pool, "pool is null");
    }

    public TestingNodeModule(String environment, String pool)
    {
        this(environment, Optional.of(requireNonNull(pool, "pool is null")));
    }

    @Override
    public void configure(Binder binder)
    {
        NodeConfig nodeConfig = new NodeConfig()
                .setEnvironment(environment)
                .setNodeInternalIp(getV4Localhost())
                .setNodeBindIp(getV4Localhost());

        if (pool.isPresent()) {
            nodeConfig.setPool(pool.get());
        }

        binder.bind(NodeConfig.class).toInstance(nodeConfig);

        newExporter(binder).export(NodeInfo.class).withGeneratedName();
    }

    @Provides
    @Singleton
    NodeInfo createNodeInfo(NodeConfig config)
    {
        return new NodeInfo("test-application", config);
    }

    @SuppressWarnings("ImplicitNumericConversion")
    private static InetAddress getV4Localhost()
    {
        try {
            return InetAddress.getByAddress("localhost", new byte[] {127, 0, 0, 1});
        }
        catch (UnknownHostException e) {
            throw new AssertionError("Could not create localhost address");
        }
    }
}
