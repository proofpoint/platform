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

import com.proofpoint.http.server.ClientAddressExtractor;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

import java.util.function.Supplier;

class ClientInfoSupplier
    implements Supplier<ClientInfo>
{
    private final ClientAddressExtractor clientAddressExtractor;

    @Inject
    ClientInfoSupplier(ClientAddressExtractor clientAddressExtractor)
    {
        this.clientAddressExtractor = clientAddressExtractor;
    }

    @Override
    public ClientInfo get()
    {
        return new InjectedClientInfo(clientAddressExtractor);
    }

    private static class InjectedClientInfo
            implements ClientInfo
    {
        private final ClientAddressExtractor clientAddressExtractor;
        private String address;

        private InjectedClientInfo(ClientAddressExtractor clientAddressExtractor)
        {
            this.clientAddressExtractor = clientAddressExtractor;
        }

        @Inject
        void setRequest(HttpServletRequest request)
        {
            address = clientAddressExtractor.clientAddressFor(request);
        }

        @Override
        public String getAddress()
        {
            return address;
        }
    }
}
