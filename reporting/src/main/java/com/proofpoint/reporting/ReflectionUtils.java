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
import javax.management.RuntimeErrorException;
import javax.management.RuntimeOperationsException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static java.util.Objects.requireNonNull;

final class ReflectionUtils
{
    private ReflectionUtils()
    {
    }

    private static final Pattern getterOrSetterPattern = Pattern.compile("(get|set|is)(.+)");

    static Object invoke(Object target, Method method)
            throws MBeanException, ReflectionException
    {
        requireNonNull(target, "target is null");
        requireNonNull(method, "method is null");

        try {
            return method.invoke(target);
        }
        catch (InvocationTargetException e) {
            // unwrap exception
            Throwable targetException = e.getTargetException();
            if (targetException instanceof RuntimeException runtimeException) {
                throw new MBeanException(
                        runtimeException,
                        "RuntimeException occurred while invoking " + toSimpleName(method));
            }
            else if (targetException instanceof ReflectionException reflectionException) {
                // allow ReflectionException to passthrough
                throw reflectionException;
            }
            else if (targetException instanceof MBeanException mBeanException) {
                // allow MBeanException to passthrough
                throw mBeanException;
            }
            else if (targetException instanceof Exception x) {
                throw new MBeanException(
                        x,
                        "Exception occurred while invoking " + toSimpleName(method));
            }
            else if (targetException instanceof Error error) {
                throw new RuntimeErrorException(
                        error,
                        "Error occurred while invoking " + toSimpleName(method));
            }
            else {
                throw new RuntimeErrorException(
                        new AssertionError(targetException),
                        "Unexpected throwable occurred while invoking " + toSimpleName(method));
            }
        }
        catch (RuntimeException e) {
            throw new RuntimeOperationsException(e, "RuntimeException occurred while invoking " + toSimpleName(method));
        }
        catch (IllegalAccessException e) {
            throw new ReflectionException(e, "IllegalAccessException occurred while invoking " + toSimpleName(method));
        }
        catch (Error err) {
            throw new RuntimeErrorException(err, "Error occurred while invoking " + toSimpleName(method));
        }
        catch (Exception e) {
            throw new ReflectionException(e, "Exception occurred while invoking " + toSimpleName(method));
        }
    }

    private static String toSimpleName(Method method)
    {
        return method.getName() + "()";
    }

    static boolean isGetter(Method method)
    {
        String methodName = method.getName();
        return (methodName.startsWith("get") || methodName.startsWith("is")) && isValidGetter(method);
    }

    static String getAttributeName(Method method)
    {
        Matcher matcher = getterOrSetterPattern.matcher(method.getName());
        if (matcher.matches()) {
            return matcher.group(2);
        }
        return LOWER_CAMEL.to(UPPER_CAMEL, method.getName());
    }

    static String getAttributeName(Field field)
    {
        return LOWER_CAMEL.to(UPPER_CAMEL, field.getName());
    }

    static boolean isValidGetter(Method getter)
    {
        if (getter == null) {
            throw new NullPointerException("getter is null");
        }
        if (getter.getParameterTypes().length != 0) {
            return false;
        }
        if (getter.getReturnType().equals(Void.TYPE)) {
            return false;
        }
        return true;
    }
}
