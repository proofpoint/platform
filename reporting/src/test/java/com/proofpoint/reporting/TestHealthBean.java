package com.proofpoint.reporting;

import org.testng.annotations.Test;

import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.fail;

public class TestHealthBean extends AbstractHealthBeanTest<Object>
{
    private final Map<Object, HealthBean> healthBeans = new HashMap<>();

    public TestHealthBean()
    {
        objects = new ArrayList<>();
        objects.add(new SimpleHealthObject());
        objects.add(new SimpleHealthRemoveFromRotationObject());
        objects.add(new SimpleHealthRestartDesiredObject());
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
            throws MBeanException, ReflectionException
    {
        for (HealthBeanAttribute attribute : healthBeans.get(object).attributes()) {
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

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "healthcheck annotation on non-AtomicReference field .*field")
    public void testFieldNotAtomicReference()
    {
        HealthBean.forTarget(new Object() {
            @HealthCheck("Field")
            private Object field;
        });
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = ".*getString\\(\\) cannot have both @HealthCheckRemoveFromRotation and @HealthCheckRestartDesired annotations")
    public void testMethodRestartAndRemoveAnnotations()
    {
        HealthBean.forTarget(new Object() {
            @HealthCheckRemoveFromRotation("Name 1")
            @HealthCheckRestartDesired("Name 2")
            public String getString()
            {
                return null;
            }
        });
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = ".*field cannot have both @HealthCheckRemoveFromRotation and @HealthCheckRestartDesired annotations")
    public void testFieldRestartAndRemoveAnnotations()
    {
        HealthBean.forTarget(new Object() {
            @HealthCheckRemoveFromRotation("Field 1")
            @HealthCheckRestartDesired("Field 2")
            private AtomicReference<String> field;
        });
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = ".*getString\\(\\) cannot have both @HealthCheck and @HealthCheckRestartDesired annotations")
    public void testMethodRegularAndRestartAnnotations()
    {
        HealthBean.forTarget(new Object() {
            @HealthCheck("Name 1")
            @HealthCheckRestartDesired("Name 2")
            public String getString()
            {
                return null;
            }
        });
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = ".*field cannot have both @HealthCheck and @HealthCheckRestartDesired annotations")
    public void testFieldRegularAndRestartAnnotations()
    {
        HealthBean.forTarget(new Object() {
            @HealthCheck("Field 1")
            @HealthCheckRestartDesired("Field 2")
            private AtomicReference<String> field;
        });
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = ".*getString\\(\\) cannot have both @HealthCheck and @HealthCheckRemoveFromRotation annotations")
    public void testMethodRegularAndRemoveAnnotations()
    {
        HealthBean.forTarget(new Object() {
            @HealthCheck("Name 1")
            @HealthCheckRemoveFromRotation("Name 2")
            public String getString()
            {
                return null;
            }
        });
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = ".*field cannot have both @HealthCheck and @HealthCheckRemoveFromRotation annotations")
    public void testFieldRegularAndRemoveAnnotations()
    {
        HealthBean.forTarget(new Object() {
            @HealthCheck("Field 1")
            @HealthCheckRemoveFromRotation("Field 2")
            private AtomicReference<String> field;
        });
    }
}
