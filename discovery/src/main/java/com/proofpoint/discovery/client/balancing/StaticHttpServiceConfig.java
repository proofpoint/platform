/*
 * Copyright 2017 Proofpoint, Inc.
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
package com.proofpoint.discovery.client.balancing;

import com.google.common.collect.ForwardingMultiset;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;
import jakarta.validation.constraints.Size;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;

import static com.google.common.collect.ImmutableMultiset.toImmutableMultiset;

public class StaticHttpServiceConfig
{
    private UriMultiset uris;

    @Nullable
    @Size(min = 1)
    public UriMultiset getUris()
    {
        return uris;
    }

    @Config("uri")
    @ConfigDescription("Set of URIs for the service. Default is to use Discovery service.")
    public StaticHttpServiceConfig setUris(UriMultiset uris)
    {
        this.uris = uris;
        return this;
    }

    public static final class UriMultiset extends ForwardingMultiset<URI>
    {
        private final Multiset<URI> delegate;

        private UriMultiset(Collection<URI> delegate)
        {
            this.delegate = ImmutableMultiset.copyOf(delegate);
        }

        public static UriMultiset of(URI... uris)
        {
            return new UriMultiset(ImmutableMultiset.copyOf(uris));
        }

        public static UriMultiset valueOf(String string)
        {
            ImmutableMultiset<URI> uris = Arrays.stream(string.split("\\s*,\\s*"))
                    .map(URI::create)
                    .collect(toImmutableMultiset());
            return new UriMultiset(uris);
        }

        @Override
        protected Multiset<URI> delegate()
        {
            return delegate;
        }
    }
}
