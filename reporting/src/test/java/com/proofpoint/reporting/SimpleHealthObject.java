package com.proofpoint.reporting;

public class SimpleHealthObject
        implements SimpleHealthInterface
{
    private String stringValue;
    private Object objectValue;
    private String notBeanValue;
    private String privateValue;

    private String notHealthCheck;


    @HealthCheck("String value")
    public String getStringValue()
    {
        return stringValue;
    }

    public void setStringValue(String stringValue)
    {
        this.stringValue = stringValue;
    }

    @HealthCheck("Object value")
    public Object getObjectValue()
    {
        return objectValue;
    }

    public void setObjectValue(Object objectValue)
    {
        this.objectValue = objectValue;
    }

    @HealthCheck("Not Bean value")
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

    @HealthCheck("Private value")
    private String getPrivateValue()
    {
        return privateValue;
    }

    private void setPrivateValue(String privateValue)
    {
        this.privateValue = privateValue;
    }
}
