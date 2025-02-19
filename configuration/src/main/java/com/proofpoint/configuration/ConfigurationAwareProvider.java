package com.proofpoint.configuration;

import com.google.inject.ConfigurationException;
import com.google.inject.Module;
import jakarta.inject.Provider;

/**
 * A provider with access to the Platform {@link ConfigurationFactory}.
 * <p>
 * Implementing this interface ensures that the provider gets access to the
 * {@link ConfigurationFactory} before the first
 * call to {@link Provider#get()}.
 *
 * @param <T> Element type that is returned by this provider.
 */
public interface ConfigurationAwareProvider<T> extends Provider<T>
{
    /**
     * Called by the Platform framework before the first call to get.
     *
     * @param configurationFactory The Platform configuration factory.
     */
    void setConfigurationFactory(ConfigurationFactory configurationFactory);

    /**
     * Called during configuration validation. Must use the
     * {@link ConfigurationFactory} previously set to build all config
     * objects that this provider will need
     *
     * @param modules The application modules. The provider may inspect these
     *                in order to determine which config objects to build.
     * @throws ConfigurationException when a programming error prevents
     *                                this building.
     */
    default void buildConfigObjects(Iterable<? extends Module> modules)
    {
        get();
    }
}
