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

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

class HealthStatusRepresentation
{
    @JsonProperty
    private final String description;
    @JsonProperty
    private final Status status;
    @JsonProperty @Nullable
    private final String reason;

    public HealthStatusRepresentation(String description, Status status, @Nullable String reason)
    {
        this.description = checkNotNull(description, "description is null");
        this.status = checkNotNull(status, "status is null");
        this.reason = reason;
    }

    enum Status
    {
        OK, ERROR, UNKNOWN
    }
}
