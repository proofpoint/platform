/*
 * Copyright 2016 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.reporting;

public class SimpleDiagnosticObject
        implements SimpleDiagnosticInterface
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

    private int notDiagnostic;

    @Diagnostic
    public boolean isBooleanValue()
    {
        return booleanValue;
    }

    public void setBooleanValue(boolean booleanValue)
    {
        this.booleanValue = booleanValue;
    }

    @Diagnostic
    public Boolean isBooleanBoxedValue()
    {
        return booleanBoxedValue;
    }

    public void setBooleanBoxedValue(Boolean booleanBoxedValue)
    {
        this.booleanBoxedValue = booleanBoxedValue;
    }

    @Diagnostic
    public byte getByteValue()
    {
        return byteValue;
    }

    public void setByteValue(byte byteValue)
    {
        this.byteValue = byteValue;
    }

    @Diagnostic
    public Byte getByteBoxedValue()
    {
        return byteBoxedValue;
    }

    public void setByteBoxedValue(Byte byteBoxedValue)
    {
        this.byteBoxedValue = byteBoxedValue;
    }

    @Diagnostic
    public short getShortValue()
    {
        return shortValue;
    }

    public void setShortValue(short shortValue)
    {
        this.shortValue = shortValue;
    }

    @Diagnostic
    public Short getShortBoxedValue()
    {
        return shortBoxedValue;
    }

    public void setShortBoxedValue(Short shortBoxedValue)
    {
        this.shortBoxedValue = shortBoxedValue;
    }

    @Diagnostic
    public int getIntegerValue()
    {
        return integerValue;
    }

    public void setIntegerValue(int integerValue)
    {
        this.integerValue = integerValue;
    }

    @Diagnostic
    public Integer getIntegerBoxedValue()
    {
        return integerBoxedValue;
    }

    public void setIntegerBoxedValue(Integer integerBoxedValue)
    {
        this.integerBoxedValue = integerBoxedValue;
    }

    @Diagnostic
    public long getLongValue()
    {
        return longValue;
    }

    public void setLongValue(long longValue)
    {
        this.longValue = longValue;
    }

    @Diagnostic
    public Long getLongBoxedValue()
    {
        return longBoxedValue;
    }

    public void setLongBoxedValue(Long longBoxedValue)
    {
        this.longBoxedValue = longBoxedValue;
    }

    @Diagnostic
    public float getFloatValue()
    {
        return floatValue;
    }

    public void setFloatValue(float floatValue)
    {
        this.floatValue = floatValue;
    }

    @Diagnostic
    public Float getFloatBoxedValue()
    {
        return floatBoxedValue;
    }

    public void setFloatBoxedValue(Float floatBoxedValue)
    {
        this.floatBoxedValue = floatBoxedValue;
    }

    @Diagnostic
    public double getDoubleValue()
    {
        return doubleValue;
    }

    public void setDoubleValue(double doubleValue)
    {
        this.doubleValue = doubleValue;
    }

    @Diagnostic
    public Double getDoubleBoxedValue()
    {
        return doubleBoxedValue;
    }

    public void setDoubleBoxedValue(Double doubleBoxedValue)
    {
        this.doubleBoxedValue = doubleBoxedValue;
    }

    public void setNotDiagnostic(int value)
    {
        this.notDiagnostic = value;
    }

    public int getNotDiagnostic()
    {
        return notDiagnostic;
    }

    @Diagnostic
    public String getStringValue()
    {
        return stringValue;
    }

    public void setStringValue(String stringValue)
    {
        this.stringValue = stringValue;
    }

    @Diagnostic
    public Object getObjectValue()
    {
        return objectValue;
    }

    public void setObjectValue(Object objectValue)
    {
        this.objectValue = objectValue;
    }

    @Diagnostic
    private int getPrivateValue()
    {
        return privateValue;
    }

    private void setPrivateValue(int privateValue)
    {
        this.privateValue = privateValue;
    }
}
