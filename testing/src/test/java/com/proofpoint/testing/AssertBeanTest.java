package com.proofpoint.testing;

import com.google.common.base.Preconditions;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.fail;

public class AssertBeanTest
{
    private Object bean;

    @BeforeMethod
    public void beforeMethod()
            throws Exception
    {
        bean = new SampleBean();
    }

    private static class SampleBean
    {
        ////////////////////////////////////////////////////////////
        private String notNull = null;
        public String getNotNull()
        {
            return notNull;
        }
        public void setNotNull(String notNull)
        {
            Preconditions.checkNotNull(notNull);    // property that does not accept null
            this.notNull = notNull;
        }
        ////////////////////////////////////////////////////////////
        private int intValue = 0;
        public int getIntValue()
        {
            return intValue;
        }
        public void setIntValue(int value)
        {
            this.intValue = value;
        }
        ////////////////////////////////////////////////////////////
        private String inconsistent = null;
        public String getInconsistent()
        {
            return inconsistent;
        }
        public void setInconsistent(String incnsistent)
        {
            this.inconsistent = inconsistent;       // bad code (typo).  unused input and assign to itself.
        }
        ////////////////////////////////////////////////////////////
        private String missingSetter = null;
        public String getMissingSetter() {
            return missingSetter;
        }
        ////////////////////////////////////////////////////////////
        private String missingGetter = null;
        public void setMissingGetter(String value)
        {
            this.missingGetter = value;
        }
        ////////////////////////////////////////////////////////////
        private boolean inconsistentType = true;
        public boolean getInconsistentType()
        {
            return inconsistentType;
        }
        public void setInconsistentType(float value)
        {
            inconsistentType = value > 0;               // getter return type != setter argument type
        }
        ////////////////////////////////////////////////////////////
        private boolean goodBoolean = true;
        public  boolean getGoodBoolean() { return goodBoolean; }
        public  void    setGoodBoolean(boolean value) { goodBoolean = value; }
        private Boolean goodBoolean2 = true;
        public  Boolean getGoodBoolean2() { return goodBoolean2; }
        public  void    setGoodBoolean2(Boolean value) { goodBoolean2 = value; }
        private int     goodInt = 0;
        public  int     getGoodInt() { return goodInt; }
        public  void    setGoodInt(int value) { goodInt = value; }
        private Integer goodInt2 = 0;
        public  Integer getGoodInt2() { return goodInt2; }
        public  void    setGoodInt2(Integer value) { goodInt2 = value; }
        private long    goodLong = 0;
        public  long    getGoodLong() { return goodLong; }
        public  void    setGoodLong(long value) { goodLong = value; }
        private Long    goodLong2 = (long) 0;
        public  Long    getGoodLong2() { return goodLong2; }
        public  void    setGoodLong2(Long value) { goodLong2 = value; }
        private double  goodDouble = 0;
        public  double  getGoodDouble() { return goodDouble; }
        public  void    setGoodDouble(double value) { goodDouble = value; }
        private Double  goodDouble2 = (double) 0;
        public  Double  getGoodDouble2() { return goodDouble2; }
        public  void    setGoodDouble2(Double value) { goodDouble2 = value; }
        private float   goodFloat = 0;
        public  float   getGoodFloat() { return goodFloat; }
        public  void    setGoodFloat(float value) { goodFloat = value; }
        private Float   goodFloat2 = (float) 0;
        public  Float   getGoodFloat2() { return goodFloat2; }
        public  void    setGoodFloat2(Float value) { goodFloat2 = value; }
        private String  goodString = "";
        public  String  getGoodString() { return goodString; }
        public  void    setGoodString(String value) { goodString = value; }
        ////////////////////////////////////////////////////////////
        private int greaterThanZero = 1;
        public int getGreaterThanZero()
        {
            return greaterThanZero;
        }
        public void setGreaterThanZero(int value)
        {
            Preconditions.checkArgument(value > 0);     // value must be > 0
            greaterThanZero = value;
        }
        ////////////////////////////////////////////////////////////
        private double greaterEqual3LessThanEqual37 = 4;
        public double getGreaterEqual3LessThanEqual37()
        {
            return greaterEqual3LessThanEqual37;
        }
        public void setGreaterEqual3LessThanEqual37(double value)
        {
            Preconditions.checkArgument(value >= 3);
            Preconditions.checkArgument(value <= 37);
            greaterEqual3LessThanEqual37 = value;
        }
    }

    @Test
    public void testGoodProperties()
        throws Exception
    {
        // Test assignment of valid value with auto-boxing
        AssertBean.assertSetAndGet(bean, "goodBoolean", true);
        AssertBean.assertSetAndGet(bean, "goodBoolean", Boolean.TRUE);
        AssertBean.assertSetAndGet(bean, "goodBoolean", false);
        AssertBean.assertSetAndGet(bean, "goodBoolean", Boolean.FALSE);
        AssertBean.assertSetAndGet(bean, "goodBoolean2", true);
        AssertBean.assertSetAndGet(bean, "goodBoolean2", Boolean.TRUE);
        AssertBean.assertSetAndGet(bean, "goodBoolean2", false);
        AssertBean.assertSetAndGet(bean, "goodBoolean2", Boolean.FALSE);
        AssertBean.assertSetAndGet(bean, "goodInt", 1234);
        AssertBean.assertSetAndGet(bean, "goodInt", Integer.valueOf(4321));
        AssertBean.assertSetAndGet(bean, "goodInt2", 1234);
        AssertBean.assertSetAndGet(bean, "goodInt2", Integer.valueOf(4321));
        AssertBean.assertSetAndGet(bean, "goodLong", (long) 1234);
        AssertBean.assertSetAndGet(bean, "goodLong", Long.valueOf(4321));
        AssertBean.assertSetAndGet(bean, "goodLong2", (long) 1234);
        AssertBean.assertSetAndGet(bean, "goodLong2", Long.valueOf(4321));
        AssertBean.assertSetAndGet(bean, "goodDouble", (double) 1234);
        AssertBean.assertSetAndGet(bean, "goodDouble", Double.valueOf(4321));
        AssertBean.assertSetAndGet(bean, "goodDouble2", (double) 1234);
        AssertBean.assertSetAndGet(bean, "goodDouble2", Double.valueOf(4321));
        AssertBean.assertSetAndGet(bean, "goodFloat", (float) 1234);
        AssertBean.assertSetAndGet(bean, "goodFloat", Float.valueOf(4321));
        AssertBean.assertSetAndGet(bean, "goodFloat2", (float) 1234);
        AssertBean.assertSetAndGet(bean, "goodFloat2", Float.valueOf(4321));
        AssertBean.assertSetAndGet(bean, "goodString", "A good cook can cook good cookies");
    }


    @Test (expectedExceptions = AssertionError.class)
    public void testPropertyNotFound()
        throws Exception
    {
        // If property does not exist, assertion is raised
        AssertBean.assertSetAndGet(bean, "noSuchProperty", "foo");
    }


    @Test (expectedExceptions = AssertionError.class)
    public void testMissingGetter()
        throws Exception
    {
        // Property exists, but has no Setter
        AssertBean.assertSetAndGet(bean, "missingGetter", "foo");
    }

    @Test (expectedExceptions = AssertionError.class)
    public void testMissingSetter()
        throws Exception
    {
        // Property exists, but has no Getter
        AssertBean.assertSetAndGet(bean, "missingSetter", "foo");
    }

    @Test (expectedExceptions = AssertionError.class)
    public void testInconsistentType()
        throws Exception
    {
        AssertBean.assertSetAndGet(bean, "inconsistentType", true);
    }

    @Test
    public void testMismatchType()
        throws Exception
    {
        try {
            // Setting a string into a property of type int
            AssertBean.assertSetAndGet(bean, "intValue", "???");
            fail("Expecting an Exception but not getting it");
        }
        catch (Exception e) {
            Assertions.assertInstanceOf(e, IllegalArgumentException.class);
        }
    }

    @Test (expectedExceptions = AssertionError.class)
    public void testNullToFieldNotNullable()
        throws Exception
    {
        // Trying to test null assignment to a field that setting null is not possible (int)
        AssertBean.assertNotNull(bean, "intValue");
        fail("Expecting an AssertionError but not getting it");
    }

    @Test
    public void testAssertNotNull()
            throws Exception
    {
        // Assigning a null value should raise NullPointerException
        AssertBean.assertNotNull(bean, "notNull");
    }

    @Test
    public void testAssertNotValid()
            throws Exception
    {
        AssertBean.assertNotValid(bean, "notNull", null, NullPointerException.class);
        try {
            // Providing a valid value on this method should raise AssertionError
            AssertBean.assertNotValid(bean, "notNull", "valid value", NullPointerException.class);
            fail("Expecting AssertionError but got none");
        }
        catch (AssertionError e) {
        }

        AssertBean.assertNotValid(bean, "greaterThanZero", 0, IllegalArgumentException.class);
        AssertBean.assertNotValid(bean, "greaterThanZero", -1, IllegalArgumentException.class);
        try {
            // Providing a valid value on this method should raise AssertionError
            AssertBean.assertNotValid(bean, "greaterThanZero", 1, IllegalArgumentException.class);
            fail("Expecting AssertionError but got none");
        }
        catch (AssertionError e) {
        }
    }



    @Test (expectedExceptions = AssertionError.class)
    public void testInconsistentSetAndGet()
            throws Exception
    {
        // Detect inconsistent get/set implementation
        // property "inconsistent" has a bug that setter does not change its value.
        AssertBean.assertSetAndGet(bean, "inconsistent", "xyz");
        fail("Expecting an AssertionError but not getting it");
    }

    @Test
    public void testAssertBetweenRangeInclusive()
            throws Exception
    {
        AssertBean.assertBetweenRangeInclusive(bean, "greaterEqual3LessThanEqual37", 3.0, 37.0);

        try {
            // If the implementation does not match the range given, AssertionError is raised
            AssertBean.assertBetweenRangeInclusive(bean, "greaterEqual3LessThanEqual37", 3.0, 37.000000001);
            fail("Range does not match implementation but got no AssertionError");
        }
        catch (AssertionError ignore)
        {
        }
        try {
            // If the implementation does not match the range given, AssertionError is raised
            AssertBean.assertBetweenRangeInclusive(bean, "greaterEqual3LessThanEqual37", 2.999999999, 37.0);
            fail("Range does not match implementation but got no AssertionError");
        }
        catch (AssertionError ignore)
        {
        }
    }

    @Test
    public void testAssertGreaterThanZero()
            throws Exception
    {
        AssertBean.assertGreaterThanZero(bean, "greaterThanZero");

        try {
            AssertBean.assertGreaterThanZero(bean, "goodDouble");
            fail("goodDouble accepts negative numbers.  Expected AssertionError");
        }
        catch (AssertionError expected)
        {
        }
    }


}
