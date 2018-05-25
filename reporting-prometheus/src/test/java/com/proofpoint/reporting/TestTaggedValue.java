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
package com.proofpoint.reporting;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import static com.proofpoint.reporting.TaggedValue.taggedValue;
import static com.proofpoint.testing.EquivalenceTester.comparisonTester;

public class TestTaggedValue
{
    @Test
    public void testComparison() {
        comparisonTester()
                .addLesserGroup(taggedValue(ImmutableMap.of(), 3), taggedValue(ImmutableMap.of(), 4))
                .addGreaterGroup(taggedValue(ImmutableMap.of("a", "x"), 1))
                .addGreaterGroup(taggedValue(ImmutableMap.of("a", "x", "b", "y"), 1))
                .addGreaterGroup(taggedValue(ImmutableMap.of("a", "x", "b", "z"), 1))
                .addGreaterGroup(taggedValue(ImmutableMap.of("a", "y", "b", "y"), 1))
                .addGreaterGroup(taggedValue(ImmutableMap.of("b", "y"), 1))
                .check();
    }
}
