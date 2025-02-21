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

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public final class ReportUtils
{
    private static final Pattern LABEL_NOT_ACCEPTED_CHARACTER_PATTERN = Pattern.compile("[^A-Za-z0-9_]");
    private static final Pattern INITIAL_DIGIT_PATTERN = Pattern.compile("[0-9]");

    private ReportUtils()
    {}

    public static boolean isReportable(Object value)
    {
        if (value instanceof Double aDouble) {
            return !(aDouble.isNaN() || aDouble.isInfinite());
        }
        if (value instanceof Float aFloat) {
            return !(aFloat.isNaN() || aFloat.isInfinite());
        }
        if (value instanceof Long) {
            return !(value.equals(Long.MAX_VALUE) || value.equals(Long.MIN_VALUE));
        }
        if (value instanceof Integer) {
            return !(value.equals(Integer.MAX_VALUE) || value.equals(Integer.MIN_VALUE));
        }
        if (value instanceof Short) {
            return !(value.equals(Short.MAX_VALUE) || value.equals(Short.MIN_VALUE));
        }
        return true;
    }

    static void writeTags(BufferedWriter writer, Iterable<Entry<String, String>> tags)
            throws IOException
    {
        char prefix = '{';
        for (Entry<String, String> tag : tags) {
            writer.append(prefix);
            prefix = ',';
            String label = LABEL_NOT_ACCEPTED_CHARACTER_PATTERN.matcher(tag.getKey()).replaceAll("_");
            String value = tag.getValue();
            if (INITIAL_DIGIT_PATTERN.matcher(label).lookingAt()) {
                writer.append('_');
            }
            writer.write(label);
            writer.append("=\"");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\\':
                        writer.append("\\\\");
                        break;
                    case '\"':
                        writer.append("\\\"");
                        break;
                    case '\n':
                        writer.append("\\n");
                        break;
                    default:
                        writer.append(c);
                }
            }
            writer.append("\"");
        }
        if (prefix == ',') {
            writer.append('}');
        }
    }
}
