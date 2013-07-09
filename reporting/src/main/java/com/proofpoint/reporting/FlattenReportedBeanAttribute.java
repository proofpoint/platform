/*
 *  Copyright 2010 Dain Sundstrom
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

import javax.management.AttributeNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.lang.reflect.Method;

import static com.proofpoint.reporting.ReflectionUtils.invoke;

class FlattenReportedBeanAttribute implements ReportedBeanAttribute
{
    private final Method flattenGetter;
    private final ReportedBeanAttribute delegate;
    private final MBeanAttributeInfo info;

    public FlattenReportedBeanAttribute(String prefix, Method flattenGetter, ReportedBeanAttribute delegate)
    {
        this.flattenGetter = flattenGetter;
        this.delegate = delegate;
        this.info = delegate.getInfo();
    }

    public MBeanAttributeInfo getInfo()
    {
        return info;
    }

    public String getName()
    {
        return info.getName();
    }

    public Number getValue(Object target)
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        if (target != null) {
            target = invoke(target, flattenGetter);
        }
        return delegate.getValue(target);
    }
}