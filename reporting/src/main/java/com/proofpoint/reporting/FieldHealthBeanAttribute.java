package com.proofpoint.reporting;

import com.google.auto.value.AutoValue;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

@AutoValue
abstract class FieldHealthBeanAttribute
        implements HealthBeanAttribute
{
    static FieldHealthBeanAttribute fieldHealthBeanAttribute(String description, boolean isRemoveFromRotation, Object target, Field field)
    {
        return new AutoValue_FieldHealthBeanAttribute(description, isRemoveFromRotation, target, field);
    }

    abstract Object getTarget();

    abstract Field getField();

    @Override
    public String getValue()
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        Object atomicReference;
        try {
            atomicReference = getField().get(getTarget());
        }
        catch (IllegalAccessException e) {
            throw new ReflectionException(e, "Exception occurred while invoking " + getField().getName());
        }

        Object value = ((AtomicReference<?>) atomicReference).get();
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
