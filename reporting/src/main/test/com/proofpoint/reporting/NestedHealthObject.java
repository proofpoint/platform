package com.proofpoint.reporting;

import org.weakref.jmx.Nested;

public class NestedHealthObject
{
    private final SimpleHealthObject simpleHealthObject = new SimpleHealthObject();

    @Nested
    public SimpleHealthObject getSimpleHealthObject()
    {
        return simpleHealthObject;
    }
}