package com.proofpoint.configuration;

import com.google.common.collect.Iterators;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import com.proofpoint.configuration.ConfigurationFactoryTest.AnnotatedSetter;
import com.proofpoint.configuration.ConfigurationFactoryTest.LegacyMapConfigPresent;
import com.proofpoint.configuration.ConfigurationFactoryTest.LegacyMapValueConfigPresent;
import com.proofpoint.configuration.ConfigurationInspector.ConfigAttribute;
import com.proofpoint.configuration.ConfigurationInspector.ConfigRecord;
import com.proofpoint.configuration.ConfigurationMetadataTest.SetterSensitiveClass;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestConfigurationInspector
{

    private static final String PACKAGE_NAME = "com.proofpoint.configuration.";

    private static InspectionVerifier inspect(Map<String, String> properties, Module module)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        List<Message> messages = new ConfigurationValidator(configurationFactory, null).validate(module);
        Guice.createInjector(new ConfigurationModule(configurationFactory), module, new ValidationErrorModule(messages));
        return new InspectionVerifier(new ConfigurationInspector().inspect(configurationFactory));
    }

    @Test
    public void testSimpleConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        inspect(properties, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(AnnotatedSetter.class);
            }
        })
                .component("ConfigurationFactoryTest$AnnotatedSetter")
                .value("BooleanValue", "boolean-value", "false", "true", "")
                .value("StringValue", "string-value", "null", "some value", "")
                .end();
    }

    @Test
    public void testSecuritySensitiveConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("value", "some value");
        inspect(properties, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(SetterSensitiveClass.class);
            }
        })
                .component("ConfigurationMetadataTest$SetterSensitiveClass")
                .value("Value", "value", "[REDACTED]", "[REDACTED]", "description")
                .end();
    }

    @Test
    public void testSimpleMapConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.a", "this is a");
        properties.put("map-a.b", "this is b");
        inspect(properties, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(LegacyMapConfigPresent.class);
            }
        })
                .component("ConfigurationFactoryTest$LegacyMapConfigPresent")
                .value("MapA[a]", "map-a.a", "-- n/a --", "this is a", "")
                .value("MapA[b]", "map-a.b", "-- n/a --", "this is b", "")
                .value("MapB", "map-b", "-- n/a --", "-- empty --", "")
                .end();
    }

    @Test
    public void testConfigMapValueConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k1.string-value", "this is a");
        properties.put("map-a.k1.string-b", "this is b");
        properties.put("map-a.k2.string-value", "this is k2 a");
        properties.put("map-a.k2.string-b", "this is k2 b");
        inspect(properties, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(LegacyMapValueConfigPresent.class);
            }
        })
                .component("ConfigurationFactoryTest$LegacyMapValueConfigPresent")
                .value("MapA[k1]StringA", "map-a.k1.string-a", "defaultA", "this is a", "")
                .value("MapA[k1]StringB", "map-a.k1.string-b", "defaultB", "this is b", "")
                .value("MapA[k2]StringA", "map-a.k2.string-a", "defaultA", "this is k2 a", "")
                .value("MapA[k2]StringB", "map-a.k2.string-b", "defaultB", "this is k2 b", "")
                .end();
    }

    private static class InspectionVerifier
    {
        private final Iterator<ConfigRecord<?>> recordIterator;
        private Iterator<ConfigAttribute> attributeIterator = null;

        public InspectionVerifier(SortedSet<ConfigRecord<?>> inspect)
        {
            recordIterator = inspect.iterator();
        }

        public InspectionVerifier component(String expectedName)
        {
            if (attributeIterator != null && attributeIterator.hasNext()) {
                fail("Extra attributes: " + Iterators.toString(attributeIterator));
            }
            ConfigRecord<?> record = recordIterator.next();
            assertEquals(record.getComponentName(), PACKAGE_NAME + expectedName);
            attributeIterator = record.getAttributes().iterator();
            return this;
        }

        public InspectionVerifier value(String attributeName, String propertyName, String defaultValue, String currentValue, String description)
        {
            final ConfigAttribute attribute = attributeIterator.next();
            assertEquals(attribute.getAttributeName(), attributeName, "Attribute name");
            assertEquals(attribute.getPropertyName(), propertyName, "Property name");
            assertEquals(attribute.getDefaultValue(), defaultValue, "Default value");
            assertEquals(attribute.getCurrentValue(), currentValue, "Current value");
            assertEquals(attribute.getDescription(), description, "Description");
            return this;
        }

        public void end()
        {
            if (attributeIterator != null && attributeIterator.hasNext()) {
                fail("Extra attributes: " + Iterators.toString(attributeIterator));
            }
            if (recordIterator != null && recordIterator.hasNext()) {
                fail("Extra components: " + Iterators.toString(recordIterator));
            }
        }
    }
}
