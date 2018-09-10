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
package com.proofpoint.http.server;

import com.google.common.net.InetAddresses;

import java.net.Inet4Address;

final class Inet4Network
{
    private final int bits;
    private final long start;
    private final long end;

    private Inet4Network(Inet4Address address, int bits)
    {
        this.bits = bits;
        this.start = addressToLong(address);
        long length = 1L << (32 - this.bits);
        this.end = start + length - 1;
    }

    public boolean containsAddress(Inet4Address address)
    {
        long ip = addressToLong(address);
        return (ip >= start) && (ip <= end);
    }

    @Override
    public String toString()
    {
        return InetAddresses.fromInteger((int)start).getHostAddress() + "/" + bits;
    }

    @Override
    @SuppressWarnings({"RedundantIfStatement"})
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Inet4Network that = (Inet4Network) o;

        if (bits != that.bits) {
            return false;
        }
        if (start != that.start) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = (int)start;
        result = 31 * result + bits;
        return result;
    }

    @SuppressWarnings("StringSplitter")
    public static Inet4Network fromCidr(String cidr)
    {
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid CIDR format: " + cidr);
        }

        Inet4Address address = (Inet4Address) InetAddresses.forString(parts[0]);
        int bits = Integer.parseInt(parts[1]);

        if ((bits < 0) || (bits > 32)) {
            throw new IllegalArgumentException("invalid prefix size: " + bits);
        }

        int mask = (bits == 0) ? 0 : (-1 << (32 - bits));
        int ip = InetAddresses.coerceToInteger(address);
        if ((ip & mask) != ip) {
            throw new IllegalArgumentException("invalid prefix for prefix size: " + bits);
        }

        return new Inet4Network(address, bits);
    }

    static long addressToLong(Inet4Address address)
    {
        return InetAddresses.coerceToInteger(address) & 0xffffffffL;
    }
}
