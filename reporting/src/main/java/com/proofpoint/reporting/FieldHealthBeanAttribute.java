package com.proofpoint.reporting;

import javax.management.ReflectionException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

record FieldHealthBeanAttribute(String getDescription, Type getType, Object target, Field field)
        implements HealthBeanAttribute
{
    FieldHealthBeanAttribute
    {
        requireNonNull(getDescription, "description is null");
        requireNonNull(getType, "type is null");
        requireNonNull(target, "target is null");
        requireNonNull(field, "field is null");
    }

    @Override
    public String getValue()
            throws ReflectionException
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
