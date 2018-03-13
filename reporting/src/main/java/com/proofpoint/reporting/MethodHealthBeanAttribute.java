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

import com.google.auto.value.AutoValue;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.lang.reflect.Method;

import static com.proofpoint.reporting.ReflectionUtils.invoke;

@AutoValue
abstract class MethodHealthBeanAttribute
        implements HealthBeanAttribute
{
    static MethodHealthBeanAttribute methodHealthBeanAttribute(String description, Type type, Object target, Method getter)
    {
        return new AutoValue_MethodHealthBeanAttribute(description, type, target, getter);
    }

    abstract Object getTarget();

    abstract Method getGetter();

    @Override
    public String getValue()
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        Object value = invoke(getTarget(), getGetter());
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
