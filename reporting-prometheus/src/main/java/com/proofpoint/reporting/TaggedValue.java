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

import com.google.common.collect.ImmutableSortedMap;
import com.proofpoint.reporting.PrometheusBeanAttribute.ValueAndTimestamp;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;

import static java.util.Objects.requireNonNull;

record TaggedValue(
        SortedMap<String, String> tags,
        ValueAndTimestamp valueAndTimestamp
)
        implements Comparable<TaggedValue>
{
    TaggedValue
    {
        tags = ImmutableSortedMap.copyOf(tags);
        requireNonNull(valueAndTimestamp, "valueAndTimestamp is null");
    }

    TaggedValue(Map<String, String> tags, ValueAndTimestamp valueAndTimestamp)
    {
        this(ImmutableSortedMap.copyOf(tags), valueAndTimestamp);
    }

    @Override
    public int compareTo(TaggedValue o)
    {
        Iterator<Entry<String, String>> otherIterator = o.tags().entrySet().iterator();
        for (Entry<String, String> entry : tags().entrySet()) {
            if (!otherIterator.hasNext()) {
                return 1;
            }
            Entry<String, String> nextEntry = otherIterator.next();
            int compare = entry.getKey().compareTo(nextEntry.getKey());
            if (compare != 0) {
                return compare;
            }
            compare = entry.getValue().compareTo(nextEntry.getValue());
            if (compare != 0) {
                return compare;
            }
        }
        if (otherIterator.hasNext()) {
            return -1;
        }
        return 0;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof TaggedValue taggedValue)) {
            return false;
        }
        return compareTo(taggedValue) == 0;
    }

    @Override
    public int hashCode()
    {
        return tags().entrySet().hashCode();
    }
}
