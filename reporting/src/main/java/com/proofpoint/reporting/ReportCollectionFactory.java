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

import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static java.lang.reflect.Proxy.newProxyInstance;
import static java.util.Objects.requireNonNull;

public class ReportCollectionFactory
{
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    private static final Method OBJECT_EQUALS_METHOD;
    private static final Method OBJECT_HASH_CODE_METHOD;
    private static final Method OBJECT_TO_STRING_METHOD;
    private final Ticker ticker;
    private final ReportExporter reportExporter;

    static {
        try {
            OBJECT_EQUALS_METHOD = Object.class.getDeclaredMethod("equals", Object.class);
            OBJECT_HASH_CODE_METHOD = Object.class.getDeclaredMethod("hashCode");
            OBJECT_TO_STRING_METHOD = Object.class.getDeclaredMethod("toString");
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

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

    /**
     * Creates a report collection with a name prefix of the interface class.
     *
     * @param aClass The interface class of the created report collection
     */
    @SuppressWarnings("unchecked")
    public <T> T createReportCollection(Class<T> aClass)
    {
        requireNonNull(aClass, "class is null");
        return (T) newProxyInstance(aClass.getClassLoader(),
                new Class[]{aClass},
                new StatInvocationHandler(aClass, false, Optional.of(aClass.getSimpleName()), ImmutableMap.of()));
    }

    /**
     * Creates a report collection with a name prefix of the interface class.
     *
     * @param aClass The interface class of the created report collection
     * @param applicationPrefix Whether to prefix the metric names with the application name
     * @param namePrefix Name prefix for all metrics reported out of the report collection
     * @param tags Tags for all metrics reported out of the report collection
     */
    @SuppressWarnings("unchecked")
    public <T> T createReportCollection(Class<T> aClass, boolean applicationPrefix, @Nullable String namePrefix, Map<String, String> tags)
    {
        requireNonNull(aClass, "class is null");
        requireNonNull(tags, "tags is null");

        if ("".equals(namePrefix)) {
            namePrefix = null;
        }
        return (T) newProxyInstance(aClass.getClassLoader(),
                new Class[] {aClass},
                new StatInvocationHandler(aClass, applicationPrefix, Optional.ofNullable(namePrefix), ImmutableMap.copyOf(tags)));
    }

    /**
     * @deprecated Use {@link #createReportCollection(Class, boolean, String, Map)}.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public <T> T createReportCollection(Class<T> aClass, String name)
    {
        requireNonNull(aClass, "class is null");
        requireNonNull(name, "name is null");

        Optional<String> namePrefix = Optional.empty();
        Builder<String, String> tagsBuilder = ImmutableMap.builder();
        ObjectName objectName;
        try {
            objectName = ObjectName.getInstance(name);
        }
        catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
        int index = objectName.getDomain().length();
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

            if ("type".equals(key)) {
                checkArgument(!namePrefix.isPresent(), "ObjectName " + name + " has two type parameters");
                namePrefix = Optional.of(value);
            }
            else {
                checkArgument(!"name".equals(key), "ObjectName" + name + " not permitted to have a name parameter");
                tagsBuilder.put(key, value);
            }
        }

        return (T) newProxyInstance(aClass.getClassLoader(),
                new Class[]{aClass},
                new StatInvocationHandler(aClass, false, namePrefix, tagsBuilder.build()));
    }

    private class StatInvocationHandler implements InvocationHandler
    {
        private final Map<Method, MethodImplementation> implementationMap;

        <T> StatInvocationHandler(Class<T> aClass, boolean applicationPrefix, Optional<String> namePrefix, Map<String, String> tags)
        {
            Builder<Method, MethodImplementation> cacheBuilder = ImmutableMap.builder();
            for (Method method : aClass.getMethods()) {
                StringBuilder builder = new StringBuilder();
                if (namePrefix.isPresent()) {
                    builder.append(namePrefix.get()).append('.');
                }
                builder.append(LOWER_CAMEL.to(UPPER_CAMEL, method.getName()));
                if (method.getParameterTypes().length == 0) {
                    cacheBuilder.put(method, new SingletonImplementation(method, applicationPrefix, builder.toString(), tags));
                }
                else {
                    cacheBuilder.put(method, new CacheImplementation(method, applicationPrefix, builder.toString(), tags));
                }
            }
            implementationMap = cacheBuilder.build();
        }

        @Override
        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH", justification = "All possible methods covered")
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable
        {
            ImmutableList.Builder<Optional<String>> argBuilder = ImmutableList.builder();
            for (Object arg : firstNonNull(args, EMPTY_OBJECT_ARRAY)) {
                if (arg == null) {
                    argBuilder.add(Optional.empty());
                }
                else if (arg instanceof Optional) {
                    argBuilder.add(((Optional<?>)arg).map(Object::toString));
                }
                else {
                    argBuilder.add(Optional.of(arg.toString()));
                }
            }
            MethodImplementation implementation = implementationMap.get(method);
            if (implementation == null) {
                if (OBJECT_EQUALS_METHOD.equals(method)) {
                    return proxy == args[0];
                }
                if (OBJECT_HASH_CODE_METHOD.equals(method)) {
                    return hashCode();
                }
                if (OBJECT_TO_STRING_METHOD.equals(method)) {
                    return proxy.getClass().getName() + "@" + Integer.toHexString(hashCode());
                }
            }
            return implementation.get(argBuilder.build());
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

        SingletonImplementation(Method method, boolean applicationPrefix, String namePrefix, Map<String, String> tags)
        {
            checkArgument(method.getParameterTypes().length == 0, "method has parameters");
            returnValue = getReturnValueSupplier(method).get();

            reportExporter.export(returnValue, applicationPrefix, namePrefix, tags);
        }

        @Override
        public Object get(List<Optional<String>> key)
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

        CacheImplementation(Method method, boolean applicationPrefix, String namePrefix, Map<String, String> tags)
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
                        String keyName = ((Key) annotation).value();
                        checkArgument(!tags.containsKey(keyName), methodName(method) + " @Key(\"" + keyName + "\") duplicates tag on entire report collection");
                        keyNameBuilder.add(keyName);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new RuntimeException(methodName(method) + " parameter " + argPosition
                            + " has no @com.proofpoint.reporting.Key annotation");
                }
            }
            List<String> keyNames = keyNameBuilder.build();
            loadingCache = CacheBuilder.newBuilder()
                    .ticker(ticker)
                    .expireAfterAccess(15, TimeUnit.MINUTES)
                    .removalListener(new UnexportRemovalListener())
                    .build(new CacheLoader<List<Optional<String>>, Object>()
                    {
                        @Override
                        public Object load(List<Optional<String>> key)
                        {
                            Object returnValue = returnValueSupplier.get();

                            synchronized (registeredMap) {
                                Object existingStat = registeredMap.get(key);
                                if (existingStat != null) {
                                    reinsertedSet.add(existingStat);
                                    return existingStat;
                                }
                                registeredMap.put(key, returnValue);
                                Builder<String, String> tagBuilder = ImmutableMap.builder();
                                tagBuilder.putAll(tags);
                                for (int i = 0; i < keyNames.size(); ++i) {
                                    Optional<String> keyValue = key.get(i);
                                    if (keyValue.isPresent()) {
                                        tagBuilder.put(keyNames.get(i), keyValue.get());
                                    }
                                }
                                reportExporter.export(returnValue, applicationPrefix, namePrefix, tagBuilder.build());
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
                    reportExporter.unexportObject(notification.getValue());
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
                throwIfUnchecked(e);
                throw new RuntimeException(e);
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
}
