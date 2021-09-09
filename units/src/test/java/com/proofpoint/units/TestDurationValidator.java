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
package com.proofpoint.units;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import org.hibernate.validator.HibernateValidator;
import org.testng.annotations.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.units.ConstraintValidatorAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.fail;

public class TestDurationValidator
{
    private static final Validator validator = Validation.byProvider(HibernateValidator.class).configure().buildValidatorFactory().getValidator();

    @Test
    public void testMaxDurationValidator()
    {
        MaxDurationValidator maxValidator = new MaxDurationValidator();
        maxValidator.initialize(new MockMaxDuration(new Duration(5, TimeUnit.SECONDS)));

        assertThat(maxValidator).isValidFor(new Duration(0, TimeUnit.SECONDS));
        assertThat(maxValidator).isValidFor(new Duration(5, TimeUnit.SECONDS));
        assertThat(maxValidator).isInvalidFor(new Duration(6, TimeUnit.SECONDS));
    }

    @Test
    public void testMinDurationValidator()
    {
        MinDurationValidator minValidator = new MinDurationValidator();
        minValidator.initialize(new MockMinDuration(new Duration(5, TimeUnit.SECONDS)));

        assertThat(minValidator).isValidFor(new Duration(5, TimeUnit.SECONDS));
        assertThat(minValidator).isValidFor(new Duration(6, TimeUnit.SECONDS));
        assertThat(minValidator).isInvalidFor(new Duration(0, TimeUnit.SECONDS));
    }

    @Test
    public void testAllowsNullMinAnnotation()
    {
        validator.validate(new NullMinAnnotation());
    }

    @Test
    public void testAllowsNullMaxAnnotation()
    {
        validator.validate(new NullMaxAnnotation());
    }

    @Test
    public void testDetectsBrokenMinAnnotation()
    {
        try {
            validator.validate(new BrokenMinAnnotation());
            fail("expected a ValidationException caused by an IllegalArgumentException");
        }
        catch (ValidationException e) {
            assertThat(e).hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    public void testDetectsBrokenMaxAnnotation()
    {
        try {
            validator.validate(new BrokenMaxAnnotation());
            fail("expected a ValidationException caused by an IllegalArgumentException");
        }
        catch (ValidationException e) {
            assertThat(e).hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    public void testPassesValidation()
    {
        ConstrainedDuration object = new ConstrainedDuration(new Duration(7, TimeUnit.SECONDS));
        Set<ConstraintViolation<ConstrainedDuration>> violations = validator.validate(object);
        assertThat(violations).isEmpty();
    }

    @Test
    public void testFailsMaxDurationConstraint()
    {
        ConstrainedDuration object = new ConstrainedDuration(new Duration(11, TimeUnit.SECONDS));
        Set<ConstraintViolation<ConstrainedDuration>> violations = validator.validate(object);
        assertThat(violations).hasSize(2);

        for (ConstraintViolation<ConstrainedDuration> violation : violations) {
            assertThat(violation.getConstraintDescriptor().getAnnotation()).isInstanceOf(MaxDuration.class);
        }
    }

    @Test
    public void testFailsMinDurationConstraint()
    {
        ConstrainedDuration object = new ConstrainedDuration(new Duration(1, TimeUnit.SECONDS));
        Set<ConstraintViolation<ConstrainedDuration>> violations = validator.validate(object);
        assertThat(violations).hasSize(2);

        for (ConstraintViolation<ConstrainedDuration> violation : violations) {
            assertThat(violation.getConstraintDescriptor().getAnnotation()).isInstanceOf(MinDuration.class);
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class ConstrainedDuration
    {
        private final Duration duration;

        public ConstrainedDuration(Duration duration)
        {
            this.duration = duration;
        }

        @MinDuration("5s")
        public Duration getConstrainedByMin()
        {
            return duration;
        }

        @MaxDuration("10s")
        public Duration getConstrainedByMax()
        {
            return duration;
        }

        @MinDuration("5000ms")
        @MaxDuration("10000ms")
        public Duration getConstrainedByMinAndMax()
        {
            return duration;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class NullMinAnnotation
    {
        @MinDuration("1s")
        public Duration getConstrainedByMin()
        {
            return null;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class NullMaxAnnotation
    {
        @MaxDuration("1s")
        public Duration getConstrainedByMin()
        {
            return null;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class BrokenMinAnnotation
    {
        @MinDuration("broken")
        public Duration getConstrainedByMin()
        {
            return new Duration(10, TimeUnit.SECONDS);
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class BrokenMaxAnnotation
    {
        @MinDuration("broken")
        public Duration getConstrainedByMin()
        {
            return new Duration(10, TimeUnit.SECONDS);
        }
    }
}
