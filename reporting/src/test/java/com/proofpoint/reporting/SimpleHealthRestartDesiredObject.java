package com.proofpoint.reporting;

import java.util.concurrent.atomic.AtomicReference;

public class SimpleHealthRestartDesiredObject
        implements SimpleHealthInterface
{
    private String stringValue;
    private Object objectValue;
    private String notBeanValue;
    private String privateValue;

    @HealthCheckRestartDesired("Field value")
    private final AtomicReference<String> fieldValue = new AtomicReference<>();

    private String notHealthCheck;


    @HealthCheckRestartDesired("String value")
    public String getStringValue()
    {
        return stringValue;
    }

    public void setStringValue(String stringValue)
    {
        this.stringValue = stringValue;
    }

    @HealthCheckRestartDesired("Object value")
    public Object getObjectValue()
    {
        return objectValue;
    }

    public void setObjectValue(Object objectValue)
    {
        this.objectValue = objectValue;
    }

    @HealthCheckRestartDesired("Not Bean value")
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

    @HealthCheckRestartDesired("Private value")
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
