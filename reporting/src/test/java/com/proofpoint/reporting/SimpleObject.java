/*
 *  Copyright 2009 Martin Traverso
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.proofpoint.reporting;

public class SimpleObject
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

    @Override
    @Reported
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
    @Reported
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
    @Reported
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
    @Reported
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
    @Reported
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
    @Reported
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
    @Reported
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
    @Reported
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
    @Reported
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
    @Reported
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
    @Reported
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
    @Reported
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
    @Reported
    public double getDoubleValue()
    {
        return doubleValue;
    }

    @Override
    public void setDoubleValue(double doubleValue)
    {
        this.doubleValue = doubleValue;
    }

    @Override
    @Reported
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
    @Reported
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
    @Reported
    public Object getObjectValue()
    {
        return objectValue;
    }

    @Override
    public void setObjectValue(Object objectValue)
    {
        this.objectValue = objectValue;
    }

    @Reported
    private int getPrivateValue()
    {
        return privateValue;
    }

    private void setPrivateValue(int privateValue)
    {
        this.privateValue = privateValue;
    }
}
