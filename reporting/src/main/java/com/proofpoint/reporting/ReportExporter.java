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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.proofpoint.reporting.ReportException.Reason;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.proofpoint.reporting.AnnotationUtils.findAnnotatedMethods;
import static com.proofpoint.reporting.AnnotationUtils.isFlatten;
import static com.proofpoint.reporting.AnnotationUtils.isNested;
import static java.util.Objects.requireNonNull;

/**
 * Dynamically exports and unexports reporting-annotated objects to the reporting
 * subsystem.
 */
public class ReportExporter
{
    private final ReportedBeanRegistry registry;
    private final BucketIdProvider bucketIdProvider;

    @Inject
    ReportExporter(ReportedBeanRegistry registry, BucketIdProvider bucketIdProvider)
    {
        this.registry = requireNonNull(registry, "registry is null");
        this.bucketIdProvider = requireNonNull(bucketIdProvider, "bucketIdProvider is null");
    }

    /**
     * Export an object to the metrics reporting system.
     *
     * @param object The object to export
     * @param applicationPrefix Whether to prefix the metric name with the application name
     * @param namePrefix Name prefix for all metrics reported out of the object
     * @param tags Tags for all metrics reported out of the object
     */
    public void export(Object object, boolean applicationPrefix, String namePrefix, Map<String, String> tags)
    {
        ReportedBean reportedBean = ReportedBean.forTarget(object);
        notifyBucketIdProvider(object, bucketIdProvider, null);
        if (!reportedBean.getAttributes().isEmpty()) {
            try {
                registry.register(object, reportedBean, applicationPrefix, namePrefix, tags);
            }
            catch (InstanceAlreadyExistsException e) {
                throw new ReportException(Reason.INSTANCE_ALREADY_EXISTS, e.getMessage());
            }
        }
    }

    /**
     * Undo the export of an object to the metrics reporting system.
     *
     * @param object The object to unexport
     */
    public void unexportObject(Object object)
    {
        try {
            registry.unregister(object);
        }
        catch (InstanceNotFoundException e) {
            throw new ReportException(Reason.INSTANCE_NOT_FOUND, e.getMessage());
        }
    }

    /**
     * @deprecated Use {@link #export(Object, boolean, String, Map)}
     */
    @Deprecated
    public void export(String name, Object object)
    {
        ObjectName objectName;
        try {
            objectName = new ObjectName(name);
        }
        catch (MalformedObjectNameException e) {
            throw new ReportException(Reason.MALFORMED_OBJECT_NAME, e.getMessage());
        }

        export(objectName, object);
    }

    /**
     * @deprecated Use {@link #export(Object, boolean, String, Map)}
     */
    @Deprecated
    public void export(ObjectName objectName, Object object)
    {
        ReportedBean reportedBean = ReportedBean.forTarget(object);
        notifyBucketIdProvider(object, bucketIdProvider, null);
        if (!reportedBean.getAttributes().isEmpty()) {
            try {
                registry.register(reportedBean, objectName);
            }
            catch (InstanceAlreadyExistsException e) {
                throw new ReportException(Reason.INSTANCE_ALREADY_EXISTS, e.getMessage());
            }
        }
    }

    /**
     * @deprecated Use {@link #unexportObject(Object)} after exporting with {@link #export(Object, boolean, String, Map)}
     */
    @Deprecated
    public void unexport(String name)
    {
        ObjectName objectName;

        try {
            objectName = new ObjectName(name);
        }
        catch (MalformedObjectNameException e) {
            throw new ReportException(Reason.MALFORMED_OBJECT_NAME, e.getMessage());
        }

        unexport(objectName);
    }

    /**
     * @deprecated Use {@link #unexportObject(Object)} after exporting with {@link #export(Object, boolean, String, Map)}
     */
    @Deprecated
    public void unexport(ObjectName objectName)
    {
        try {
            registry.unregisterLegacy(objectName);
        }
        catch (InstanceNotFoundException e) {
            throw new ReportException(Reason.INSTANCE_NOT_FOUND, e.getMessage());
        }
    }

    @VisibleForTesting
    static void notifyBucketIdProvider(Object object, BucketIdProvider bucketIdProvider, Method annotatedGetter)
    {
        if (object instanceof Bucketed) {
            ((Bucketed<?>) object).setBucketIdProvider(bucketIdProvider);
        }
        else if (annotatedGetter != null && !isNested(annotatedGetter) && !isFlatten(annotatedGetter)) {
            return;
        }

        try {
            for (Entry<Method, Method> entry : findAnnotatedMethods(object.getClass(), ReportedAnnotation.class, Prometheus.class).entrySet()) {
                notifyBucketIdProvider(entry.getKey().invoke(object), bucketIdProvider, entry.getValue());
            }
        }
        catch (IllegalAccessException | InvocationTargetException e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }
}
