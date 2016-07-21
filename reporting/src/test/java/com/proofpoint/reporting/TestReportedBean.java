package com.proofpoint.reporting;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.weakref.jmx.internal.guava.collect.ImmutableList;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.proofpoint.reporting.ReportExporter.notifyBucketIdProvider;
import static com.proofpoint.reporting.Util.getMethod;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestReportedBean
{
    private final TestingBucketIdProvider bucketIdProvider = new TestingBucketIdProvider();
    private final Map<Object, ReportedBean> reportedBeans = new HashMap<>();
    private final List<Object> objects = ImmutableList.of(
            new SimpleObject(),
            new CustomAnnotationObject(),
            new FlattenObject(),
            new CustomFlattenAnnotationObject(),
            new NestedObject(),
            new CustomNestedAnnotationObject()
    );

    public TestReportedBean()
    {
        for (Object object : objects) {
            notifyBucketIdProvider(object, bucketIdProvider, null);
            reportedBeans.put(object, ReportedBean.forTarget(object));
        }
    }

    private Collection<ReportedBeanAttribute> getAttributes(Object object)
    {
        return reportedBeans.get(object).getAttributes();
    }

    private Object getAttribute(Object object, String attributeName)
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        return reportedBeans.get(object).getAttribute(attributeName);
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "report annotation on non-getter .*operation\\(\\)")
    public void testNonAttribute()
    {
        ReportedBean.forTarget(new Object() {
            @Reported
            public int operation()
            {
                return 3;
            }
        });
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "report annotation on non-getter .*operation\\(int\\)")
    public void testSetter()
    {
        ReportedBean.forTarget(new Object() {
            @Reported
            public void operation(int param)
            {
            }
        });
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "report annotation on non-getter .*getVoid\\(\\)")
    public void testInvalidGetter()
    {
        ReportedBean.forTarget(new Object() {
            @Reported
            public void getVoid()
            {
            }
        });
    }

    @Test(dataProvider = "fixtures")
    public void testGetterAttributeInfo(String attribute, boolean isIs, Object[] values, Class<?> clazz)
            throws Exception
    {
        for (Object t : objects) {
            String attributeName = toFeatureName(attribute, t);

            Collection<ReportedBeanAttribute> attributes = getAttributes(t);
            ReportedBeanAttribute attributeInfo = getAttributeInfo(attributes, attributeName);
            assertNotNull(attributeInfo, "AttributeInfo for " + attributeName);
            assertEquals(attributeInfo.getName(), attributeName, "Attribute Name for " + attributeName);
        }
    }

    @Test
    public void testNotReportedAttributeInfo()
            throws Exception
    {

        for (Object t : objects) {
            Collection<ReportedBeanAttribute> attributes = getAttributes(t);
            String attributeName = toFeatureName("NotReported", t);
            ReportedBeanAttribute attributeInfo = getAttributeInfo(attributes, attributeName);
            assertNull(attributeInfo, "AttributeInfo for " + attributeName);
        }
    }

    private ReportedBeanAttribute getAttributeInfo(Collection<ReportedBeanAttribute> attributes, String attributeName)
    {
        for (ReportedBeanAttribute attribute : attributes) {
            if (attribute.getName().equals(attributeName)) {
                return attribute;
            }
        }
        return null;
    }

    @Test(dataProvider = "fixtures")
    public void testGet(String attribute, boolean isIs, Object[] values, Class<?> clazz)
            throws Exception
    {
        String methodName = "set" + attribute.replace(".", "");
        for (Object t : objects) {
            String attributeName = toFeatureName(attribute, t);
            SimpleInterface simpleInterface = toSimpleInterface(t);
            Method setter = getMethod(simpleInterface.getClass(), methodName, clazz);

            for (Object value : values) {
                setter.invoke(simpleInterface, value);
                bucketIdProvider.advance();

                if (isIs && value != null) {
                    if ((Boolean) value) {
                        value = 1;
                    }
                    else {
                        value = 0;
                    }
                }

                assertEquals(getAttribute(t, attributeName), value);
            }
        }
    }

    @Test
    public void testGetFailsOnNotReported()
            throws Exception
    {

        for (Object t : objects) {
            try {
                getAttribute(t, "NotReported");
                fail("Should not allow getting unreported attribute");
            }
            catch (AttributeNotFoundException e) {
                // ignore
            }
        }
    }

    @DataProvider(name = "fixtures")
    Object[][] getFixtures()
    {
        return new Object[][] {

                new Object[] { "BooleanValue", true, new Object[] { true, false }, Boolean.TYPE },
                new Object[] { "BooleanBoxedValue", true, new Object[] { true, false, null }, Boolean.class },
                new Object[] { "ByteValue", false, new Object[] { Byte.MAX_VALUE, Byte.MIN_VALUE, (byte) 0 },
                               Byte.TYPE },
                new Object[] { "ByteBoxedValue", false, new Object[] { Byte.MAX_VALUE, Byte.MIN_VALUE, (byte) 0, null },
                               Byte.class },

                new Object[] { "ShortValue", false, new Object[] { Short.MAX_VALUE, Short.MIN_VALUE, (short) 0 },
                               Short.TYPE },
                new Object[] { "ShortBoxedValue", false,
                               new Object[] { Short.MAX_VALUE, Short.MIN_VALUE, (short) 0, null }, Short.class },

                new Object[] { "IntegerValue", false, new Object[] { Integer.MAX_VALUE, Integer.MIN_VALUE, 0 },
                               Integer.TYPE },
                new Object[] { "IntegerBoxedValue", false,
                               new Object[] { Integer.MAX_VALUE, Integer.MIN_VALUE, 0, null }, Integer.class },

                new Object[] { "LongValue", false, new Object[] { Long.MAX_VALUE, Long.MIN_VALUE, 0L }, Long.TYPE },
                new Object[] { "LongBoxedValue", false, new Object[] { Long.MAX_VALUE, Long.MIN_VALUE, 0L, null },
                               Long.class },

                new Object[] { "FloatValue", false,
                               new Object[] { -Float.MIN_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, 0.0f,
                                              Float.NaN }, Float.TYPE },
                new Object[] { "FloatBoxedValue", false,
                               new Object[] { -Float.MIN_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, 0.0f,
                                              Float.NaN, null }, Float.class },

                new Object[] { "DoubleValue", false,
                               new Object[] { -Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.MIN_VALUE,
                                              0.0, Double.NaN }, Double.TYPE },
                new Object[] { "DoubleBoxedValue", false,
                               new Object[] { -Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.MIN_VALUE,
                                              0.0, Double.NaN }, Double.class },

                new Object[] { "StringValue", false, new Object[] { null, "hello there" }, String.class },

                new Object[] { "ObjectValue", false, new Object[] { "random object", 1, true }, Object.class },

                new Object[] { "PrivateValue", false, new Object[] { Integer.MAX_VALUE, Integer.MIN_VALUE, 0 },
                        Integer.TYPE },

                new Object[] { "BucketedBooleanValue", true, new Object[] { true, false }, Boolean.TYPE },
                new Object[] { "BucketedIntegerValue", false, new Object[] { Integer.MAX_VALUE, Integer.MIN_VALUE, 0 },
                               Integer.TYPE },
                new Object[] { "NestedBucket.BucketedBooleanBoxedValue", true, new Object[] { true, false, null }, Boolean.class },
                new Object[] { "NestedBucket.BucketedLongValue", false, new Object[] { Long.MAX_VALUE, Long.MIN_VALUE, 0L }, Long.TYPE },
                new Object[] { "BucketedBooleanBoxedValue", true, new Object[] { true, false, null }, Boolean.class },
                new Object[] { "BucketedLongValue", false, new Object[] { Long.MAX_VALUE, Long.MIN_VALUE, 0L }, Long.TYPE },
        };
    }

    private String toFeatureName(String attribute, Object t)
    {
        String attributeName;
        if (t instanceof NestedObject) {
            attributeName = "SimpleObject." + attribute;
        }
        else {
            attributeName = attribute;
        }
        return attributeName;
    }

    private SimpleInterface toSimpleInterface(Object t)
    {
        SimpleInterface simpleInterface;
        if (t instanceof SimpleInterface) {
            simpleInterface = (SimpleInterface) t;
        }
        else if (t instanceof FlattenObject) {
            simpleInterface = ((FlattenObject) t).getSimpleObject();
        }
        else if (t instanceof NestedObject) {
            simpleInterface = ((NestedObject) t).getSimpleObject();
        }
        else {
            throw new IllegalArgumentException("Expected objects implementing SimpleInterface or FlattenObject but got " + t.getClass().getName());
        }
        return simpleInterface;
    }

    private static class TestingBucketIdProvider
            implements BucketIdProvider
    {
        private int bucketId = 0;

        @Override
        public int get()
        {
            return bucketId;
        }

        public void advance()
        {
            ++bucketId;
        }
    }
}
