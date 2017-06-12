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

import com.google.common.collect.ForwardingSet;
import com.google.common.collect.ImmutableSet;
import com.proofpoint.configuration.Config;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StaticHttpServiceConfig
{
    private UriSet uris = UriSet.of();

    public UriSet getUris()
    {
        return uris;
    }

    @Config("uri")
    public StaticHttpServiceConfig setUris(UriSet uris)
    {
        this.uris = uris;
        return this;
    }

    public static final class UriSet extends ForwardingSet<URI>
    {
        private final Set<URI> delegate;

        private UriSet(Set<URI> delegate)
        {
            this.delegate = ImmutableSet.copyOf(delegate);
        }

        public static UriSet of(URI... uris)
        {
            return new UriSet(ImmutableSet.copyOf(uris));
        }

        public static UriSet valueOf(String string)
        {
            List<URI> uris = Arrays.stream(string.split("\\s*,\\s*"))
                    .map(URI::create)
                    .collect(Collectors.toList());
            return new UriSet(ImmutableSet.copyOf(uris));
        }

        @Override
        protected Set<URI> delegate()
        {
            return delegate;
        }
    }
}
