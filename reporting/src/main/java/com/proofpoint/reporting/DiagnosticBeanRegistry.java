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

import com.google.auto.value.AutoValue;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.collect.Maps.newConcurrentMap;
import static com.proofpoint.reporting.DiagnosticBeanRegistry.RegistrationInfo.registrationInfo;
import static java.util.Objects.requireNonNull;

class DiagnosticBeanRegistry
{
    private final ConcurrentMap<Reference, RegistrationInfo> reportedBeans = newConcurrentMap();

    Collection<RegistrationInfo> getDiagnosticBeans()
    {
        return reportedBeans.values();
    }

    void register(Object object, ReportedBean reportedBean, String namePrefix)
            throws InstanceAlreadyExistsException
    {
        requireNonNull(object, "object is null");
        if (reportedBeans.putIfAbsent(new Reference(object), registrationInfo(reportedBean, namePrefix)) != null) {
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
        static RegistrationInfo registrationInfo(ReportedBean reportedBean, String namePrefix)
        {
            return new AutoValue_DiagnosticBeanRegistry_RegistrationInfo(reportedBean, namePrefix);
        }

        abstract ReportedBean getReportedBean();

        abstract String getNamePrefix();
    }
}
