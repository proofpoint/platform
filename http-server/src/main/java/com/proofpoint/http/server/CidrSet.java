/*
 * Copyright 2018 Proofpoint, Inc.
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
package com.proofpoint.http.server;

import com.google.common.collect.ImmutableSet;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class CidrSet
{
    private final Set<Inet4Network> cidrs;

    private CidrSet(Collection<Inet4Network> cidrs) {
        this.cidrs = Set.copyOf(cidrs);
    }

    /**
     * Returns a {@link CidrSet} from a string.
     *
     * @param cidrList Comma-separated list of IPv4 CIDR blocks. For now only IPv4 CIDR blocks are supported.
     * @return A {@link CidrSet} identifying all addresses in the blocks in {@code cidrList}.
     */
    public static CidrSet fromString(String cidrList) {
        Set<Inet4Network> uris = Arrays.stream(cidrList.split("\\s*,\\s*"))
                        .map(Inet4Network::fromCidr)
                        .collect(ImmutableSet.toImmutableSet());
        return new CidrSet(uris);
    }

    /**
     * @return A {@link CidrSet} identifying no addresses.
     */
    public static CidrSet empty()
    {
        return new CidrSet(Set.of());
    }

    /**
     * Determines whether an {@link InetAddress} is contained in this {@link CidrSet}.
     *
     * @param address The address to check.
     * @return true iff the address is contained in the {@link CidrSet}.
     */
    public boolean containsAddress(InetAddress address)
    {
        if (!(address instanceof Inet4Address)) {
            return false;
        }

        Inet4Address inet4Address = (Inet4Address) address;
        for (Inet4Network cidr : cidrs) {
            if (cidr.containsAddress(inet4Address)) {
                return true;
            }
        }

        return false;
    }

    public CidrSet union(CidrSet other) {
        return new CidrSet(ImmutableSet.<Inet4Network>builder()
                .addAll(cidrs)
                .addAll(other.cidrs)
                .build());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CidrSet cidrSet = (CidrSet) o;
        return Objects.equals(cidrs, cidrSet.cidrs);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(cidrs);
    }

    @Override
    public String toString()
    {
        return cidrs.stream()
                .map(Inet4Network::toString)
                .collect(Collectors.joining(","));
    }
}
