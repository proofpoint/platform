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

import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.lang.reflect.Method;

import static com.proofpoint.reporting.ReflectionUtils.invoke;
import static java.util.Objects.requireNonNull;

record MethodHealthBeanAttribute(String getDescription, HealthBeanAttribute.Type getType, Object target, Method getter)
        implements HealthBeanAttribute
{
    MethodHealthBeanAttribute
    {
        requireNonNull(getDescription, "description is null");
        requireNonNull(getType, "type is null");
        requireNonNull(target, "target is null");
        requireNonNull(getter, "getter is null");
    }

    @Override
    public String getValue()
            throws MBeanException, ReflectionException
    {
        Object value = invoke(target(), getter());
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
