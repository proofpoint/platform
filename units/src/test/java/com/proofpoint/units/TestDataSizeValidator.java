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

import org.hibernate.validator.HibernateValidator;
import org.testng.annotations.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidationException;
import javax.validation.Validator;
import java.util.Set;

import static com.proofpoint.units.ConstraintValidatorAssert.assertThat;
import static com.proofpoint.units.DataSize.Unit.GIGABYTE;
import static com.proofpoint.units.DataSize.Unit.KILOBYTE;
import static com.proofpoint.units.DataSize.Unit.MEGABYTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestDataSizeValidator
{
    private static final Validator VALIDATOR = Validation.byProvider(HibernateValidator.class).configure().buildValidatorFactory().getValidator();

    @Test
    public void testMaxDataSizeValidator()
    {
        MaxDataSizeValidator maxValidator = new MaxDataSizeValidator();
        maxValidator.initialize(new MockMaxDataSize(new DataSize(8, MEGABYTE)));

        assertThat(maxValidator).isValidFor(new DataSize(0, KILOBYTE));
        assertThat(maxValidator).isValidFor(new DataSize(5, KILOBYTE));
        assertThat(maxValidator).isValidFor(new DataSize(5005, KILOBYTE));
        assertThat(maxValidator).isValidFor(new DataSize(5, MEGABYTE));
        assertThat(maxValidator).isValidFor(new DataSize(8, MEGABYTE));
        assertThat(maxValidator).isValidFor(new DataSize(8192, KILOBYTE));
        assertThat(maxValidator).isInvalidFor(new DataSize(9, MEGABYTE));
        assertThat(maxValidator).isInvalidFor(new DataSize(1, GIGABYTE));
    }

    @Test
    public void testMinDataSizeValidator()
    {
        MinDataSizeValidator minValidator = new MinDataSizeValidator();
        minValidator.initialize(new MockMinDataSize(new DataSize(4, MEGABYTE)));

        assertThat(minValidator).isValidFor(new DataSize(4, MEGABYTE));
        assertThat(minValidator).isValidFor(new DataSize(4096, KILOBYTE));
        assertThat(minValidator).isValidFor(new DataSize(5, MEGABYTE));
        assertThat(minValidator).isInvalidFor(new DataSize(0, GIGABYTE));
        assertThat(minValidator).isInvalidFor(new DataSize(1, MEGABYTE));
    }

    @Test
    public void testAllowsNullMinAnnotation()
    {
        VALIDATOR.validate(new NullMinAnnotation());
    }

    @Test
    public void testAllowsNullMaxAnnotation()
    {
        VALIDATOR.validate(new NullMaxAnnotation());
    }

    @Test
    public void testDetectsBrokenMinAnnotation()
    {
        try {
            VALIDATOR.validate(new BrokenMinAnnotation());
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
            VALIDATOR.validate(new BrokenMaxAnnotation());
            fail("expected a ValidationException caused by an IllegalArgumentException");
        }
        catch (ValidationException e) {
            assertThat(e).hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    public void testPassesValidation()
    {
        ConstrainedDataSize object = new ConstrainedDataSize(new DataSize(7, MEGABYTE));
        Set<ConstraintViolation<ConstrainedDataSize>> violations = VALIDATOR.validate(object);
        assertTrue(violations.isEmpty());
    }

    @Test
    public void testFailsMaxDataSizeConstraint()
    {
        ConstrainedDataSize object = new ConstrainedDataSize(new DataSize(11, MEGABYTE));
        Set<ConstraintViolation<ConstrainedDataSize>> violations = VALIDATOR.validate(object);
        assertThat(violations).hasSize(2);

        for (ConstraintViolation<ConstrainedDataSize> violation : violations) {
            assertThat(violation.getConstraintDescriptor().getAnnotation()).isInstanceOf(MaxDataSize.class);
        }
    }

    @Test
    public void testFailsMinDataSizeConstraint()
    {
        ConstrainedDataSize object = new ConstrainedDataSize(new DataSize(1, MEGABYTE));
        Set<ConstraintViolation<ConstrainedDataSize>> violations = VALIDATOR.validate(object);
        assertThat(violations).hasSize(2);

        for (ConstraintViolation<ConstrainedDataSize> violation : violations) {
            assertThat(violation.getConstraintDescriptor().getAnnotation()).isInstanceOf(MinDataSize.class);
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class ConstrainedDataSize
    {
        private final DataSize dataSize;

        public ConstrainedDataSize(DataSize dataSize)
        {
            this.dataSize = dataSize;
        }

        @MinDataSize("5MB")
        public DataSize getConstrainedByMin()
        {
            return dataSize;
        }

        @MaxDataSize("10MB")
        public DataSize getConstrainedByMax()
        {
            return dataSize;
        }

        @MinDataSize("5000kB")
        @MaxDataSize("10000kB")
        public DataSize getConstrainedByMinAndMax()
        {
            return dataSize;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class NullMinAnnotation
    {
        @MinDataSize("1MB")
        public DataSize getConstrainedByMin()
        {
            return null;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class NullMaxAnnotation
    {
        @MaxDataSize("1MB")
        public DataSize getConstrainedByMin()
        {
            return null;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class BrokenMinAnnotation
    {
        @MinDataSize("broken")
        public DataSize getConstrainedByMin()
        {
            return new DataSize(32, KILOBYTE);
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class BrokenMaxAnnotation
    {
        @MinDataSize("broken")
        public DataSize getConstrainedByMin()
        {
            return new DataSize(32, KILOBYTE);
        }
    }
}
