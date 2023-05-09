package com.proofpoint.jaxrs;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;

import java.util.function.Supplier;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.MapBinder.newMapBinder;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static java.util.Objects.requireNonNull;

/**
 * Binds JAX-RS resources, providers, and features.
 *
 * <h3>The JAX-RS Binding EDSL</h3>
 *
 * <pre>
 *     jaxrsBinder(binder).bind(Singleton.class);</pre>
 *
 * Binds the singleton {@code Singleton} to the service port.
 * {@code Singleton} is typically a JAX-RS resource, though may be a
 * {@link jakarta.ws.rs.ext.Provider} or {@link jakarta.ws.rs.core.Feature}.
 *
 * <pre>
 *     jaxrsBinder(binder).bind(Singleton.class)
 *         .withApplicationPrefix();</pre>
 *
 * In the case where {@code Singleton} is a JAX-RS resource, causes the
 * reported resource metrics to be named with the application prefix.
 * {@code .withApplicationPrefix()} may be used after any method that
 * binds a JAX-RS resource.
 *
 * <pre>
 *     jaxrsBinder(binder).bindInstance(singleton);</pre>
 *
 * In this example, your module itself, <i>not Guice</i>, takes responsibility
 * for obtaining a {@code singleton} instance, then asks JAX-RS to use it on
 * the service port.
 *
 * <pre>
 *     jaxrsBinder(binder).bindAdmin(Singleton.class);</pre>
 *
 * Binds the singleton {@code Singleton} to the admin port.
 *
 * <pre>
 *     jaxrsBinder(binder).bindAdminInstance(singleton);</pre>
 *
 * Asks JAX-RS to use {@code singleton} on the admin port.
 *
 * <pre>
 *     jaxrsBinder(binder).bindInjectionProvider(InjectedContextObject.class).to(InjectedContextObjectSupplier.class);</pre>
 *
 * Binds a provider of JAX-RS {@link jakarta.ws.rs.core.Context} annotated
 * parameters. On the service port, a {@code @Context InjectedContextObject}
 * parameter will be obtained from the singleton
 * {@code InjectedContextObjectSupplier}, which must implement
 * {@code Supplier<InjectedContextObject>}.
 *
 */
public class JaxrsBinder
{
    private final Binder binder;
    private final Multibinder<Object> resourceBinder;
    private final Multibinder<Object> adminResourceBinder;
    private final Multibinder<JaxrsBinding> keyBinder;
    private final Multibinder<Class<?>> applicationPrefixedBinder;
    private final MapBinder<Class<?>, Supplier<?>> injectionProviderBinder;

    private JaxrsBinder(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder cannot be null").skipSources(getClass());
        this.resourceBinder = newSetBinder(binder, Object.class, JaxrsResource.class).permitDuplicates();
        this.adminResourceBinder = newSetBinder(binder, Object.class, AdminJaxrsResource.class).permitDuplicates();
        this.keyBinder = newSetBinder(binder, JaxrsBinding.class, JaxrsResource.class).permitDuplicates();
        this.applicationPrefixedBinder = newSetBinder(binder, new TypeLiteral<Class<?>>() {}, JaxrsApplicationPrefixed.class).permitDuplicates();
        injectionProviderBinder = newMapBinder(binder, new TypeLiteral<Class<?>>() {}, new TypeLiteral<Supplier<?>>() {}, JaxrsInjectionProvider.class);
    }

    /**
     * Creates a new {@link JaxrsBinder}. See the EDSL examples at {@link JaxrsBinder}.
     *
     * @param binder The Guice {@link Binder} to use.
     */
    public static JaxrsBinder jaxrsBinder(Binder binder)
    {
        return new JaxrsBinder(binder);
    }

    /**
     * See the EDSL description at {@link JaxrsBinder}.
     */
    public PrefixedJaxrsBinder bind(Class<?> implementation)
    {
        binder.bind(implementation).in(SINGLETON);
        resourceBinder.addBinding().to(implementation).in(SINGLETON);
        return registerJaxRsBinding(Key.get(implementation));
    }

    /**
     * See the EDSL description at {@link JaxrsBinder}.
     */
    public PrefixedJaxrsBinder bindInstance(Object instance)
    {
        resourceBinder.addBinding().toInstance(instance);
        return new PrefixedJaxrsBinder(applicationPrefixedBinder, instance.getClass());
    }

    /**
     * See the EDSL description at {@link JaxrsBinder}.
     */
    public PrefixedJaxrsBinder bindAdmin(Class<?> implementation)
    {
        binder.bind(implementation).in(SINGLETON);
        adminResourceBinder.addBinding().to(implementation).in(SINGLETON);
        return registerJaxRsBinding(Key.get(implementation));
    }

    /**
     * See the EDSL description at {@link JaxrsBinder}.
     */
    public PrefixedJaxrsBinder bindAdminInstance(Object instance)
    {
        adminResourceBinder.addBinding().toInstance(instance);
        return new PrefixedJaxrsBinder(applicationPrefixedBinder, instance.getClass());
    }

    public PrefixedJaxrsBinder registerJaxRsBinding(Key<?> key)
    {
        JaxrsBinding binding = new JaxrsBinding(key);
        keyBinder.addBinding().toInstance(binding);
        return new PrefixedJaxrsBinder(applicationPrefixedBinder, key.getTypeLiteral().getRawType());
    }

    /**
     * See the EDSL description at {@link JaxrsBinder}.
     */
    public <T> LinkedInjectionProviderBindingBuilder<T> bindInjectionProvider(Class<T> type) {
        return new LinkedInjectionProviderBindingBuilder<>(type, injectionProviderBinder);
    }
}
