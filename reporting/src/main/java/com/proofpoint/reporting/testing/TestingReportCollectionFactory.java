package com.proofpoint.reporting.testing;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Ticker;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;
import com.proofpoint.reporting.ReportCollectionFactory;
import com.proofpoint.reporting.ReportExporter;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.reflect.Proxy.newProxyInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class TestingReportCollectionFactory
    extends ReportCollectionFactory
{
    private final NamedInstanceMap mockMap = new NamedInstanceMap();
    private final NamedInstanceMap superMap = new NamedInstanceMap();

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
        checkNotNull(aClass, "class is null");

        T mock = mock(aClass);
        mockMap.put(null, aClass, mock);

        T superCollection = super.createReportCollection(aClass);
        superMap.put(null, aClass, superCollection);

        return (T) newProxyInstance(
                aClass.getClassLoader(),
                new Class[]{aClass},
                new TestingInvocationHandler(mock, superCollection));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T createReportCollection(Class<T> aClass, String name)
    {
        checkNotNull(aClass, "class is null");
        checkNotNull(name, "name is null");

        T mock = mock(aClass);
        mockMap.put(name, aClass, mock);

        T superCollection = super.createReportCollection(aClass);
        superMap.put(name, aClass, superCollection);

        return (T) newProxyInstance(
                aClass.getClassLoader(),
                new Class[]{aClass},
                new TestingInvocationHandler(mock, superCollection));
    }

    @Nullable
    public <T> T getMock(Class<T> aClass)
    {
        return mockMap.get(null, aClass);
    }

    @Nullable
    public <T> T getMock(Class<T> aClass, String name)
    {
        checkNotNull(name, "name is null");
        return mockMap.get(name, aClass);
    }

    @Nullable
    public <T> T getSuper(Class<T> aClass)
    {
        return superMap.get(null, aClass);
    }

    @Nullable
    public <T> T getSuper(Class<T> aClass, String name)
    {
        checkNotNull(name, "name is null");
        return superMap.get(name, aClass);
    }

    @Override
    protected Supplier<Object> getReturnValueSupplier(Method method)
    {
        final Supplier<Object> superSupplier = super.getReturnValueSupplier(method);
        return new Supplier<Object>()
        {
            @Override
            public Object get()
            {
                return spy(superSupplier.get());
            }
        };
    }

    private static class TestingInvocationHandler<T>
            implements InvocationHandler
    {
        private final T mock;
        private final T superCollection;

        public TestingInvocationHandler(T mock, T superCollection)
        {
            this.mock = mock;
            this.superCollection = superCollection;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable
        {
            method.invoke(mock, args);
            return method.invoke(superCollection, args);
        }
    }

    private static class NamedInstanceMap
    {
        private Map<Optional<String>, ClassToInstanceMap<Object>> nameMap = new HashMap<>();

        public synchronized <T> void put(@Nullable String name, Class<T> aClass, T value)
        {
            ClassToInstanceMap<Object> instanceMap = nameMap.get(fromNullable(name));
            if (instanceMap == null) {
                instanceMap = MutableClassToInstanceMap.create();
                nameMap.put(fromNullable(name), instanceMap);
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
            ClassToInstanceMap<Object> instanceMap = nameMap.get(fromNullable(name));
            if (instanceMap == null) {
                return null;
            }
            return (T) instanceMap.get(aClass);
        }
    }
}
