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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Nested;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class AnnotationUtils
{
    private static final Set<Class<? extends Annotation>> FLATTEN_OR_NESTED_ANNOTATION_SET = Set.of(Nested.class, Flatten.class);
    private static final Set<Class<? extends Annotation>> FLATTEN_ANNOTATION_SET = Set.of(Flatten.class);
    private static final Set<Class<? extends Annotation>> NESTED_ANNOTATION_SET = Set.of(Nested.class);
    private static final Set<Class<? extends Annotation>> REPORTED_ANNOTATION_SET = Set.of(ReportedAnnotation.class);

    private AnnotationUtils()
    {
    }

    /**
     * Find methods that are tagged with a particular annotation somewhere in the hierarchy
     *
     * @param clazz the class to analyze
     * @param annotationClass the annotations to look for
     * @return a map that associates a concrete method to the actual method tagged as reported
     *         (which may belong to a different class in clazz's hierarchy)
     */
    @SafeVarargs
    static Map<Method, Method> findAnnotatedMethods(Class<?> clazz, Class<? extends Annotation>... annotationClass)
    {
        Map<Method, Method> result = new HashMap<>();
        Set<Signature> foundMethods = new HashSet<>();
        findAnnotatedMethods(clazz, result, foundMethods, ImmutableSet.copyOf(annotationClass));

        return result;
    }

    private static void findAnnotatedMethods(Class<?> clazz, Map<Method, Method> result, Set<Signature> foundMethods, Set<Class<? extends Annotation>> annotationSet)
    {
        // gather all available methods
        // this returns everything, even if it's declared in a parent
        for (Method method : clazz.getDeclaredMethods()) {
            // skip methods that are used internally by the vm for implementing covariance, etc
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }

            Signature methodSignature = new Signature(method);
            if (foundMethods.contains(methodSignature)) {
                continue;
            }
            foundMethods.add(methodSignature);

            // look for annotations recursively in superclasses or interfaces
            Method reportedGetter = findAnnotatedMethod(clazz, method.getName(), method.getParameterTypes(), annotationSet);
            if (reportedGetter != null) {
                method.setAccessible(true);
                reportedGetter.setAccessible(true);
                result.put(method, reportedGetter);
            }
        }

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            findAnnotatedMethods(superclass, result, foundMethods, annotationSet);
        }

        for (Class<?> iface : clazz.getInterfaces()) {
            findAnnotatedMethods(iface, result, foundMethods, annotationSet);
        }
    }

    private static Method findAnnotatedMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Set<Class<? extends Annotation>> annotationSet)
    {
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            if (isAnnotatedMethod(method, annotationSet)) {
                return method;
            }
        }
        catch (NoSuchMethodException ignored) {
        }

        if (clazz.getSuperclass() != null) {
            Method reportedGetter = findAnnotatedMethod(clazz.getSuperclass(), methodName, parameterTypes, annotationSet);
            if (reportedGetter != null) {
                return reportedGetter;
            }
        }

        for (Class<?> iface : clazz.getInterfaces()) {
            Method reportedGetter = findAnnotatedMethod(iface, methodName, parameterTypes, annotationSet);
            if (reportedGetter != null) {
                return reportedGetter;
            }
        }

        return null;
    }

    /**
     * Find fields that are tagged with a particular annotation somewhere in the hierarchy
     *
     * @param clazz the class to analyze
     * @param annotationClass the annotations to look for
     * @return a collection of fields
     */
    @SafeVarargs
    static Collection<Field> findAnnotatedFields(Class<?> clazz, Class<? extends Annotation>... annotationClass)
    {
        Set<Field> result = new HashSet<>();
        findAnnotatedFields(clazz, result, ImmutableSet.copyOf(annotationClass));

        return result;
    }

    private static void findAnnotatedFields(Class<?> clazz, Set<Field> result, Set<Class<? extends Annotation>> annotationSet)
    {
        // gather all available fields
        // this returns everything, even if it's declared in a parent
        for (Field field : clazz.getDeclaredFields()) {
            // skip fields that are used internally by the vm for implementing covariance, etc
            if (field.isSynthetic()) {
                continue;
            }

            if (isAnnotationPresent(annotationSet, new HashSet<>(), field.getDeclaredAnnotations())) {
                field.setAccessible(true);
                result.add(field);
            }
        }

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            findAnnotatedFields(superclass, result, annotationSet);
        }

        for (Class<?> iface : clazz.getInterfaces()) {
            findAnnotatedFields(iface, result, annotationSet);
        }
    }

    private static boolean isAnnotatedMethod(Method method, Set<Class<? extends Annotation>> annotationSet)
    {
        return isAnnotationPresent(Sets.union(annotationSet, FLATTEN_OR_NESTED_ANNOTATION_SET), new HashSet<>(), method.getAnnotations());
    }

    static boolean isFlatten(Method method)
    {
        return method != null && isAnnotationPresent(FLATTEN_ANNOTATION_SET, new HashSet<>(), method.getAnnotations());
    }

    static boolean isNested(Method method)
    {
        return method != null && isAnnotationPresent(NESTED_ANNOTATION_SET, new HashSet<>(), method.getAnnotations());
    }

    static boolean isReported(Method method)
    {
        return method != null && isAnnotationPresent(REPORTED_ANNOTATION_SET, new HashSet<>(), method.getAnnotations());
    }

    private static boolean isAnnotationPresent(Set<Class<? extends Annotation>> annotationClasses, Set<Class<? extends Annotation>> processedTypes, Annotation... annotations)
    {
        // are any of the annotations the specified annotation
        for (Annotation annotation : annotations) {
            for (Class<? extends Annotation> annotationClass : annotationClasses) {
                if (annotationClass.isInstance(annotation)) {
                    return true;
                }
            }
        }

        // are any of the annotations annotated with the specified annotation
        for (Annotation annotation : annotations) {
            if (processedTypes.add(annotation.annotationType()) && isAnnotationPresent(annotationClasses, processedTypes, annotation.annotationType().getAnnotations())) {
                return true;
            }
        }

        return false;
    }
}
