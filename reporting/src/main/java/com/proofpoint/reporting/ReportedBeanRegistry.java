/*
 * Copyright 2013 Proofpoint, Inc.
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.collect.Maps.newConcurrentMap;
import static com.proofpoint.reporting.ReportedBeanRegistry.RegistrationInfo.registrationInfo;
import static java.util.Objects.requireNonNull;

class ReportedBeanRegistry
{
    private static final Pattern QUOTED_PATTERN = Pattern.compile("\"(.*)\"");
    private static final Pattern BACKQUOTE_PATTERN = Pattern.compile("\\\\(.)");

    private final ConcurrentMap<Reference, RegistrationInfo> reportedBeans = newConcurrentMap();
    private final ConcurrentMap<ObjectName, ReportedBean> legacyReportedBeans = newConcurrentMap();

    Collection<RegistrationInfo> getReportedBeans()
    {
        return reportedBeans.values();
    }

    void register(Object object, ReportedBean reportedBean, String namePrefix, Map<String, String> tags)
            throws InstanceAlreadyExistsException
    {
        requireNonNull(object, "object is null");
        if (reportedBeans.putIfAbsent(new Reference(object), registrationInfo(reportedBean, namePrefix, tags)) != null) {
            throw new InstanceAlreadyExistsException(object + " is already registered");
        }
    }

    void unregister(Object object)
            throws InstanceNotFoundException
    {
        if (reportedBeans.remove(new Reference(object)) == null) {
            throw new InstanceNotFoundException(object + " not found");
        }
    }

    void register(ReportedBean reportedBean, ObjectName name)
            throws InstanceAlreadyExistsException
    {
        if (name == null) {
            throw new UnsupportedOperationException("Only explicit name supported");
        }

        StringBuilder nameBuilder = new StringBuilder();
        if (name.getKeyProperty("type") != null) {
            nameBuilder.append(LOWER_CAMEL.to(UPPER_CAMEL, dequote(name.getKeyProperty("type"))));
            if (name.getKeyProperty("name") != null) {
                nameBuilder.append(".");
            }
        }
        if (name.getKeyProperty("name") != null) {
            nameBuilder.append(LOWER_CAMEL.to(UPPER_CAMEL, dequote(name.getKeyProperty("name"))));
        }

        Builder<String, String> tagsBuilder = ImmutableMap.builder();
        for (Entry<String, String> entry : name.getKeyPropertyList().entrySet()) {
            if (!entry.getKey().equals("type") && !entry.getKey().equals("name")) {
                tagsBuilder.put(entry.getKey(), dequote(entry.getValue()));
            }
        }

        if (legacyReportedBeans.putIfAbsent(name, reportedBean) != null) {
            throw new InstanceAlreadyExistsException(name + " is already registered");
        }

        try {
            register(reportedBean, reportedBean, nameBuilder.toString(), tagsBuilder.build());
        }
        catch (InstanceAlreadyExistsException e) {
            legacyReportedBeans.remove(name);
            throw e;
        }
    }

    void unregisterLegacy(ObjectName name)
            throws InstanceNotFoundException
    {
        ReportedBean remove = legacyReportedBeans.remove(name);
        if (remove == null) {
            throw new InstanceNotFoundException(name.getCanonicalName() + " not found");
        }
        reportedBeans.remove(new Reference(remove));
    }

    private static String dequote(String value)
    {
        Matcher matcher = QUOTED_PATTERN.matcher(value);
        String dequoted;
        if (matcher.matches()) {
            dequoted = BACKQUOTE_PATTERN.matcher(matcher.group(1)).replaceAll("$1");
        }
        else {
            dequoted = value;
        }
        return dequoted;
    }

    private static class Reference
    {
        private final Object referent;

        Reference(Object referent) {
            this.referent = requireNonNull(referent);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Reference reference = (Reference) o;

            return referent == reference.referent;
        }

        @Override
        public int hashCode()
        {
            return referent.hashCode();
        }
    }

    @AutoValue
    abstract static class RegistrationInfo
    {
        static RegistrationInfo registrationInfo(ReportedBean reportedBean, String namePrefix, Map<String, String> tags)
        {
            return new AutoValue_ReportedBeanRegistry_RegistrationInfo(reportedBean, namePrefix, ImmutableMap.copyOf(tags));
        }

        abstract ReportedBean getReportedBean();

        abstract String getNamePrefix();

        abstract Map<String, String> getTags();
    }
}
