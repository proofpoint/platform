/*
 * Copyright 2018 Proofpoint, Inc.
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

import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.lang.reflect.Method;

import static com.proofpoint.reporting.ReflectionUtils.invoke;

class NestedPrometheusBeanAttribute implements PrometheusBeanAttribute
{
    private final Method nestedGetter;
    private final PrometheusBeanAttribute delegate;
    private final String name;

    NestedPrometheusBeanAttribute(String prefix, Method nestedGetter, PrometheusBeanAttribute delegate)
    {
        this.nestedGetter = nestedGetter;
        this.delegate = delegate;
        name = prefix + "." + delegate.getName();
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getType()
    {
        return delegate.getType();
    }

    @Override
    public Object getValue(Object target)
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        if (target != null) {
            target = invoke(target, nestedGetter);
        }
        return delegate.getValue(target);
    }
}
