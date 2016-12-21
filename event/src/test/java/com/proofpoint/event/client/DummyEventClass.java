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
package com.proofpoint.event.client;

import com.google.auto.value.AutoValue;

@EventType("Dummy")
@AutoValue
public abstract class DummyEventClass
{
    public static DummyEventClass dummyEventClass(double doubleValue, int intValue, String stringValue, boolean boolValue)
    {
        return new AutoValue_DummyEventClass(doubleValue, intValue, stringValue, boolValue);
    }

    @EventField
    public abstract double getDoubleValue();

    @EventField
    public abstract int getIntValue();

    @EventField
    public abstract String getStringValue();

    @EventField
    public abstract boolean isBoolValue();
}
