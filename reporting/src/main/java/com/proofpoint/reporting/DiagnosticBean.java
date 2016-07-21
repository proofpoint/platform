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
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.proofpoint.reporting.ReflectionUtils.getAttributeName;
import static com.proofpoint.reporting.ReflectionUtils.isNoArgsReturnsValue;
import static java.util.Objects.requireNonNull;

class DiagnosticBean
{
    private final Map<String, ReportedBeanAttribute> attributes;

    private DiagnosticBean(Collection<ReportedBeanAttribute> attributes)
    {
        Map<String, ReportedBeanAttribute> attributesBuilder = new TreeMap<>();
        for (ReportedBeanAttribute attribute : attributes) {
            attributesBuilder.put(attribute.getName(), attribute);
        }
        this.attributes = Collections.unmodifiableMap(attributesBuilder);
    }

    Collection<ReportedBeanAttribute> getAttributes()
    {
        return attributes.values();
    }

    Object getAttribute(String name)
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        requireNonNull(name, "name is null");
        ReportedBeanAttribute mbeanAttribute = attributes.get(name);
        if (mbeanAttribute == null) {
            throw new AttributeNotFoundException(name);
        }
        return mbeanAttribute.getValue(null);
    }

    static DiagnosticBean forTarget(Object target)
    {
        requireNonNull(target, "target is null");

        List<ReportedBeanAttribute> attributes = new ArrayList<>();

        Map<String, DiagnosticBeanAttributeBuilder> attributeBuilders = new TreeMap<>();

        for (Map.Entry<Method, Method> entry : AnnotationUtils.findAnnotatedMethods(target.getClass(), Diagnostic.class).entrySet()) {
            Method concreteMethod = entry.getKey();
            Method annotatedMethod = entry.getValue();

            if (!isNoArgsReturnsValue(concreteMethod)) {
                throw new RuntimeException("diagnostic annotation on non-getter " + annotatedMethod.toGenericString());
            }

            String attributeName = getAttributeName(concreteMethod);

            DiagnosticBeanAttributeBuilder attributeBuilder = attributeBuilders.get(attributeName);
            if (attributeBuilder == null) {
                attributeBuilder = new DiagnosticBeanAttributeBuilder().named(attributeName).onInstance(target);
            }

            attributeBuilder = attributeBuilder
                    .withConcreteGetter(concreteMethod)
                    .withAnnotatedGetter(annotatedMethod);

            attributeBuilders.put(attributeName, attributeBuilder);
        }

        for (DiagnosticBeanAttributeBuilder attributeBuilder : attributeBuilders.values()) {
            attributes.addAll(attributeBuilder.build());
        }

        return new DiagnosticBean(attributes);
    }
}
