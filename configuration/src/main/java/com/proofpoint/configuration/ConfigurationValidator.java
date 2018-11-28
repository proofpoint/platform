/*
 * Copyright 2010 Proofpoint, Inc.
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
package com.proofpoint.configuration;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Module;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Message;
import com.google.inject.spi.PrivateElements;
import com.google.inject.spi.ProviderInstanceBinding;

import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

public class ConfigurationValidator
{
    private final ConfigurationFactory configurationFactory;

    public ConfigurationValidator(ConfigurationFactory configurationFactory)
    {
        requireNonNull(configurationFactory, "configurationFactory is null");
        this.configurationFactory = configurationFactory;
    }

    public List<Message> validate(Module... modules)
    {
        return validate(ImmutableList.copyOf(modules));
    }

    public List<Message> validate(Iterable<? extends Module> modules)
    {
        final List<Message> messages = new ArrayList<>();


        for (String error : configurationFactory.getInitialErrors()) {
            final Message message = new Message(error);
            messages.add(message);
            configurationFactory.getMonitor().onError(message);
        }

        for (final Element element : new ElementsIterator(modules)) {
            element.acceptVisitor(new DefaultElementVisitor<Void>()
            {
                @Override
                public <T> Void visit(Binding<T> binding)
                {
                    // look for ConfigurationAwareProviders...
                    if (binding instanceof ProviderInstanceBinding) {
                        ProviderInstanceBinding<?> providerInstanceBinding = (ProviderInstanceBinding<?>) binding;
                        Provider<?> provider = providerInstanceBinding.getUserSuppliedProvider();
                        if (provider instanceof ConfigurationAwareProvider) {
                            ConfigurationAwareProvider<?> configurationProvider = (ConfigurationAwareProvider<?>) provider;
                            // give the provider the configuration factory
                            configurationProvider.setConfigurationFactory(configurationFactory);
                            try {
                                configurationProvider.buildConfigObjects(modules);
                            } catch (ConfigurationException e) {
                                // if we got errors, add them to the errors list
                                for (Message message : e.getErrorMessages()) {
                                    messages.add(new Message(singletonList(binding.getSource()), message.getMessage(), message.getCause()));
                                }
                            }
                        }
                    }

                    return null;
                }

                @Override
                public Void visit(PrivateElements privateElements)
                {
                    for (Element element : privateElements.getElements()) {
                        element.acceptVisitor(this);
                    }

                    return null;
                }
            });
        }

        for (String unusedProperty : configurationFactory.getUnusedProperties()) {
            final Message message = new Message(format("Configuration property '%s' was not used", unusedProperty));
            messages.add(message);
            configurationFactory.getMonitor().onError(message);
        }

        return messages;
    }
}
