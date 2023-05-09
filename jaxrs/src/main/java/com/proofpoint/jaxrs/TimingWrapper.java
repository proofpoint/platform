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
package com.proofpoint.jaxrs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.TypeLiteral;
import com.proofpoint.reporting.Key;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.field.FieldDescription.ForLoadedField;
import net.bytebuddy.description.method.MethodDescription.ForLoadedMethod;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription.ForLoadedType;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ParameterDefinition;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender.Size;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static net.bytebuddy.description.modifier.FieldManifestation.FINAL;
import static net.bytebuddy.description.modifier.Ownership.STATIC;
import static net.bytebuddy.description.modifier.Visibility.PACKAGE_PRIVATE;
import static net.bytebuddy.description.modifier.Visibility.PRIVATE;
import static net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy.Default.NO_CONSTRUCTORS;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

class TimingWrapper
{
    private static final Set<Class<?>> JAXRS_ANNOTATIONS = Set.of(GET.class, PUT.class, POST.class, DELETE.class, HEAD.class);
    private static final Type KEYNAMES_MAP_TYPE = new TypeLiteral<Map<String, List<String>>>() {}.getType();

    private TimingWrapper() {}

    static Object wrapIfAnnotatedResource(Object jaxRsSingleton)
    {
        Class<?> jaxRsSingletonClass = jaxRsSingleton.getClass();
        if (!jaxRsSingletonClass.isAnnotationPresent(Path.class)) {
            return jaxRsSingleton;
        }

        boolean hasKeyAnnotation = false;
        ImmutableMap.Builder<String, List<String>> keyNamesBuilder = ImmutableMap.builder();
        Builder<TimingWrapped> typeDefinition = new ByteBuddy()
                .subclass(TimingWrapped.class, NO_CONSTRUCTORS)
                .annotateType(jaxRsSingletonClass.getAnnotations())
                .defineField("delegate", jaxRsSingletonClass, PRIVATE, FINAL)
                .defineConstructor(PACKAGE_PRIVATE)
                .withParameters(jaxRsSingletonClass)
                .intercept(
                        MethodCall.invoke(new ForLoadedType(TimingWrapped.class)
                                .getDeclaredMethods()
                                .filter(isConstructor().and(takesArguments(0))).getOnly())
                                .onSuper()
                                .andThen(
                                        FieldAccessor.ofField("delegate")
                                                .setsArgumentAt(0)
                                )
                );

        for (Method method : jaxRsSingletonClass.getMethods()) {
            List<String> keyNames = new ArrayList<>();

            if (Arrays.stream(method.getAnnotations()).anyMatch(annotation -> JAXRS_ANNOTATIONS.contains(annotation.annotationType()))) {
                ParameterDefinition<TimingWrapped> defineMethod = typeDefinition.defineMethod(method.getName(), method.getReturnType(), method.getModifiers());
                for (Parameter parameter : method.getParameters()) {
                    ParameterDefinition<TimingWrapped> defineParameter = defineMethod
                            .withParameter(parameter.getType(), parameter.getName(), parameter.getModifiers())
                            .annotateParameter(parameter.getAnnotations());
                    defineMethod = defineParameter;

                    Key keyAnnotation = parameter.getAnnotation(Key.class);
                    if (keyAnnotation != null) {
                        String keyValue = keyAnnotation.value();
                        if ("method".equals(keyValue) || "responseCode".equals(keyValue) || "responseCodeFamily".equals(keyValue)) {
                            throw new RuntimeException("\"" + keyValue + "\" tag name in @Key annotation on parameter of method " + method + " duplicates standard tag name");
                        }
                        if (keyNames.contains(keyValue)) {
                            throw new RuntimeException("Duplicate \"" + keyValue + "\" tag name in @Key annotation on parameter of method " + method);
                        }
                        keyNames.add(keyValue);
                    }
                }

                if (!keyNames.isEmpty()) {
                    hasKeyAnnotation = true;
                    keyNamesBuilder.put(method.getName(), keyNames);
                    defineMethod = defineMethod.withParameter(ContainerRequestContext.class, "timingWrapperContext")
                            .annotateParameter(new Context() {
                                @Override
                                public Class<? extends Annotation> annotationType()
                                {
                                    return Context.class;
                                }
                            });
                }

                typeDefinition = defineMethod
                        .intercept(new MethodImplementation(method, !keyNames.isEmpty()))
                        .annotateMethod(method.getAnnotations());
            }
        }

        if (!hasKeyAnnotation) {
            return jaxRsSingleton;
        }

        try {
            ClassLoadingStrategy<ClassLoader> strategy;
            if (ClassInjector.UsingLookup.isAvailable()) {
                // Java 9 and above
                Class<?> methodHandles = Class.forName("java.lang.invoke.MethodHandles");
                Object lookup = methodHandles.getMethod("lookup").invoke(null);
                Method privateLookupIn = methodHandles.getMethod("privateLookupIn",
                        Class.class,
                        Class.forName("java.lang.invoke.MethodHandles$Lookup"));
                Object privateLookup = privateLookupIn.invoke(null, TimingWrapper.class, lookup);
                strategy = ClassLoadingStrategy.UsingLookup.of(privateLookup);
            } else if (ClassInjector.UsingReflection.isAvailable()) {
                // Java 8
                strategy = ClassLoadingStrategy.Default.INJECTION;
            } else {
                throw new IllegalStateException("No code generation strategy available");
            }

            return typeDefinition.defineMethod("getKeyNames", KEYNAMES_MAP_TYPE, STATIC, PACKAGE_PRIVATE)
                    .intercept(FixedValue.value(keyNamesBuilder.build()))
                    .make()
                    .load(TimingWrapper.class.getClassLoader(), strategy)
                    .getLoaded()
                    .getDeclaredConstructor(jaxRsSingletonClass)
                    .newInstance(jaxRsSingleton);
        }
        catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static class MethodImplementation implements Implementation
    {
        private static final ForLoadedField TAGS_KEY_FIELD;
        private static final ForLoadedMethod IMMUTABLE_LIST_BUILDER_METHOD;
        private static final ForLoadedMethod LONG_VALUE_OF_METHOD;
        private static final ForLoadedMethod DOUBLE_VALUE_OF_METHOD;
        private static final ForLoadedMethod FLOAT_VALUE_OF_METHOD;
        private static final ForLoadedMethod BOOLEAN_VALUE_OF_METHOD;
        private static final ForLoadedMethod INTEGER_VALUE_OF_METHOD;
        private static final ForLoadedMethod OPTIONAL_OF_NULLABLE_METHOD;
        private static final ForLoadedMethod BUILDER_ADD_METHOD;
        private static final ForLoadedMethod BUILDER_BUILD_METHOD;
        private static final ForLoadedMethod CONTEXT_SET_PROPERTY_METHOD;

        private final Method delegateMethod;
        private final boolean hasKeyParameters;

        static {
            try {
                TAGS_KEY_FIELD = new ForLoadedField(TimingFilter.class.getDeclaredField("TAGS_KEY"));
                IMMUTABLE_LIST_BUILDER_METHOD = new ForLoadedMethod(ImmutableList.class.getMethod("builder"));
                LONG_VALUE_OF_METHOD = new ForLoadedMethod(Long.class.getMethod("valueOf", long.class));
                DOUBLE_VALUE_OF_METHOD = new ForLoadedMethod(Double.class.getMethod("valueOf", double.class));
                FLOAT_VALUE_OF_METHOD = new ForLoadedMethod(Float.class.getMethod("valueOf", float.class));
                BOOLEAN_VALUE_OF_METHOD = new ForLoadedMethod(Boolean.class.getMethod("valueOf", boolean.class));
                INTEGER_VALUE_OF_METHOD = new ForLoadedMethod(Integer.class.getMethod("valueOf", int.class));
                OPTIONAL_OF_NULLABLE_METHOD = new ForLoadedMethod(Optional.class.getMethod("ofNullable", Object.class));
                BUILDER_ADD_METHOD = new ForLoadedMethod(ImmutableList.Builder.class.getMethod("add", Object.class));
                BUILDER_BUILD_METHOD = new ForLoadedMethod(ImmutableList.Builder.class.getMethod("build"));
                CONTEXT_SET_PROPERTY_METHOD = new ForLoadedMethod(ContainerRequestContext.class.getMethod("setProperty", String.class, Object.class));
            }
            catch (NoSuchFieldException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        MethodImplementation(Method delegateMethod, boolean hasKeyParameters)
        {
            this.delegateMethod = delegateMethod;
            this.hasKeyParameters = hasKeyParameters;
        }

        @Override
        public ByteCodeAppender appender(Target implementationTarget)
        {
            return (methodVisitor, implementationContext, instrumentedMethod) -> {
                ImmutableList.Builder<StackManipulation> builder = ImmutableList.builder();
                List<? extends ParameterDescription> instrumentedMethodParameters = new ArrayList<>(instrumentedMethod.getParameters());
                if (hasKeyParameters) {
                    builder.add(
                            MethodVariableAccess.load(instrumentedMethodParameters.remove(instrumentedMethodParameters.size() - 1)),
                            FieldAccess.forField(TAGS_KEY_FIELD).read(),
                            MethodInvocation.invoke(IMMUTABLE_LIST_BUILDER_METHOD)
                    );
                    for (ParameterDescription parameterDescription : instrumentedMethod.getParameters()) {
                        if (parameterDescription.getDeclaredAnnotations().isAnnotationPresent(Key.class)) {
                            builder.add(MethodVariableAccess.load(parameterDescription));
                            Generic type = parameterDescription.getType();
                            if (type.isPrimitive()) {
                                if (type.represents(long.class)) {
                                    builder.add(MethodInvocation.invoke(LONG_VALUE_OF_METHOD));
                                } else if (type.represents(double.class)) {
                                    builder.add(MethodInvocation.invoke(DOUBLE_VALUE_OF_METHOD));
                                } else if (type.represents(float.class)) {
                                    builder.add(MethodInvocation.invoke(FLOAT_VALUE_OF_METHOD));
                                } else if (type.represents(boolean.class)) {
                                    builder.add(MethodInvocation.invoke(BOOLEAN_VALUE_OF_METHOD));
                                } else if (!type.represents(void.class)) {
                                    builder.add(MethodInvocation.invoke(INTEGER_VALUE_OF_METHOD));
                                }
                            }
                            builder.add(
                                    MethodInvocation.invoke(OPTIONAL_OF_NULLABLE_METHOD),
                                    MethodInvocation.invoke(BUILDER_ADD_METHOD)
                            );
                        }
                    }
                    builder.add(
                        MethodInvocation.invoke(BUILDER_BUILD_METHOD),
                        MethodInvocation.invoke(CONTEXT_SET_PROPERTY_METHOD)
                    );
                }
                builder.add(
                        MethodVariableAccess.REFERENCE.loadFrom(0),
                        FieldAccess.forField(implementationTarget.getInstrumentedType()
                                .getDeclaredFields()
                                .filter(named("delegate")).getOnly())
                                .read()
                );
                for (ParameterDescription parameterDescription : instrumentedMethodParameters) {
                        builder.add(MethodVariableAccess.load(parameterDescription));
                }
                builder.add(
                        MethodInvocation.invoke(new ForLoadedType(delegateMethod.getDeclaringClass())
                                .getDeclaredMethods()
                                .filter(ElementMatchers.is(delegateMethod)).getOnly()),
                        MethodReturn.of(instrumentedMethod.getReturnType())
                );
                StackManipulation.Size size = new StackManipulation.Compound(builder.build())
                        .apply(methodVisitor, implementationContext);
                return new Size(size.getMaximalSize(), instrumentedMethod.getStackSize());
            };
        }

        @Override
        public InstrumentedType prepare(InstrumentedType instrumentedType)
        {
            return instrumentedType;
        }
    }
}
