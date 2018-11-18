package com.proofpoint.log;

import com.google.inject.Guice;
import com.google.inject.Stage;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

public class TestLogJmxModule
{
    @Test
    public void testModule()
    {
        Guice.createInjector(Stage.PRODUCTION,
                new TestingMBeanModule(),
                new LogJmxModule());
    }
}

