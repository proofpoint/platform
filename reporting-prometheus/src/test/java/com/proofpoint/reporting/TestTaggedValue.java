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

import static com.proofpoint.reporting.PrometheusBeanAttribute.ValueAndTimestamp.valueAndTimestamp;
import static com.proofpoint.reporting.SimplePrometheusValue.simplePrometheusValue;
import static com.proofpoint.testing.EquivalenceTester.comparisonTester;

public class TestTaggedValue
{
    @Test
    public void testComparison()
    {
        comparisonTester()
                .addLesserGroup(new TaggedValue(ImmutableMap.of(), valueAndTimestamp(simplePrometheusValue(3), null)),
                        new TaggedValue(ImmutableMap.of(), valueAndTimestamp(simplePrometheusValue(4), 3333L)))
                .addGreaterGroup(new TaggedValue(ImmutableMap.of("a", "x"), valueAndTimestamp(simplePrometheusValue(1), null)))
                .addGreaterGroup(new TaggedValue(ImmutableMap.of("a", "x", "b", "y"), valueAndTimestamp(simplePrometheusValue(1), null)))
                .addGreaterGroup(new TaggedValue(ImmutableMap.of("a", "x", "b", "z"), valueAndTimestamp(simplePrometheusValue(1), null)))
                .addGreaterGroup(new TaggedValue(ImmutableMap.of("a", "y", "b", "y"), valueAndTimestamp(simplePrometheusValue(1), null)))
                .addGreaterGroup(new TaggedValue(ImmutableMap.of("b", "y"), valueAndTimestamp(simplePrometheusValue(1), null)))
                .check();
    }
}
