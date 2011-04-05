/*
 * Copyright 2011 Proofpoint, Inc.
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
package com.proofpoint.testing;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import org.testng.Assert;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;


/**
 * A collection of static methods to exercise Javabean properties.  These assertion methods simplify
 * testing Configuration Javabean classes used with the platform configuration module.<p/>
 *
 * <ul>
 *     <li>assertSetAndGet -- verify that getter returns the same value set by setter.</li>
 *     <li>assertNotValid -- verify that setter raises specific exception on invalid input</li>
 *     <li>assertNotNull -- non-null property setter returns NullPointerException on null argument.</li>
 *     <li>assertBetweenRangeInclusive -- numerical range check</li>
 *     <li>assertGreaterThanZero -- lazy alias to test [0, Type.MAX_VALUE]</li>
 * </ul>
 *
 * In addition, any of the AssertBean methods check for bean property implementation consistency,
 * such as having both setter and getter, and that the getter return type matches setter argument, etc.<p/>
 *
 * Note 1: These methods can only be applied to Read/Writable Javabean properties that implements
 * both getter and setter.  Attempts to call these methods with Javabean properties that do not support
 * both getter and setter results in assertion failure.<p/>
 *
 * Note 2: These methods uses Java introspection API to access getter/setter.  As such, getter/setter
 * must conform to the Javabean specification such as getter taking only one input argument, and
 * setter returning void (not self).<p/>
 *
 */
public class AssertBean
{
    private static final Map<String, Map<String, PropertyDescriptor>> propertyDescriptorCache = Maps.newHashMap();
    private static final double MARGIN = 0.001;
    private static final int STEPS = 10;


    /**
     * Verify that a bean property only accepts non-null values.  A null
     * value results in NullPointerException.
     */
    public static void assertNotNull(Object bean, String propertyName)
            throws Exception
    {
        assertNotValid(bean, propertyName, null, NullPointerException.class);
    }


    /**
     * Verify that a bean property accepts value of matching Java numerical values,
     * and that a value is accepted if an only if it is within the specified range
     * inclusively.
     */
    public static void assertBetweenRangeInclusive(
            Object bean, String propertyName, Integer lowerBound, Integer upperBound)
            throws Exception
    {
        PropertyDescriptor propertyDescriptor = lookupPropertyDescriptor(bean, propertyName);
        Class<?> propertyType = propertyDescriptor.getPropertyType();

        if (propertyType.isAssignableFrom(Integer.class) || propertyType.isAssignableFrom(int.class)) {
            Assertions.assertLessThan(lowerBound, upperBound);
            int margin = 1;
            if (lowerBound > Integer.MIN_VALUE) {
                assertNotValid(bean, propertyName, Integer.valueOf(lowerBound - margin),
                               IllegalArgumentException.class);
            }
            if (upperBound < Integer.MAX_VALUE) {
                assertNotValid(bean, propertyName, Integer.valueOf(upperBound + margin),
                               IllegalArgumentException.class);
            }

            try {
                assertSetAndGet(bean, propertyName, lowerBound);
                assertSetAndGet(bean, propertyName, upperBound);

                Integer stepsSize = (upperBound - lowerBound) / STEPS;
                if (stepsSize > 0) {
                    Integer value = lowerBound;
                    for (int i = 0; i < STEPS && value <= upperBound; i++) {
                        assertSetAndGet(bean, propertyName, value);
                        value += stepsSize;
                    }
                }
            }
            catch (IllegalArgumentException e) {
                throw new AssertionError(String.format("%s: %s",
                                                       e.getClass().getName(), Strings.nullToEmpty(e.getMessage())));
            }
        }
        else {
            fail(String.format("Incompatible property type: %s.  Expecting %s.",
                               propertyType.getName(), lowerBound.getClass().getName()));
        }
    }


    /**
     * Verify that a bean property accepts value of matching Java numerical values,
     * and that a value is accepted if an only if it is within the specified range
     * inclusively.
     */
    public static void assertBetweenRangeInclusive(
            Object bean, String propertyName, Long lowerBound, Long upperBound)
            throws Exception
    {
        PropertyDescriptor propertyDescriptor = lookupPropertyDescriptor(bean, propertyName);
        Class<?> propertyType = propertyDescriptor.getPropertyType();

        if (propertyType.isAssignableFrom(Long.class) || propertyType.isAssignableFrom(long.class)) {
            Assertions.assertLessThan(lowerBound, upperBound);
            int margin = 1;
            if (lowerBound > Long.MIN_VALUE) {
                assertNotValid(bean, propertyName, Long.valueOf(lowerBound - margin),
                               IllegalArgumentException.class);
            }
            if (upperBound < Long.MAX_VALUE) {
                assertNotValid(bean, propertyName, Long.valueOf(upperBound + margin),
                               IllegalArgumentException.class);
            }

            try {
                assertSetAndGet(bean, propertyName, lowerBound);
                assertSetAndGet(bean, propertyName, upperBound);

                Long stepsSize = (upperBound - lowerBound) / STEPS;
                if (stepsSize > 0) {
                    Long value = lowerBound;
                    for (int i = 0; i < STEPS && value <= upperBound; i++) {
                        assertSetAndGet(bean, propertyName, value);
                        value += stepsSize;
                    }
                }
            }
            catch (IllegalArgumentException e) {
                throw new AssertionError(String.format("%s: %s", e.getClass().getName(), e.getMessage()));
            }
        }
        else {
            fail(String.format("Incompatible property type: %s.  Expecting %s.",
                               propertyType.getName(), lowerBound.getClass().getName()));
        }
    }


    /**
     * Verify that a bean property accepts value of matching Java numerical values,
     * and that a value is accepted if an only if it is within the specified range
     * inclusively.
     */
    public static void assertBetweenRangeInclusive(
            Object bean, String propertyName, Double lowerBound, Double upperBound)
            throws Exception
    {
        PropertyDescriptor propertyDescriptor = lookupPropertyDescriptor(bean, propertyName);
        Class<?> propertyType = propertyDescriptor.getPropertyType();

        if (propertyType.isAssignableFrom(Double.class) || propertyType.isAssignableFrom(double.class)) {
            Assertions.assertLessThan(lowerBound, upperBound);
            double margin = MARGIN;
            if (lowerBound > Double.MIN_VALUE) {
                assertNotValid(bean, propertyName, Double.valueOf(lowerBound - margin),
                               IllegalArgumentException.class);
            }
            if (upperBound < Double.MAX_VALUE) {
                assertNotValid(bean, propertyName, Double.valueOf(upperBound + margin),
                               IllegalArgumentException.class);
            }

            try {
                assertSetAndGet(bean, propertyName, lowerBound);
                assertSetAndGet(bean, propertyName, upperBound);

                Double stepsSize = (upperBound - lowerBound) / STEPS;
                if (stepsSize > 0) {
                    Double value = lowerBound;
                    for (int i = 0; i < STEPS && value <= upperBound; i++) {
                        assertSetAndGet(bean, propertyName, value);
                        value += stepsSize;
                    }
                }
            }
            catch (IllegalArgumentException e) {
                throw new AssertionError(String.format("%s: %s", e.getClass().getName(), e.getMessage()));
            }
        }
        else {
            fail(String.format("Incompatible property type: %s.  Expecting %s.",
                               propertyType.getName(), lowerBound.getClass().getName()));
        }
    }


    /**
     * Verify that a bean property accepts value of matching Java numerical values,
     * and that a value is accepted if an only if it is within the specified range
     * inclusively.
     */
    public static void assertBetweenRangeInclusive(
            Object bean, String propertyName, Float lowerBound, Float upperBound)
            throws Exception
    {
        PropertyDescriptor propertyDescriptor = lookupPropertyDescriptor(bean, propertyName);
        Class<?> propertyType = propertyDescriptor.getPropertyType();

        if (propertyType.isAssignableFrom(Float.class) || propertyType.isAssignableFrom(float.class)) {
            Assertions.assertLessThan(lowerBound, upperBound);
            float margin = (float) MARGIN;
            if (lowerBound > Float.MIN_VALUE) {
                assertNotValid(bean, propertyName, Float.valueOf(lowerBound - margin),
                               IllegalArgumentException.class);
            }
            if (upperBound < Float.MAX_VALUE) {
                assertNotValid(bean, propertyName, Float.valueOf(upperBound + margin),
                               IllegalArgumentException.class);
            }

            try {
                assertSetAndGet(bean, propertyName, lowerBound);
                assertSetAndGet(bean, propertyName, upperBound);

                Float stepsSize = (upperBound - lowerBound) / STEPS;
                if (stepsSize > 0) {
                    Float value = lowerBound;
                    for (int i = 0; i < STEPS && value <= upperBound; i++) {
                        assertSetAndGet(bean, propertyName, value);
                        value += stepsSize;
                    }
                }
            }
            catch (IllegalArgumentException e) {
                throw new AssertionError(String.format("%s: %s", e.getClass().getName(), e.getMessage()));
            }
        }
        else {
            fail(String.format("Incompatible property type: %s.  Expecting %s.",
                               propertyType.getName(), lowerBound.getClass().getName()));
        }
    }




    /**
     * Assert that a bean property only accepts positive non-zero value
     */
    public static void assertGreaterThanZero(Object bean, String propertyName)
            throws Exception
    {
        PropertyDescriptor propertyDescriptor = lookupPropertyDescriptor(bean, propertyName);
        Class<?> propertyType = propertyDescriptor.getPropertyType();

        if (propertyType.isAssignableFrom(Integer.class) || propertyType.isAssignableFrom(int.class)) {
            assertBetweenRangeInclusive(bean, propertyName, Integer.valueOf(1), Integer.MAX_VALUE);
        }
        else if (propertyType.isAssignableFrom(Long.class) || propertyType.isAssignableFrom(long.class)) {
            assertBetweenRangeInclusive(bean, propertyName, Long.valueOf(1), Long.MAX_VALUE);
        }
        else if (propertyType.isAssignableFrom(Double.class) || propertyType.isAssignableFrom(double.class)) {
            assertBetweenRangeInclusive(bean, propertyName, Double.valueOf(MARGIN), Double.MAX_VALUE);
        }
        else if (propertyType.isAssignableFrom(Float.class) || propertyType.isAssignableFrom(float.class)) {
            assertBetweenRangeInclusive(bean, propertyName, Float.valueOf((float)MARGIN), Float.MAX_VALUE);
        }
        else {
            fail("Don't know how to handle value of type: " + propertyType.getSimpleName());
        }
    }


    /**
     * Assert that a bean property rejects an invalid value and raised expected exception
     */
    public static void assertNotValid(
            Object bean, String propertyName, Object value, Class<? extends Exception> expectedException)
        throws Exception {

        Preconditions.checkNotNull(bean);
        Preconditions.checkNotNull(propertyName);
        Preconditions.checkNotNull(expectedException);

        try {
            assertSetAndGet(bean, propertyName, value);
            fail("Expecting exception " + expectedException.getSimpleName() + " but got none");
        }
        catch (Exception e) {
            if (! e.getClass().isAssignableFrom(expectedException)) {
                fail(String.format("Expecting exception %s, but got %s",
                                   expectedException.getSimpleName(), e.getClass().getSimpleName()));
            }
        }
    }


    /**
     * Assert that a bean property getter returns the same value set by the setter
     */
    public static void assertSetAndGet(Object bean, String propertyName, File directory)
            throws Exception {
        Assert.assertNotNull(bean);
        Assert.assertNotNull(propertyName);
        Assert.assertNotNull(directory);

        File tmpDir = null;
        File testDir = null;

        try {
            tmpDir = Files.createTempDir();
            testDir = new File(tmpDir, directory.getAbsolutePath());
            testDir.mkdirs();

            assertSetAndGet(bean, propertyName, testDir.getAbsolutePath());
        }
        finally {
            if (testDir != null) {
                Files.deleteRecursively(testDir);
            }
            if (tmpDir != null) {
                Files.deleteRecursively(tmpDir);
            }
        }
    }


    /**
     * Assert that a bean property getter returns the same value set by the setter
     */
    public static void assertSetAndGet(Object bean, String propertyName, Object value)
            throws Exception {

        PropertyDescriptor propertyDescriptor = lookupPropertyDescriptor(bean, propertyName);

        Method getter = propertyDescriptor.getReadMethod();
        Method setter = propertyDescriptor.getWriteMethod();

        try {
            setter.invoke(bean, value);
            Object getResult = getter.invoke(bean);

            // Better to get class from getResult due to auto-boxing
            Class<?> expectedType = getResult != null? getResult.getClass(): getter.getReturnType();
            Assertions.assertInstanceOf(value, expectedType);
            assertEquals(getResult, value);
        }
        catch (InvocationTargetException e) {
            // Unexpected exception probably due to invalid bean property definition.
            // This is not a part of the assert check in this method, and there is
            // no reason to mask it with an AssertError.  Simply let this exception
            // propagate.
            if (e.getTargetException() instanceof Exception) {
                throw (Exception) e.getTargetException();
            }
            else {
                throw e;
            }
        }
    }


    private static PropertyDescriptor lookupPropertyDescriptor(Object bean, String propertyName)
        throws Exception {

        Assert.assertNotNull(bean);
        Assert.assertNotNull(propertyName);

        Map<String, PropertyDescriptor> descMap = propertyDescriptorCache.get(bean.getClass().getName());
        if (descMap == null) {
            descMap = Maps.newHashMap();
            propertyDescriptorCache.put(bean.getClass().getName(), descMap);
        }

        PropertyDescriptor propertyDescriptor = descMap.get(propertyName);
        if (propertyDescriptor == null) {
            // Don't see a method to index into a PropertyDescriptor, so we just have to
            // do a linear search to find a match.
            BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());
            for (PropertyDescriptor p: beanInfo.getPropertyDescriptors()) {
                if (propertyName.equals(p.getName())) {
                    propertyDescriptor = p;
                    descMap.put(propertyName, p);
                    break;
                }
            }
        }

        Assert.assertNotNull(propertyDescriptor,
                      String.format("Bean class %s does not have property with name: %s",
                                    bean.getClass().getSimpleName(), propertyName));

        Method getter = propertyDescriptor.getReadMethod();
        Assert.assertNotNull(getter, "There is no getter available for property name: " + propertyName);

        Method setter = propertyDescriptor.getWriteMethod();
        Assert.assertNotNull(setter, "There is no setter available for property name: " + propertyName);

        // Verify that the return type of the getter matches the argument type of the setter.
        // for introspection to work, this already has exactly one argument
        Class<?> [] setterParams = setter.getParameterTypes();
        assertEquals(getter.getReturnType(), setterParams[0]);

        return propertyDescriptor;
    }
}
