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

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class TaggedReportBinder
{
    protected final Mapping mapping;

    TaggedReportBinder(Mapping mapping)
    {
        this.mapping = mapping;
    }

    /**
     * See the EDSL description at {@link ReportBinder}.
     */
    public void withTags(Map<String, String> tags)
    {
        mapping.setTags(ImmutableMap.copyOf(tags));
    }
}
