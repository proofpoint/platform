/*
 * Copyright 2012 Proofpoint, Inc.
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

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;

/**
 * Wraps JsonProcessingExceptions to provide more information about parsing errors.
 */
public class JsonMapperParsingException extends ParsingException
{
    private final Class<?> type;

    private JsonMapperParsingException(Class<?> type, String message, Throwable cause)
    {
        super(message, cause);
        this.type = type;
    }

    static JsonMapperParsingException jsonMapperParsingException(Class<?> type, Throwable cause)
    {
        StringBuilder sb = new StringBuilder("Invalid json");
        JsonLocation location = null;
        if (cause instanceof JsonProcessingException jsonProcessingException) {
            location = jsonProcessingException.getLocation();
        }
        if (location != null) {
            sb.append(" line ")
                    .append(location.getLineNr())
                    .append(" column ")
                    .append(location.getColumnNr());
        }

        if (cause instanceof JsonMappingException jsonMappingException) {
            sb.append(" field ");
            boolean appendDot = false;
            for (Reference reference : jsonMappingException.getPath()) {
                if (appendDot) {
                    sb.append('.');
                }
                appendDot = true;
                if (reference.getIndex() >= 0) {
                    sb.append('[');
                    sb.append(reference.getIndex());
                    sb.append(']');
                }
                else if (reference.getFieldName() != null) {
                    sb.append(reference.getFieldName());
                }
                else {
                    sb.append('?');
                }
            }
            if (!appendDot) {
                sb.append('?');
            }
        }

        return new JsonMapperParsingException(type, sb.toString(), cause);
    }

    /**
     * Returns the type of object that failed Json parsing.
     *
     * @return object type of object that failed Json parsing
     */
    public Class<?> getType()
    {
        return type;
    }
}