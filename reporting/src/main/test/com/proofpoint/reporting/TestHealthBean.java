package com.proofpoint.reporting;

import org.testng.annotations.Test;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.fail;

public class TestHealthBean extends AbstractHealthBeanTest<Object>
{
    private final Map<Object, HealthBean> healthBeans = new HashMap<>();

    public TestHealthBean()
    {
        objects = new ArrayList<>();
        objects.add(new SimpleHealthObject());
        objects.add(new FlattenHealthObject());
        objects.add(new NestedHealthObject());

        for (Object object : objects) {
            healthBeans.put(object, HealthBean.forTarget(object));
        }
    }

    @Override
    protected Object getObject(Object o) {
        return o;
    }

    @Override
    protected String getAttribute(Object object, String description)
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        for (HealthBeanAttribute attribute : healthBeans.get(object).getAttributes()) {
            if (attribute.getDescription().equals(description)) {
                return attribute.getValue();
            }
        }
        fail("Did not find attribute for " + description);
        return null;
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "healthcheck annotation on non-getter .*operation\\(int\\)")
    public void testNonAttribute()
    {
        HealthBean.forTarget(new Object() {
            @HealthCheck("Operation")
            public void operation(int param)
            {
            }
        });
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "healthcheck annotation on non-getter .*getVoid\\(\\)")
    public void testInvalidGetter()
    {
        HealthBean.forTarget(new Object() {
            @HealthCheck("Void")
            public void getVoid()
            {
            }
        });
    }
}
