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

import com.google.common.base.Supplier;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.inject.Inject;
import org.weakref.jmx.ObjectNameBuilder;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.propagate;
import static java.lang.reflect.Proxy.newProxyInstance;

public class ReportCollectionFactory
{
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    private final Ticker ticker;
    private final ReportExporter reportExporter;

    @Inject
    public ReportCollectionFactory(ReportExporter reportExporter)
    {
        this(reportExporter, Ticker.systemTicker());
    }

    protected ReportCollectionFactory(ReportExporter reportExporter, Ticker ticker)
    {
        this.reportExporter = reportExporter;
        this.ticker = ticker;
    }

    @SuppressWarnings("unchecked")
    public <T> T createReportCollection(Class<T> aClass)
    {
        checkNotNull(aClass, "class is null");
        return (T) newProxyInstance(aClass.getClassLoader(),
                new Class[]{aClass},
                new StatInvocationHandler(aClass, null));
    }

    @SuppressWarnings("unchecked")
    public <T> T createReportCollection(Class<T> aClass, String name)
    {
        checkNotNull(aClass, "class is null");
        checkNotNull(name, "name is null");
        return (T) newProxyInstance(aClass.getClassLoader(),
                new Class[]{aClass},
                new StatInvocationHandler(aClass, name));
    }

    private class StatInvocationHandler implements InvocationHandler
    {
        private final Map<Method, MethodImplementation> implementationMap;

        public <T> StatInvocationHandler(Class<T> aClass, @Nullable String name)
        {
            Builder<Method, MethodImplementation> cacheBuilder = ImmutableMap.builder();
            for (Method method : aClass.getMethods()) {
                if (method.getParameterTypes().length == 0) {
                    cacheBuilder.put(method, new SingletonImplementation(aClass, method, name));
                }
                else {
                    cacheBuilder.put(method, new CacheImplementation(aClass, method, name));
                }
            }
            implementationMap = cacheBuilder.build();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable
        {
            ImmutableList.Builder<Optional<String>> argBuilder = ImmutableList.builder();
            for (Object arg : firstNonNull(args, EMPTY_OBJECT_ARRAY)) {
                if (arg == null) {
                    argBuilder.add(Optional.<String>empty());
                }
                else if (arg instanceof Optional) {
                    argBuilder.add(((Optional<?>)arg).map(Object::toString));
                }
                else {
                    argBuilder.add(Optional.of(arg.toString()));
                }
            }
            return implementationMap.get(method).get(argBuilder.build());
        }

    }

    private interface MethodImplementation
    {
        Object get(List<Optional<String>> key)
                throws ExecutionException;
    }

    private class SingletonImplementation implements MethodImplementation
    {
        private final Object returnValue;

        SingletonImplementation(Class<?> aClass, Method method, String name)
        {
            checkArgument(method.getParameterTypes().length == 0, "method has parameters");
            returnValue = getReturnValueSupplier(method).get();

            MethodInfo methodInfo = MethodInfo.methodInfo(aClass, method, name);
            String packageName = methodInfo.getPackageName();
            Map<String, String> properties = methodInfo.getProperties();
            String upperMethodName = methodInfo.getUpperMethodName();

            ObjectNameBuilder objectNameBuilder = new ObjectNameBuilder(packageName);
            for (Entry<String, String> entry : properties.entrySet()) {
                objectNameBuilder = objectNameBuilder.withProperty(entry.getKey(), entry.getValue());
            }
            objectNameBuilder = objectNameBuilder.withProperty("name", upperMethodName);
            String objectName = objectNameBuilder.build();

            reportExporter.export(objectName, returnValue);
        }

        @Override
        public Object get(List<Optional<String>> key)
                throws ExecutionException
        {
            return returnValue;
        }
    }

    private class CacheImplementation implements MethodImplementation
    {
        private final LoadingCache<List<Optional<String>>, Object> loadingCache;
        @GuardedBy("registeredMap")
        private final Map<List<Optional<String>>, Object> registeredMap = new HashMap<>();
        @GuardedBy("registeredMap")
        private final Set<Object> reinsertedSet = new HashSet<>();
        @GuardedBy("registeredMap")
        private final Map<Object, String> objectNameMap = new HashMap<>();

        CacheImplementation(Class<?> aClass, Method method, String name)
        {
            checkState(method.getParameterTypes().length != 0);

            final Supplier<Object> returnValueSupplier = getReturnValueSupplier(method);

            ImmutableList.Builder<String> keyNameBuilder = ImmutableList.builder();
            int argPosition = 0;
            for (Annotation[] annotations : method.getParameterAnnotations()) {
                ++argPosition;
                boolean found = false;
                for (Annotation annotation : annotations) {
                    if (Key.class.isAssignableFrom(annotation.annotationType())) {
                        keyNameBuilder.add(((Key)annotation).value());
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new RuntimeException(methodName(method) + " parameter " + argPosition
                            + " has no @com.proofpoint.reporting.Key annotation");
                }
            }
            MethodInfo methodInfo = MethodInfo.methodInfo(aClass, method, name);
            String packageName = methodInfo.getPackageName();
            Map<String, String> properties = methodInfo.getProperties();
            String upperMethodName = methodInfo.getUpperMethodName();
            final List<String> keyNames = keyNameBuilder.build();
            loadingCache = CacheBuilder.newBuilder()
                    .ticker(ticker)
                    .expireAfterAccess(15, TimeUnit.MINUTES)
                    .removalListener(new UnexportRemovalListener())
                    .build(new CacheLoader<List<Optional<String>>, Object>()
                    {
                        @Override
                        public Object load(List<Optional<String>> key)
                                throws Exception
                        {
                            Object returnValue = returnValueSupplier.get();

                            ObjectNameBuilder objectNameBuilder = new ObjectNameBuilder(packageName);
                            for (Entry<String, String> entry : properties.entrySet()) {
                                objectNameBuilder = objectNameBuilder.withProperty(entry.getKey(), entry.getValue());
                            }
                            objectNameBuilder = objectNameBuilder.withProperty("name", upperMethodName);
                            for (int i = 0; i < keyNames.size(); ++i) {
                                if (key.get(i).isPresent()) {
                                    objectNameBuilder = objectNameBuilder.withProperty(keyNames.get(i), key.get(i).get());
                                }
                            }
                            String objectName = objectNameBuilder.build();

                            synchronized (registeredMap) {
                                Object existingStat = registeredMap.get(key);
                                if (existingStat != null) {
                                    reinsertedSet.add(existingStat);
                                    return existingStat;
                                }
                                registeredMap.put(key, returnValue);
                                reportExporter.export(objectName, returnValue);
                                objectNameMap.put(returnValue, objectName);
                            }
                            return returnValue;
                        }
                    });
        }

        @Override
        public Object get(List<Optional<String>> key)
                throws ExecutionException
        {
            return loadingCache.get(key);
        }

        private class UnexportRemovalListener implements RemovalListener<List<Optional<String>>, Object>
        {
            @Override
            public void onRemoval(RemovalNotification<List<Optional<String>>, Object> notification)
            {
                synchronized (registeredMap) {
                    if (reinsertedSet.remove(notification.getValue())) {
                        return;
                    }
                    String objectName = objectNameMap.remove(notification.getValue());
                    reportExporter.unexport(objectName);
                    registeredMap.remove(notification.getKey());
                }
            }
        }
    }

    protected Supplier<Object> getReturnValueSupplier(Method method) {
        final Constructor<?> constructor;
        try {
            constructor = method.getReturnType().getConstructor();
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException(methodName(method) + " return type " + method.getReturnType().getSimpleName()
                    + " has no public no-arg constructor");
        }

        return () -> {
            try {
                return constructor.newInstance();
            }
            catch (Exception e) {
                throw propagate(e);
            }
        };
    }

    private static String methodName(Method method)
    {
        StringBuilder builder = new StringBuilder(method.getDeclaringClass().getName());
        builder.append(".").append(method.getName()).append('(');
        boolean first = true;
        for (Class<?> type : method.getParameterTypes()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(type.getName());
            first = false;
        }
        builder.append(')');
        return builder.toString();
    }

    private static class MethodInfo
    {
        private final String packageName;
        private final Map<String, String> properties;
        private final String upperMethodName;

        private MethodInfo(String packageName, Map<String, String> properties, String upperMethodName)
        {
            this.packageName = packageName;
            this.properties = properties;
            this.upperMethodName = upperMethodName;
        }

        public String getPackageName()
        {
            return packageName;
        }

        public Map<String, String> getProperties()
        {
            return properties;
        }

        public String getUpperMethodName()
        {
            return upperMethodName;
        }

        public static MethodInfo methodInfo(Class<?> aClass, Method method, String name)
        {
            String packageName;
            Map<String, String> properties = new LinkedHashMap<>();
            if (name == null) {
                packageName = aClass.getPackage().getName();
                properties.put("type", aClass.getSimpleName());
            }
            else {
                ObjectName objectName;
                try {
                    objectName = ObjectName.getInstance(name);
                }
                catch (MalformedObjectNameException e) {
                    throw propagate(e);
                }
                packageName = objectName.getDomain();
                int index = packageName.length();
                if (name.charAt(index++) != ':') {
                    throw new RuntimeException("Unable to parse ObjectName " + name);
                }
                while (index < name.length()) {
                    int separatorIndex = name.indexOf('=', index);
                    String key = name.substring(index, separatorIndex);
                    String value;
                    if (name.charAt(++separatorIndex) == '\"') {
                        StringBuilder sb = new StringBuilder();
                        char c;
                        while ((c = name.charAt(++separatorIndex)) != '\"') {
                            if (c == '\\') {
                                c = name.charAt(++separatorIndex);
                            }
                            sb.append(c);
                        }
                        if (name.charAt(++separatorIndex) != ',') {
                            throw new RuntimeException("Unable to parse ObjectName " + name);
                        }
                        value = sb.toString();
                        index = separatorIndex + 1;
                    }
                    else {
                        index = name.indexOf(',', separatorIndex);
                        if (index == -1) {
                            index = name.length();
                        }
                        value = name.substring(separatorIndex, index);
                        ++index;
                    }
                    properties.put(key, value);
                }
            }
            String upperMethodName = LOWER_CAMEL.to(UPPER_CAMEL, method.getName());
            return new MethodInfo(packageName, properties, upperMethodName);
        }
    }
}
