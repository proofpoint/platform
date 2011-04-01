package com.proofpoint.testing;

import com.google.common.base.Preconditions;
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
 * A collection of static methods to exercise Javabean properties.  These assertion methods simplifies
 * testing Configuration Javabean classes using the platform configuration module, and improves
 * consistency of testing the configuratin values.<p/>
 *
 * Notable methods are: <p/>
 *
 * <ul>
 *     <li>assertSetAndGet -- verify that getter returns the same value set by setter.</li>
 *     <li>assertNotValid -- verify that setter raises specific exception on invalid input</li>
 *     <li>assertNotNull -- non-null property setter returns NullPointerException on null argument.</li>
 *     <li>assertBetweenRangeInclusive -- numerical range check</li>
 *     <li>assertGreaterThanZero -- lazy alias to test [0, Type.Max]</li>
 * </ul>
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

    /**
     * Assert that a bean property only accepts non-null values
     */
    public static void assertNotNull(Object bean, String propertyName)
            throws Exception
    {
        assertNotValid(bean, propertyName, null, NullPointerException.class);
    }


    /**
     * Assert that a bean property only accepts values within range inclusively.
     * Setting bean values outside of the range results in error.
     */
    public static <T extends Number> void assertBetweenRangeInclusive(
            Object bean, String propertyName, T lowerBound, T upperBound)
            throws Exception
    {
        PropertyDescriptor propertyDescriptor = lookupPropertyDescriptor(bean, propertyName);
        Class<?> propertyType = propertyDescriptor.getPropertyType();

        if (propertyType.isAssignableFrom(Integer.class) || propertyType.isAssignableFrom(int.class)) {
            Assertions.assertLessThan((Integer) lowerBound, (Integer) upperBound);
            Integer margin = 1;
            if ((Integer) lowerBound > Integer.MIN_VALUE) {
                assertNotValid(bean, propertyName,
                               Integer.valueOf((Integer) lowerBound - margin),
                               IllegalArgumentException.class);
            }
            if ((Integer) upperBound < Integer.MAX_VALUE) {
                assertNotValid(bean, propertyName,
                               Integer.valueOf((Integer) upperBound + margin),
                               IllegalArgumentException.class);
            }

            assertSetAndGet(bean, propertyName, lowerBound);
            assertSetAndGet(bean, propertyName, upperBound);

            Integer stepsSize = ((Integer) upperBound - (Integer) lowerBound) / 10;
            if (stepsSize > 0) {
                Integer value = (Integer) lowerBound;
                for (int i = 0; i < 100 && value <= (Integer) upperBound; i++) {
                    assertSetAndGet(bean, propertyName, value);
                    value += stepsSize;
                }
            }
        }
        else if (propertyType.isAssignableFrom(Long.class) || propertyType.isAssignableFrom(long.class)) {
            Assertions.assertLessThan((Long) lowerBound, (Long) upperBound);
            Long margin = (long) 1;
            if ((Long) lowerBound > Long.MIN_VALUE) {
                assertNotValid(bean, propertyName,
                               Long.valueOf((Long) lowerBound - margin),
                               IllegalArgumentException.class);
            }
            if ((Long) upperBound < Long.MAX_VALUE) {
                assertNotValid(bean, propertyName,
                               Long.valueOf((Long) upperBound + margin),
                               IllegalArgumentException.class);
            }

            assertSetAndGet(bean, propertyName, lowerBound);
            assertSetAndGet(bean, propertyName, upperBound);

            Long stepsSize = ((Long) upperBound - (Long) lowerBound) / 10;
            if (stepsSize > 0) {
                Long value = (Long) lowerBound;
                for (int i = 0; i < 100 && value <= (Long) upperBound; i++) {
                    assertSetAndGet(bean, propertyName, value);
                    value += stepsSize;
                }
            }
        }
        else if (propertyType.isAssignableFrom(Double.class) || propertyType.isAssignableFrom(double.class)) {
            Assertions.assertLessThan((Double) lowerBound, (Double) upperBound);
            Double margin = 0.001;
            if ((Double) lowerBound > Double.MIN_VALUE) {
                assertNotValid(bean, propertyName,
                               Double.valueOf((Double) lowerBound - margin),
                               IllegalArgumentException.class);
            }
            if ((Double) upperBound < Double.MAX_VALUE) {
                assertNotValid(bean, propertyName,
                               Double.valueOf((Double) upperBound + margin),
                               IllegalArgumentException.class);
            }

            assertSetAndGet(bean, propertyName, lowerBound);
            assertSetAndGet(bean, propertyName, upperBound);

            Double stepsSize = ((Double) upperBound - (Double) lowerBound) / 10;
            if (stepsSize > 0) {
                Double value = (Double) lowerBound;
                for (int i = 0; i < 100 && value <= (Double) upperBound; i++) {
                    assertSetAndGet(bean, propertyName, value);
                    value += stepsSize;
                }
            }
        }
        else if (propertyType.isAssignableFrom(Float.class) || propertyType.isAssignableFrom(float.class)) {
            Assertions.assertLessThan((Float) lowerBound, (Float) upperBound);
            Float margin = (float) 0.001;
            if ((Float) lowerBound > Float.MIN_VALUE) {
                assertNotValid(bean, propertyName,
                               Float.valueOf((Float) lowerBound - margin),
                               IllegalArgumentException.class);
            }
            if ((Float) upperBound < Float.MAX_VALUE) {
                assertNotValid(bean, propertyName,
                               Float.valueOf((Float) upperBound + margin),
                               IllegalArgumentException.class);
            }

            assertSetAndGet(bean, propertyName, lowerBound);
            assertSetAndGet(bean, propertyName, upperBound);

            Float stepsSize = ((Float) upperBound - (Float) lowerBound) / 10;
            if (stepsSize > 0) {
                Float value = (Float) lowerBound;
                for (int i = 0; i < 100 && value <= (Float) upperBound; i++) {
                    assertSetAndGet(bean, propertyName, value);
                    value += stepsSize;
                }
            }
        }
        else {
            fail("Don't know how to handle class: " + propertyType.getSimpleName());
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
            assertBetweenRangeInclusive(bean, propertyName, Integer.valueOf(0), Integer.MAX_VALUE);
        }
        else if (propertyType.isAssignableFrom(Long.class) || propertyType.isAssignableFrom(long.class)) {
            assertBetweenRangeInclusive(bean, propertyName, Long.valueOf(0), Long.MAX_VALUE);
        }
        else if (propertyType.isAssignableFrom(Double.class) || propertyType.isAssignableFrom(double.class)) {
            assertBetweenRangeInclusive(bean, propertyName, Double.valueOf(0), Double.MAX_VALUE);
        }
        else if (propertyType.isAssignableFrom(Float.class) || propertyType.isAssignableFrom(float.class)) {
            assertBetweenRangeInclusive(bean, propertyName, Float.valueOf(0), Float.MAX_VALUE);
        }
        else {
            fail("Don't know how to handle class: " + propertyType.getSimpleName());
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

        Method readMethod = propertyDescriptor.getReadMethod();
        Assert.assertNotNull(readMethod, "There is no getter available for property name: " + propertyName);

        Method writeMethod = propertyDescriptor.getWriteMethod();
        Assert.assertNotNull(writeMethod, "There is no setter available for property name: " + propertyName);

        try {
            writeMethod.invoke(bean, value);
            Object getResult = readMethod.invoke(bean);

            Assertions.assertInstanceOf(value, getResult.getClass());
            assertEquals(getResult, value);
        }
        catch (InvocationTargetException e) {
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

        return propertyDescriptor;
    }
}
