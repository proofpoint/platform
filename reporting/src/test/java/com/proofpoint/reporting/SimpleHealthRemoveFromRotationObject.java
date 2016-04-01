package com.proofpoint.reporting;

import java.util.concurrent.atomic.AtomicReference;

public class SimpleHealthRemoveFromRotationObject
        implements SimpleHealthInterface
{
    private String stringValue;
    private Object objectValue;
    private String notBeanValue;
    private String privateValue;

    @HealthCheckRemoveFromRotation("Field value")
    private final AtomicReference<String> fieldValue = new AtomicReference<>();

    private String notHealthCheck;


    @HealthCheckRemoveFromRotation("String value")
    public String getStringValue()
    {
        return stringValue;
    }

    public void setStringValue(String stringValue)
    {
        this.stringValue = stringValue;
    }

    @HealthCheckRemoveFromRotation("Object value")
    public Object getObjectValue()
    {
        return objectValue;
    }

    public void setObjectValue(Object objectValue)
    {
        this.objectValue = objectValue;
    }

    @HealthCheckRemoveFromRotation("Not Bean value")
    public String notBeanValue()
    {
        return notBeanValue;
    }

    public void setNotBeanValue(String value)
    {
        notBeanValue = value;
    }

    public void setNotHealthCheck(String value)
    {
        this.notHealthCheck = value;
    }

    public String getNotHealthCheck()
    {
        return notHealthCheck;
    }

    @HealthCheckRemoveFromRotation("Private value")
    private String getPrivateValue()
    {
        return privateValue;
    }

    private void setPrivateValue(String privateValue)
    {
        this.privateValue = privateValue;
    }

    private void setFieldValue(String value)
    {
        fieldValue.set(value);
    }
}
