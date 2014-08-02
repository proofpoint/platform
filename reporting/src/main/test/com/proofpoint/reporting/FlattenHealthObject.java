package com.proofpoint.reporting;

import org.weakref.jmx.Flatten;

public class FlattenHealthObject
{
    private final SimpleHealthObject simpleHealthObject = new SimpleHealthObject();

    @Flatten
    public SimpleHealthObject getSimpleHealthObject()
    {
        return simpleHealthObject;
    }
}