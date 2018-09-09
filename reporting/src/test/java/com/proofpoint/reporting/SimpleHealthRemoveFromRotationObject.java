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


    @Override
    @HealthCheckRemoveFromRotation("String value")
    public String getStringValue()
    {
        return stringValue;
    }

    @Override
    public void setStringValue(String stringValue)
    {
        this.stringValue = stringValue;
    }

    @Override
    @HealthCheckRemoveFromRotation("Object value")
    public Object getObjectValue()
    {
        return objectValue;
    }

    @Override
    public void setObjectValue(Object objectValue)
    {
        this.objectValue = objectValue;
    }

    @Override
    @HealthCheckRemoveFromRotation("Not Bean value")
    public String notBeanValue()
    {
        return notBeanValue;
    }

    @Override
    public void setNotBeanValue(String value)
    {
        notBeanValue = value;
    }

    @Override
    public void setNotHealthCheck(String value)
    {
        this.notHealthCheck = value;
    }

    @Override
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
