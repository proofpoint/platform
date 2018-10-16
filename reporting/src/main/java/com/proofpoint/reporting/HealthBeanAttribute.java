package com.proofpoint.reporting;

import javax.management.MBeanException;
import javax.management.ReflectionException;

interface HealthBeanAttribute
{
    String getDescription();

    Type getType();

    String getValue()
            throws MBeanException, ReflectionException;

    enum Type {
        NORMAL, REMOVE_FROM_ROTATION, RESTART
    }
}
