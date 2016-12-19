/*
 * Copyright 2016 Proofpoint, Inc.
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
package com.proofpoint.reporting;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.proofpoint.reporting.DiagnosticBeanRegistry.RegistrationInfo.registrationInfo;
import static org.testng.Assert.assertEquals;

public class TestDiagnosticBeanRegistry
{
    public static final DiagnosticBean TESTING_DIAGNOSTIC_BEAN = DiagnosticBean.forTarget(new Object()
    {
        @Diagnostic
        public int getMetric()
        {
            return 1;
        }
    });

    private DiagnosticBeanRegistry registry;

    @BeforeMethod
    public void setup()
    {
        registry = new DiagnosticBeanRegistry();
    }

    @Test
    public void testRegister()
            throws Exception
    {
        registry.register(new Object(), TESTING_DIAGNOSTIC_BEAN, "TestingObject");
        assertEquals(registry.getDiagnosticBeans(), ImmutableList.of(registrationInfo(TESTING_DIAGNOSTIC_BEAN, "TestingObject")));
    }

    @Test
    public void testUnRegister()
            throws Exception
    {
        Object object = new Object();
        registry.register(object, TESTING_DIAGNOSTIC_BEAN, "TestingObject");
        registry.unregister(object);
        assertEquals(registry.getDiagnosticBeans(), ImmutableList.of());
    }
}
