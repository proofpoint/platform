package com.proofpoint.reporting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class CustomAnnotationObject
        extends SimpleBucketed
        implements SimpleInterface
{
    private boolean booleanValue;
    private Boolean booleanBoxedValue;
    private byte byteValue;
    private Byte byteBoxedValue;
    private short shortValue;
    private Short shortBoxedValue;
    private int integerValue;
    private Integer integerBoxedValue;
    private long longValue;
    private Long longBoxedValue;
    private float floatValue;
    private Float floatBoxedValue;
    private double doubleValue;
    private Double doubleBoxedValue;
    private String stringValue;
    private Object objectValue;
    private int privateValue;

    private int notReported;

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    @ReportedAnnotation
    public @interface Reported1
    {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    @ReportedAnnotation
    public @interface Reported2
    {
    }

    @Override
    @Reported1
    public boolean isBooleanValue()
    {
        return booleanValue;
    }

    @Override
    public void setBooleanValue(boolean booleanValue)
    {
        this.booleanValue = booleanValue;
    }

    @Override
    @Reported2
    public Boolean isBooleanBoxedValue()
    {
        return booleanBoxedValue;
    }

    @Override
    public void setBooleanBoxedValue(Boolean booleanBoxedValue)
    {
        this.booleanBoxedValue = booleanBoxedValue;
    }

    @Override
    @Reported1
    public byte getByteValue()
    {
        return byteValue;
    }

    @Override
    public void setByteValue(byte byteValue)
    {
        this.byteValue = byteValue;
    }

    @Override
    @Reported2
    public Byte getByteBoxedValue()
    {
        return byteBoxedValue;
    }

    @Override
    public void setByteBoxedValue(Byte byteBoxedValue)
    {
        this.byteBoxedValue = byteBoxedValue;
    }

    @Override
    @Reported2
    public short getShortValue()
    {
        return shortValue;
    }

    @Override
    public void setShortValue(short shortValue)
    {
        this.shortValue = shortValue;
    }

    @Override
    @Reported1
    public Short getShortBoxedValue()
    {
        return shortBoxedValue;
    }

    @Override
    public void setShortBoxedValue(Short shortBoxedValue)
    {
        this.shortBoxedValue = shortBoxedValue;
    }

    @Override
    @Reported1
    public int getIntegerValue()
    {
        return integerValue;
    }

    @Override
    public void setIntegerValue(int integerValue)
    {
        this.integerValue = integerValue;
    }

    @Override
    @Reported2
    public Integer getIntegerBoxedValue()
    {
        return integerBoxedValue;
    }

    @Override
    public void setIntegerBoxedValue(Integer integerBoxedValue)
    {
        this.integerBoxedValue = integerBoxedValue;
    }

    @Override
    @Reported1
    public long getLongValue()
    {
        return longValue;
    }

    @Override
    public void setLongValue(long longValue)
    {
        this.longValue = longValue;
    }

    @Override
    @Reported1
    public Long getLongBoxedValue()
    {
        return longBoxedValue;
    }

    @Override
    public void setLongBoxedValue(Long longBoxedValue)
    {
        this.longBoxedValue = longBoxedValue;
    }

    @Override
    @Reported2
    public float getFloatValue()
    {
        return floatValue;
    }

    @Override
    public void setFloatValue(float floatValue)
    {
        this.floatValue = floatValue;
    }

    @Override
    @Reported1
    public Float getFloatBoxedValue()
    {
        return floatBoxedValue;
    }

    @Override
    public void setFloatBoxedValue(Float floatBoxedValue)
    {
        this.floatBoxedValue = floatBoxedValue;
    }

    @Override
    @Reported1
    public double getDoubleValue()
    {
        return this.doubleValue;
    }

    @Override
    public void setDoubleValue(double doubleValue)
    {
        this.doubleValue = doubleValue;
    }

    @Override
    @Reported1
    public Double getDoubleBoxedValue()
    {
        return doubleBoxedValue;
    }

    @Override
    public void setDoubleBoxedValue(Double doubleBoxedValue)
    {
        this.doubleBoxedValue = doubleBoxedValue;
    }

    @Override
    public void setNotReported(int value)
    {
        this.notReported = value;
    }

    @Override
    public int getNotReported()
    {
        return notReported;
    }

    @Override
    @Reported1
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
    @Reported1
    public Object getObjectValue()
    {
        return objectValue;
    }

    @Override
    public void setObjectValue(Object objectValue)
    {
        this.objectValue = objectValue;
    }

    @Reported1
    private int getPrivateValue()
    {
        return privateValue;
    }

    private void setPrivateValue(int privateValue)
    {
        this.privateValue = privateValue;
    }
}
