package com.proofpoint.jaxrs;

import com.google.common.base.Supplier;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;

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
 * {@link javax.ws.rs.ext.Provider} or {@link javax.ws.rs.core.Feature}.
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
 * Binds a provider of JAX-RS {@link javax.ws.rs.core.Context} annotated
 * parameters. On the service port, a {@code @Context InjectedCntextObject}
 * parameter will be obtained from the singleton
 * {@code InjectedContextObjectSupplier}, which must implement
 * {@code Supplier<InjectedContextObject>}.
 *
 */
public class JaxrsBinder
{
    private final Multibinder<Object> resourceBinder;
    private final Multibinder<Object> adminResourceBinder;
    private final Multibinder<JaxrsBinding> keyBinder;
    private final Binder binder;
    private final MapBinder<Class<?>, Supplier<?>> injectionProviderBinder;

    private JaxrsBinder(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder cannot be null").skipSources(getClass());
        this.resourceBinder = newSetBinder(binder, Object.class, JaxrsResource.class).permitDuplicates();
        this.adminResourceBinder = newSetBinder(binder, Object.class, AdminJaxrsResource.class).permitDuplicates();
        this.keyBinder = newSetBinder(binder, JaxrsBinding.class, JaxrsResource.class).permitDuplicates();
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
    public void bind(Class<?> implementation)
    {
        binder.bind(implementation).in(SINGLETON);
        resourceBinder.addBinding().to(implementation).in(SINGLETON);
        registerJaxRsBinding(Key.get(implementation));
    }

    public void bind(TypeLiteral<?> implementation)
    {
        binder.bind(implementation).in(SINGLETON);
        resourceBinder.addBinding().to(implementation).in(SINGLETON);
        registerJaxRsBinding(Key.get(implementation));
    }

    public void bind(Key<?> targetKey)
    {
        binder.bind(targetKey).in(SINGLETON);
        resourceBinder.addBinding().to(targetKey).in(SINGLETON);
        registerJaxRsBinding(targetKey);
    }

    /**
     * See the EDSL description at {@link JaxrsBinder}.
     */
    public void bindInstance(Object instance)
    {
        resourceBinder.addBinding().toInstance(instance);
    }

    /**
     * See the EDSL description at {@link JaxrsBinder}.
     */
    public void bindAdmin(Class<?> implementation)
    {
        binder.bind(implementation).in(SINGLETON);
        adminResourceBinder.addBinding().to(implementation).in(SINGLETON);
        registerJaxRsBinding(Key.get(implementation));
    }

    public void bindAdmin(TypeLiteral<?> implementation)
    {
        binder.bind(implementation).in(SINGLETON);
        adminResourceBinder.addBinding().to(implementation).in(SINGLETON);
        registerJaxRsBinding(Key.get(implementation));
    }

    public void bindAdmin(Key<?> targetKey)
    {
        binder.bind(targetKey).in(SINGLETON);
        adminResourceBinder.addBinding().to(targetKey).in(SINGLETON);
        registerJaxRsBinding(targetKey);
    }

    /**
     * See the EDSL description at {@link JaxrsBinder}.
     */
    public void bindAdminInstance(Object instance)
    {
        adminResourceBinder.addBinding().toInstance(instance);
    }

    public void registerJaxRsBinding(Key<?> key)
    {
        keyBinder.addBinding().toInstance(new JaxrsBinding(key));
    }

    /**
     * See the EDSL description at {@link JaxrsBinder}.
     */
    public <T> LinkedInjectionProviderBindingBuilder<T> bindInjectionProvider(Class<T> type) {
        return new LinkedInjectionProviderBindingBuilder<>(type, injectionProviderBinder);
    }
}
