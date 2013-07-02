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

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.ReflectionException;
import javax.management.modelmbean.ModelMBeanConstructorInfo;
import javax.management.modelmbean.ModelMBeanNotificationInfo;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.reporting.ReflectionUtils.getAttributeName;
import static com.proofpoint.reporting.ReflectionUtils.isGetter;

class ReportedBean
{
    private final MBeanInfo mbeanInfo;
    private final Map<String, ReportedBeanAttribute> attributes;

    public ReportedBean(String className, Collection<ReportedBeanAttribute> attributes)
    {
        List<MBeanAttributeInfo> attributeInfos = new ArrayList<>();
        Map<String, ReportedBeanAttribute> attributesBuilder = new TreeMap<>();
        for (ReportedBeanAttribute attribute : attributes) {
            attributesBuilder.put(attribute.getName(), attribute);
            attributeInfos.add(attribute.getInfo());
        }
        this.attributes = Collections.unmodifiableMap(attributesBuilder);

        mbeanInfo = new MBeanInfo(className,
                null,
                attributeInfos.toArray(new MBeanAttributeInfo[attributeInfos.size()]),
                new ModelMBeanConstructorInfo[0],
                null,
                new ModelMBeanNotificationInfo[0]);
    }

    public MBeanInfo getMBeanInfo()
    {
        return mbeanInfo;
    }

    public Collection<ReportedBeanAttribute> getAttributes()
    {
        return attributes.values();
    }

    public Object getAttribute(String name)
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        checkNotNull(name, "name is null");
        ReportedBeanAttribute mbeanAttribute = attributes.get(name);
        if (mbeanAttribute == null) {
            throw new AttributeNotFoundException(name);
        }
        Object value = mbeanAttribute.getValue();
        return value;
    }

    public static ReportedBean forTarget(Object target)
    {
        checkNotNull(target, "target is null");

        Map<String, ReportedBeanAttributeBuilder> attributeBuilders = new TreeMap<>();

        for (Map.Entry<Method, Method> entry : AnnotationUtils.findReportedGetters(target.getClass()).entrySet()) {
            Method concreteMethod = entry.getKey();
            Method annotatedMethod = entry.getValue();

            if (isGetter(concreteMethod)) {
                String attributeName = getAttributeName(concreteMethod);

                ReportedBeanAttributeBuilder attributeBuilder = attributeBuilders.get(attributeName);
                if (attributeBuilder == null) {
                    attributeBuilder = new ReportedBeanAttributeBuilder().named(attributeName).onInstance(target);
                }

                attributeBuilder = attributeBuilder
                        .withConcreteGetter(concreteMethod)
                        .withAnnotatedGetter(annotatedMethod);

                attributeBuilders.put(attributeName, attributeBuilder);
            }
            else {
                throw new RuntimeException("report annotation on non-getter " + annotatedMethod.getName()); // todo - more useful exception
            }
        }

        String className = target.getClass().getName();
        List<ReportedBeanAttribute> attributes = new ArrayList<>();
        for (ReportedBeanAttributeBuilder attributeBuilder : attributeBuilders.values()) {
            attributes.addAll(attributeBuilder.build());
        }

        return new ReportedBean(className, attributes);
    }
}
