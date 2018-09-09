package com.proofpoint.reporting;

public interface SimpleHealthInterface
{
    String getStringValue();

    void setStringValue(String stringValue);

    Object getObjectValue();

    void setObjectValue(Object objectValue);

    String notBeanValue();

    void setNotBeanValue(String value);

    void setNotHealthCheck(String value);

    String getNotHealthCheck();
}
