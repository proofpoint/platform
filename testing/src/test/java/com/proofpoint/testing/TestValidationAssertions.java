package com.proofpoint.testing;

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;

import static com.proofpoint.testing.Assertions.assertContains;
import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;
import static com.proofpoint.testing.ValidationAssertions.assertValidates;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestValidationAssertions
{
    private static final String MESSAGE = "@message@";
    private static final Bean VALID_OBJECT = new Bean(new Object());
    private static final Bean INVALID_OBJECT = new Bean(null);

    @Test
    public void testAssertValidates()
    {
        assertValidates(VALID_OBJECT);
        assertValidates(VALID_OBJECT, MESSAGE);
    }

    @Test
    public void testAssertValidatesThrowsWithInvalidObject()
    {
        boolean ok = false;
        try {
            ValidationAssertions.assertValidates(INVALID_OBJECT);
        }
        catch (AssertionError e) {
            ok = true;
            verifyExceptionMessage(e, null, INVALID_OBJECT, null, null);
        }
        assertTrue(ok, "Expected AssertionError");
    }

    @Test
    public void testAssertValidatesThrowsWithInvalidObjectWithMessage()
    {
        boolean ok = false;
        try {
            assertValidates(INVALID_OBJECT, MESSAGE);
        }
        catch (AssertionError e) {
            ok = true;
            // success
            verifyExceptionMessage(e, MESSAGE, INVALID_OBJECT, null, null);
        }
        assertTrue(ok, "Expected AssertionError");
    }

    @Test
    public void testTheAssertFailsValidationMethodSucceedsWithInvalidObject()
    {
        assertFailsValidation(INVALID_OBJECT, "value", "may not be null", NotNull.class);
    }

    @Test
    public void testTheAssertFailsValidationWithMessageMethodSucceedsWithInvalidObject()
    {
        assertFailsValidation(INVALID_OBJECT, "value", "may not be null", NotNull.class, MESSAGE);
    }

    @Test
    public void testTheAssertFailsValidationMethodThrowsWithValidObject()
    {
        boolean ok = false;
        try {
            assertFailsValidation(VALID_OBJECT, "value", "may not be null", NotNull.class);
        }
        catch (AssertionError e) {
            ok = true;
            verifyExceptionMessage(e, null, VALID_OBJECT, "value", NotNull.class);
        }

        assertTrue(ok, "Expected AssertionError");
    }

    @Test
    public void testTheAssertFailsValidationWithMessageMethodThrowsWithValidObject()
    {
        boolean ok = false;
        try {
            assertFailsValidation(VALID_OBJECT, "value", "may not be null", NotNull.class, MESSAGE);
        }
        catch (AssertionError e) {
            ok = true;
            // success
            verifyExceptionMessage(e, MESSAGE, VALID_OBJECT, "value", NotNull.class);
        }
        assertTrue(ok, "Expected AssertionError");
    }


    private void verifyExceptionMessage(AssertionError e, String message, Object value, String property, Class<? extends Annotation> annotation)
    {
        Assert.assertNotNull(e);
        String actualMessage = e.getMessage();
        Assert.assertNotNull(actualMessage);
        if (message != null) {
            assertTrue(actualMessage.startsWith(message + " "));
        }
        else {
            assertFalse(actualMessage.startsWith(" "));
        }

        assertContains(actualMessage, "<" + value + ">");

        if (annotation != null) {
            assertContains(actualMessage, annotation.getName());
        }

        if (property != null) {
            assertContains(actualMessage, property);
        }
    }

    public static class Bean
    {
        private Object value;

        private Bean(Object value)
        {
            this.value = value;
        }

        @NotNull
        public Object getValue()
        {
            return value;
        }
    }
}
