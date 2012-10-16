//
//  BeanValidationException.java
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import javax.validation.ConstraintViolation;
import java.util.List;
import java.util.Set;

public class BeanValidationException extends ValidationException
{
    private ImmutableSet<ConstraintViolation<Object>> violations;

    public BeanValidationException(Set<ConstraintViolation<Object>> violations)
    {
        this.violations = ImmutableSet.copyOf(violations);
    }

    @Override
    public List<String> getErrorMessages()
    {
        ImmutableList.Builder<String> messages = new ImmutableList.Builder<>();
        for (ConstraintViolation<?> violation : violations) {
            messages.add(violation.getPropertyPath().toString() + " " + violation.getMessage());
        }

        return messages.build();
    }
}