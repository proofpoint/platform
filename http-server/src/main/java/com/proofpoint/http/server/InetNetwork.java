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

import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Objects;

import static com.google.common.net.InetAddresses.toBigInteger;

final class InetNetwork
{
    private final boolean isIpv6;
    private final int bits;
    private final BigInteger start;
    private final BigInteger end;

    private InetNetwork(InetAddress address, int bits)
    {
        this.isIpv6 = address instanceof Inet6Address;
        this.bits = bits;
        this.start = toBigInteger(address);
        int totalBits = isIpv6 ? 128 : 32;
        BigInteger length = BigInteger.valueOf(1).shiftLeft(totalBits - this.bits);
        this.end = start.add(length).subtract(BigInteger.valueOf(1));
    }

    public boolean containsAddress(InetAddress address)
    {
        BigInteger ip = addressToBigInteger(address);
        if ((address instanceof Inet6Address) != isIpv6) {
            return false;
        }
        return (ip.compareTo(start) >= 0) && (ip.compareTo(end) <= 0);
    }

    @Override
    public String toString()
    {
        if (isIpv6) {
            return InetAddresses.toAddrString(InetAddresses.fromIPv6BigInteger(start)) + "/" + bits;
        }
        return InetAddresses.fromIPv4BigInteger(start).getHostAddress() + "/" + bits;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InetNetwork that = (InetNetwork) o;
        return isIpv6 == that.isIpv6 && bits == that.bits && Objects.equals(start, that.start) && Objects.equals(end, that.end);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(isIpv6, bits, start, end);
    }

    @SuppressWarnings("StringSplitter")
    public static InetNetwork fromCidr(String cidr)
    {
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid CIDR format: " + cidr);
        }

        InetAddress address = InetAddresses.forString(parts[0]);
        int bits = Integer.parseInt(parts[1]);
        int totalBits = address instanceof Inet6Address ? 128 : 32;
        if ((bits < 0) || (bits > totalBits)) {
            throw new IllegalArgumentException("invalid prefix size: " + bits);
        }

        BigInteger mask = (bits == 0) ? BigInteger.ZERO : (BigInteger.valueOf(-1).shiftLeft(totalBits - bits));
        BigInteger ip = InetAddresses.toBigInteger(address);
        if (!ip.and(mask).equals(ip)) {
            throw new IllegalArgumentException("invalid prefix for prefix size: " + bits);
        }

        return new InetNetwork(address, bits);
    }

    static BigInteger addressToBigInteger(InetAddress address)
    {
        return toBigInteger(address);
    }
}
