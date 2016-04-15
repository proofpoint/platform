/*
 * Copyright 2013 Proofpoint, Inc.
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

import java.util.Map;

import static java.util.Objects.requireNonNull;

public class NamedReportBinder
    extends PrefixedReportBinder
{
    NamedReportBinder(Mapping mapping)
    {
        super(mapping);
    }

    /**
     * See the EDSL description at {@link ReportBinder}.
     */
    public PrefixedReportBinder withApplicationPrefix()
    {
        mapping.setApplicationPrefix(true);
        return new PrefixedReportBinder(mapping);
    }

    /**
     * @deprecated No longer necessary.
     */
    @Deprecated
    public void withGeneratedName()
    {
    }

    /**
     * @deprecated Use {@link #withNamePrefix} and/or {@link #withTags}.
     */
    @Deprecated
    public void as(String name)
    {
        mapping.setLegacyName(requireNonNull(name, "name is null"));
    }
}
