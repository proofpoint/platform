/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.reporting.testing;

import com.proofpoint.reporting.Key;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.validation.constraints.NotNull;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;

public class TestTestingReportCollectionFactory
{

    private TestingReportCollectionFactory factory;

    @BeforeMethod
    public void setup()
    {
        factory = new TestingReportCollectionFactory();
    }

    @Test
    public void testGetMock()
    {
        assertNotNull(factory.createReportCollection(KeyedDistribution.class));
        assertNotNull(factory.getMock(KeyedDistribution.class));
        assertNull(factory.getMock(KeyedDistribution2.class));
    }

    @Test
    public void testGetNamedMock()
    {
        assertNotNull(factory.createReportCollection(KeyedDistribution.class, "foo"));
        assertNotNull(factory.createReportCollection(KeyedDistribution.class, "bar"));

        KeyedDistribution foo = factory.getMock(KeyedDistribution.class, "foo");
        KeyedDistribution bar = factory.getMock(KeyedDistribution.class, "bar");
        assertNotNull(foo);
        assertNotNull(bar);
        assertNotSame(foo, bar);

        assertNull(factory.getMock(KeyedDistribution.class, "baz"));
        assertNull(factory.getMock(KeyedDistribution2.class));
        assertNull(factory.getMock(KeyedDistribution2.class, "foo"));
        assertNull(factory.getMock(KeyedDistribution2.class, "baz"));
    }

    @Test
    public void testMock()
    {
        factory.createReportCollection(KeyedDistribution.class)
                .add("foo", true);
        KeyedDistribution keyedDistribution = factory.getMock(KeyedDistribution.class);
        verify(keyedDistribution).add("foo", true);
        verifyNoMoreInteractions(keyedDistribution);
    }

    @Test
    public void testNamedMock()
    {
        factory.createReportCollection(KeyedDistribution.class, "name")
                .add("foo", true);
        KeyedDistribution keyedDistribution = factory.getMock(KeyedDistribution.class, "name");
        verify(keyedDistribution).add("foo", true);
        verifyNoMoreInteractions(keyedDistribution);
    }

    @Test
    public void testGetSuper()
    {
        assertNotNull(factory.createReportCollection(KeyedDistribution.class));
        assertNotNull(factory.getSuper(KeyedDistribution.class));
        assertNull(factory.getMock(KeyedDistribution2.class));
    }

    @Test
    public void testGetNamedSuper()
    {
        assertNotNull(factory.createReportCollection(KeyedDistribution.class, "foo"));
        assertNotNull(factory.createReportCollection(KeyedDistribution.class, "bar"));

        KeyedDistribution foo = factory.getSuper(KeyedDistribution.class, "foo");
        KeyedDistribution bar = factory.getSuper(KeyedDistribution.class, "bar");
        assertNotNull(foo);
        assertNotNull(bar);
        assertNotSame(foo, bar);

        assertNull(factory.getMock(KeyedDistribution.class, "baz"));
        assertNull(factory.getMock(KeyedDistribution2.class));
        assertNull(factory.getMock(KeyedDistribution2.class, "foo"));
        assertNull(factory.getMock(KeyedDistribution2.class, "baz"));
    }

    @Test
    public void testSuper()
    {
        KeyedDistribution reportCollection = factory.createReportCollection(KeyedDistribution.class);
        reportCollection.add("foo", true).put("bar");
        reportCollection.add("foo", false).put("other");

        KeyedDistribution keyedDistribution = factory.getSuper(KeyedDistribution.class);
        SomeObject someObject = keyedDistribution.add("foo", true);

        verify(someObject).put("bar");
        verifyNoMoreInteractions(someObject);

        assertEquals(someObject.get(), "bar");

        // Verify calls on getSuper() don't affect verification of getMock()
        verify(factory.getMock(KeyedDistribution.class)).add("foo", true);
    }

    @Test
    public void testNamedSuper()
    {
        KeyedDistribution reportCollection = factory.createReportCollection(KeyedDistribution.class, "name");
        reportCollection.add("foo", true).put("bar");
        reportCollection.add("foo", false).put("other");

        KeyedDistribution keyedDistribution = factory.getSuper(KeyedDistribution.class, "name");
        SomeObject someObject = keyedDistribution.add("foo", true);

        verify(someObject).put("bar");
        verifyNoMoreInteractions(someObject);

        assertEquals(someObject.get(), "bar");

        // Verify calls on getSuper() don't affect verification of getMock()
        verify(factory.getMock(KeyedDistribution.class, "name")).add("foo", true);
    }

    @Test(expectedExceptions = Error.class, expectedExceptionsMessageRegExp = "Duplicate ReportCollection for interface .*")
    public void testDuplicateClassFails()
    {
        factory.createReportCollection(KeyedDistribution.class);
        factory.createReportCollection(KeyedDistribution.class);
    }

    @Test(expectedExceptions = Error.class, expectedExceptionsMessageRegExp = "Duplicate ReportCollection for interface .*")
    public void testDuplicateNamedClassFails()
    {
        factory.createReportCollection(KeyedDistribution.class, "foo");
        factory.createReportCollection(KeyedDistribution.class, "foo");
    }

    private interface KeyedDistribution
    {
        SomeObject add(@Key("foo") String key, @NotNull @Key("bar") boolean bool);
    }

    private interface KeyedDistribution2
    {
        SomeObject add(@Key("foo") String key, @NotNull @Key("bar") boolean bool);
    }

    public static class SomeObject
    {
        private String value = null;

        public void put(String value)
        {
            this.value = value;
        }

        public String get()
        {
            return value;
        }
    }
}
