package com.proofpoint.reporting;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;

class FieldHealthBeanAttribute implements HealthBeanAttribute
{
    private final Object target;
    private final String description;
    private final Field field;

    public FieldHealthBeanAttribute(String description, Object target, Field field)
    {
        this.description = checkNotNull(description, "description is null");
        this.target = checkNotNull(target, "target is null");
        this.field = checkNotNull(field, "field is null");
    }

    public String getDescription()
    {
        return description;
    }

    public String getValue()
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        Object atomicReference;
        try {
            atomicReference = field.get(target);
        }
        catch (IllegalAccessException e) {
            throw new ReflectionException(e, "Exception occurred while invoking " + field.getName());
        }

        Object value = ((AtomicReference<?>) atomicReference).get();
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}