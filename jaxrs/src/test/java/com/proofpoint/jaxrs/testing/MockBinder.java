package com.proofpoint.jaxrs.testing;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Module;
import com.google.inject.PrivateBinder;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.Message;
import com.google.inject.spi.TypeConverter;
import com.google.inject.spi.TypeListener;
import org.aopalliance.intercept.MethodInterceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class MockBinder implements Binder
{

    @Override
    public void bindInterceptor(Matcher<? super Class<?>> classMatcher, Matcher<? super Method> methodMatcher, MethodInterceptor... interceptors)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void bindScope(Class<? extends Annotation> annotationType, Scope scope)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <T> LinkedBindingBuilder<T> bind(Key<T> key)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <T> AnnotatedBindingBuilder<T> bind(Class<T> type)
    {
        return null;
    }

    @Override
    public AnnotatedConstantBindingBuilder bindConstant()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <T> void requestInjection(TypeLiteral<T> type, T instance)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void requestInjection(Object instance)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void requestStaticInjection(Class<?>... types)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void install(Module module)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Stage currentStage()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void addError(String message, Object... arguments)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void addError(Throwable t)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void addError(Message message)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <T> Provider<T> getProvider(Key<T> key)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <T> Provider<T> getProvider(Class<T> type)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <T> MembersInjector<T> getMembersInjector(Class<T> type)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void convertToTypes(Matcher<? super TypeLiteral<?>> typeMatcher, TypeConverter converter)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void bindListener(Matcher<? super TypeLiteral<?>> typeMatcher, TypeListener listener)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Binder withSource(Object source)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Binder skipSources(Class... classesToSkip)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public PrivateBinder newPrivateBinder()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void requireExplicitBindings()
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void disableCircularProxies()
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
