/*
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
package com.proofpoint.units;

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.assertj.core.api.AbstractAssert;

import java.lang.annotation.Annotation;

final class ConstraintValidatorAssert<A extends Annotation, T>
        extends AbstractAssert<ConstraintValidatorAssert<A, T>, ConstraintValidator<A, T>>
{
    public ConstraintValidatorAssert(ConstraintValidator<A, T> actual)
    {
        super(actual, ConstraintValidatorAssert.class);
    }

    public static <A extends Annotation, T> ConstraintValidatorAssert<A, T> assertThat(ConstraintValidator<A, T> actual)
    {
        return new ConstraintValidatorAssert<>(actual);
    }

    public void isValidFor(T value)
    {
        isNotNull();
        if (!actual.isValid(value, new MockContext())) {
            failWithMessage("Expected <%s> to be valid for <%s>", actual, value);
        }
    }

    public void isInvalidFor(T value)
    {
        isNotNull();
        if (actual.isValid(value, new MockContext())) {
            failWithMessage("Expected <%s> to be invalid for <%s>", actual, value);
        }
    }

    private static class MockContext
            implements ConstraintValidatorContext
    {
        @Override
        public void disableDefaultConstraintViolation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDefaultConstraintMessageTemplate()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClockProvider getClockProvider()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConstraintViolationBuilder buildConstraintViolationWithTemplate(String s)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T unwrap(Class<T> type)
        {
            throw new UnsupportedOperationException();
        }
    }
}
