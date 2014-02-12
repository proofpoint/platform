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
package com.proofpoint.bootstrap;

import com.google.common.io.Resources;
import com.google.inject.Binder;
import com.google.inject.ConfigurationException;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.inject.Inject;

import java.io.InputStream;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;

public class TestBootstrap
{
    @Test
    public void testRequiresExplicitBindings()
            throws Exception
    {
        Bootstrap bootstrap = bootstrapApplication("test-application").withModules();
        try {
            bootstrap.initialize().getInstance(Instance.class);
            Assert.fail("should require explicit bindings");
        }
        catch (ConfigurationException e) {
            Assertions.assertContains(e.getErrorMessages().iterator().next().getMessage(), "Explicit bindings are required");
        }
    }

    @Test
    public void testDoesNotAllowCircularDependencies()
        throws Exception
    {
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .withModules(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(InstanceA.class);
                        binder.bind(InstanceB.class);
                    }
                });

        try {
            bootstrap.initialize().getInstance(InstanceA.class);
            Assert.fail("should not allow circular dependencies");
        }
        catch (ProvisionException e) {
            Assertions.assertContains(e.getErrorMessages().iterator().next().getMessage(), "circular proxies are disabled");
        }
    }

    @Test
    public void testWithModulesFromWellFormattedFile()
        throws Exception
    {
        testWithModulesFromFile("test-application-modules.config");
    }

    @Test
    public void testWithModulesFromFileWithExtraLinesAndSpacing()
            throws Exception
    {
        testWithModulesFromFile("test-application-modules-spacing.config");
    }

    private void testWithModulesFromFile(String fileName)
            throws Exception
    {
        String input = Resources.getResource(fileName).getFile();

        Bootstrap bootstrap = bootstrapApplication("test-application")
                .withModulesFromFile(input)
                .build();

        Assert.assertEquals(bootstrap.getModules().size(), 2);
        Assert.assertEquals(bootstrap.getModules().get(0).getClass(), ModuleA.class);
        Assert.assertEquals(bootstrap.getModules().get(1).getClass(), ModuleB.class);
    }

    public static class Instance
    {
    }

    public static class InstanceA
    {
        @Inject
        public InstanceA(InstanceB b)
        {
        }
    }

    public static class InstanceB
    {
        @Inject
        public InstanceB(InstanceA a)
        {
        }
    }

    public static class ModuleA implements Module
    {
        @Override
        public void configure(Binder binder) {
            //NOOP
        }
    }

    public static class ModuleB implements Module
    {
        @Override
        public void configure(Binder binder) {
            //NOOP
        }
    }
}
