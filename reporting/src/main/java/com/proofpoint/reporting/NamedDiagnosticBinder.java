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
package com.proofpoint.reporting;

import static java.util.Objects.requireNonNull;

public class NamedDiagnosticBinder
{
    protected final DiagnosticMapping mapping;

    NamedDiagnosticBinder(DiagnosticMapping mapping)
    {
        this.mapping = requireNonNull(mapping);
    }

    /**
     * See the EDSL description at {@link DiagnosticBinder}.
     */
    public void withNamePrefix(String namePrefix)
    {
        mapping.setNamePrefix(requireNonNull(namePrefix, "namePrefix is null"));
    }
}