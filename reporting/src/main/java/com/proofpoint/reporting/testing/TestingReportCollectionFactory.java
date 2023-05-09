/*
 * Copyright 2014 Proofpoint, Inc.
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
package com.proofpoint.reporting.testing;

import com.google.common.base.Ticker;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;
import com.proofpoint.reporting.ReportCollectionFactory;
import com.proofpoint.reporting.ReportExporter;
import jakarta.annotation.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.lang.reflect.Proxy.newProxyInstance;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * Produces report collection implementations for use in unit tests.
 *
 * For any report collection created by {@link #createReportCollection},
 * interactions may be verified through objects returned by
 * {@link #getArgumentVerifier} and {@link #getReportCollection}.
 *
 * <p>Example:
 * <pre class="code"><code class="java">
 * class TestStoreStatsRecorder {
 *     private TestingReportCollectionFactory factory;
 *     private StoreStats storeStats;
 *     private StoreStatsRecorder storeStatsRecorder;
 *
 *     &#064;BeforeMethod
 *     public void setup()
 *     {
 *         factory = new TestingReportCollectionFactory();
 *         storeStats = factory.createReportCollection(StoreStats.class);
 *         storeStatsRecorder = new StoreStatsRecorder(storeStats);
 *     }
 *
 *     &#064;Test
 *     public void testRecordSuccessfulAdd()
 *     {
 *         storeStatsRecorder.recordSuccessfulAdd(TEXT_PLAIN);
 *
 *         verify(factory.getArgumentVerifier(storeStats)).added(TEXT_PLAIN, SUCCESS);
 *         verifyNoMoreInteractions(factory.getArgumentVerifier(storeStats));
 *
 *         verify(factory.getReportCollection(storeStats).added(TEXT_PLAIN, SUCCESS)).add(1);
 *         verifyNoMoreInteractions(factory.getReportCollection(storeStats).added(TEXT_PLAIN, SUCCESS));
 *     }
 * }
 * </code></pre>
 */
public class TestingReportCollectionFactory
    extends ReportCollectionFactory
{
    private static final Method OBJECT_EQUALS_METHOD;
    private final InstanceMap argumentVerifierMap = new InstanceMap();
    private final InstanceMap superMap = new InstanceMap();
    private final NamedInstanceMap namedArgumentVerifierMap = new NamedInstanceMap();
    private final NamedInstanceMap namedSuperMap = new NamedInstanceMap();

    static {
        try {
            OBJECT_EQUALS_METHOD = Object.class.getDeclaredMethod("equals", Object.class);
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public TestingReportCollectionFactory()
    {
        super(mock(ReportExporter.class), new Ticker()
        {
            @Override
            public long read()
            {
                return 0;
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T createReportCollection(Class<T> aClass)
    {
        requireNonNull(aClass, "class is null");

        T argumentVerifier = mock(aClass);
        namedArgumentVerifierMap.put(null, aClass, argumentVerifier);

        T superCollection = super.createReportCollection(aClass);
        namedSuperMap.put(null, aClass, superCollection);

        T reportCollection = (T) newProxyInstance(
                aClass.getClassLoader(),
                new Class[] {aClass},
                new TestingInvocationHandler(argumentVerifier, superCollection));
        argumentVerifierMap.put(reportCollection, argumentVerifier);
        superMap.put(reportCollection, superCollection);

        return reportCollection;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T createReportCollection(Class<T> aClass, boolean applicationPrefix, @Nullable String namePrefix, Map<String, String> tags)
    {
        requireNonNull(aClass, "class is null");
        requireNonNull(tags, "tags is null");

        T argumentVerifier = mock(aClass);
        T superCollection = super.createReportCollection(aClass);
        T reportCollection = (T) newProxyInstance(
                aClass.getClassLoader(),
                new Class[] {aClass},
                new TestingInvocationHandler(argumentVerifier, superCollection));
        argumentVerifierMap.put(reportCollection, argumentVerifier);
        superMap.put(reportCollection, superCollection);

        return reportCollection;
    }

    /**
     * @deprecated Use {@link #createReportCollection(Class, boolean, String, Map)}.
     */
    @Deprecated
    @SuppressWarnings({"unchecked", "deprecation"})
    @Override
    public <T> T createReportCollection(Class<T> aClass, String name)
    {
        requireNonNull(aClass, "class is null");
        requireNonNull(name, "name is null");

        T argumentVerifier = mock(aClass);
        namedArgumentVerifierMap.put(name, aClass, argumentVerifier);

        T superCollection = super.createReportCollection(aClass);
        namedSuperMap.put(name, aClass, superCollection);

        return (T) newProxyInstance(
                aClass.getClassLoader(),
                new Class[]{aClass},
                new TestingInvocationHandler(argumentVerifier, superCollection));
    }

    /**
     * Returns a mock that can be used with {@link org.mockito.Mockito#verify}
     * to verify arguments to the testing report collection methods.
     *
     * @param testingReportCollection The testing report collection returned by
     *      {@link #createReportCollection}.
     */
    public <T> T getArgumentVerifier(T testingReportCollection)
    {
        return argumentVerifierMap.get(testingReportCollection);
    }

    /**
     * @deprecated Use {@link #getArgumentVerifier(Object)} with the testing
     * report collection.
     */
    @Deprecated
    public <T> T getArgumentVerifier(Class<T> aClass)
    {
        return namedArgumentVerifierMap.get(null, aClass);
    }

    /**
     * @deprecated Use {@link #createReportCollection(Class, boolean, String, Map)}
     * to create the testing report collection and {@link #getArgumentVerifier(Object)}
     * to get the argument verifier.
     */
    @Deprecated
    public <T> T getArgumentVerifier(Class<T> aClass, String name)
    {
        requireNonNull(name, "name is null");
        return namedArgumentVerifierMap.get(name, aClass);
    }

    /**
     * Returns an implementation returning the same values as the testing
     * report collection but which does not affect the argument verifier.
     * All returned values are Mockito spies, so can have their method calls
     * verified with with {@link org.mockito.Mockito#verify}.
     *
     * @param testingReportCollection The testing report collection returned by
     *      {@link #createReportCollection}.
     */
    public <T> T getReportCollection(T testingReportCollection)
    {
        return superMap.get(testingReportCollection);
    }

    /**
     * @deprecated Use {@link #getReportCollection(Object)} with the testing report collection.
     */
    @Deprecated
    public <T> T getReportCollection(Class<T> aClass)
    {
        return namedSuperMap.get(null, aClass);
    }

    /**
     * @deprecated Use {@link #createReportCollection(Class, boolean, String, Map)}
     * to create the testing report collection and {@link #getReportCollection(Object)}
     * to get the report collection that doesn't affect the argument verifier.
     */
    @Deprecated
    public <T> T getReportCollection(Class<T> aClass, String name)
    {
        requireNonNull(name, "name is null");
        return namedSuperMap.get(name, aClass);
    }

    @Override
    protected Supplier<Object> getReturnValueSupplier(Method method)
    {
        final Supplier<Object> superSupplier = super.getReturnValueSupplier(method);
        return () -> spy(superSupplier.get());
    }

    private static class TestingInvocationHandler<T>
            implements InvocationHandler
    {
        private final T argumentVerifier;
        private final T superCollection;

        TestingInvocationHandler(T argumentVerifier, T superCollection)
        {
            this.argumentVerifier = argumentVerifier;
            this.superCollection = superCollection;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable
        {
            method.invoke(argumentVerifier, args);
            if (OBJECT_EQUALS_METHOD.equals(method)) {
                return proxy == args[0];
            }
            return method.invoke(superCollection, args);
        }
    }

    private static class InstanceMap
    {
        private final Map<Object, Object> map = new IdentityHashMap<>();

        public synchronized <T> void put(T key, T value)
        {
            map.put(key, value);
        }

        @SuppressWarnings("unchecked")
        public synchronized <T> T get(T key)
        {
            return (T) map.get(key);
        }
    }

    private static class NamedInstanceMap
    {
        private final Map<Optional<String>, ClassToInstanceMap<Object>> nameMap = new HashMap<>();

        public synchronized <T> void put(@Nullable String name, Class<T> aClass, T value)
        {
            ClassToInstanceMap<Object> instanceMap = nameMap.get(ofNullable(name));
            if (instanceMap == null) {
                instanceMap = MutableClassToInstanceMap.create();
                nameMap.put(ofNullable(name), instanceMap);
            }
            if (instanceMap.putInstance(aClass, value) != null) {
                String message = "Duplicate ReportCollection for " + aClass.toString();
                if (name != null) {
                    message += " named " + name;
                }
                throw new Error(message);
            }
        }

        @SuppressWarnings("unchecked")
        public synchronized <T> T get(@Nullable String name, Class<T> aClass)
        {
            ClassToInstanceMap<Object> instanceMap = nameMap.get(ofNullable(name));
            if (instanceMap == null) {
                return null;
            }
            return (T) instanceMap.get(aClass);
        }
    }
}
