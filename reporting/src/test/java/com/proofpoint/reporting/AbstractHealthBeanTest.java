package com.proofpoint.reporting;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.List;

import static com.proofpoint.reporting.Util.getMethod;
import static org.testng.Assert.assertEquals;

public abstract class AbstractHealthBeanTest<T>
{
    protected List<T> objects;

    protected abstract Object getObject(T t);

    protected abstract String getAttribute(T t, String description)
            throws Exception;

    @Test(dataProvider = "fixtures")
    public void testGet(String methodName, String description, Object[] values, Class<?> clazz)
            throws Exception
    {
        for (T t : objects) {
            SimpleHealthInterface simpleHealthInterface = toSimpleHealthInterface(t);
            Method setter = getMethod(simpleHealthInterface.getClass(), methodName, clazz);

            for (Object value : values) {
                setter.invoke(simpleHealthInterface, value);
                Object expected = value;
                if (expected != null) {
                    expected = expected.toString();
                }
                assertEquals(getAttribute(t, description), expected);
            }
        }
    }

    @DataProvider(name = "fixtures")
    Object[][] getFixtures()
    {
        return new Object[][] {
                new Object[] { "setStringValue", "String value", new Object[] { null, "hello there" }, String.class },
                new Object[] { "setObjectValue", "Object value", new Object[] { "random object", 1, true }, Object.class },
                new Object[] { "setNotBeanValue", "Not Bean value", new Object[] { null, "hello there" }, String.class },
                new Object[] { "setPrivateValue", "Private value", new Object[] { null, "hello there" }, String.class },
                new Object[] { "setFieldValue", "Field value", new Object[] { null, "hello there" }, String.class },
        };
    }

    protected SimpleHealthInterface toSimpleHealthInterface(T t)
    {
        SimpleHealthInterface simpleHealthInterface;
        if (getObject(t) instanceof SimpleHealthInterface getObject) {
            simpleHealthInterface = getObject;
        }
        else if (getObject(t) instanceof FlattenHealthObject flattenHealthObject) {
            simpleHealthInterface = flattenHealthObject.getSimpleHealthObject();
        }
        else if (getObject(t) instanceof NestedHealthObject nestedHealthObject) {
            simpleHealthInterface = nestedHealthObject.getSimpleHealthObject();
        }
        else {
            throw new IllegalArgumentException("Expected objects implementing SimpleHealthInterface or FlattenHealthObject but got " + getObject(t).getClass().getName());
        }
        return simpleHealthInterface;
    }
}
