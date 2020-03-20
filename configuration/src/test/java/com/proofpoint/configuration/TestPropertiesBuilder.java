/*
 * Copyright 2020 Proofpoint, Inc.
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
package com.proofpoint.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import com.proofpoint.configuration.ConfigurationFactoryTest.AnnotatedSetter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static com.proofpoint.configuration.ConfigBinder.bindConfig;
import static com.proofpoint.testing.Assertions.assertContainsAllOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestPropertiesBuilder
{
    private File tempDir;

    @BeforeMethod
    public void setup()
    {
        tempDir = Files.createTempDir();
    }

    @AfterMethod
    public void teardown()
            throws IOException
    {
        deleteRecursively(tempDir.toPath(), ALLOW_INSECURE);
    }

    @Test
    public void testLoadsFromSystemProperties()
    {
        System.setProperty("test", "foo");

        PropertiesBuilder builder = new PropertiesBuilder()
                .withSystemProperties();

        assertEquals(builder.getProperties().get("test"), "foo");
        assertEquals(builder.getExpectToUse(), ImmutableSet.of());
        assertEquals(builder.getErrors(), ImmutableList.of());

        System.getProperties().remove("test");
    }

    @Test
    public void testLoadsFromFile()
            throws IOException
    {
        File file = File.createTempFile("config", ".properties", tempDir);
        try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
            out.print("test: f\u014do");
        }

        PropertiesBuilder builder = new PropertiesBuilder()
                .withPropertiesFile(file.getAbsolutePath())
                .withSystemProperties();

        assertEquals(builder.getProperties().get("test"), "f\u014do");
        assertEquals(builder.getExpectToUse(), ImmutableSet.of("test"));
        assertEquals(builder.getErrors(), ImmutableList.of());
    }

    @Test
    public void testSystemOverridesFile()
            throws IOException
    {
        File file = File.createTempFile("config", ".properties", tempDir);
        try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
            out.println("key1: original");
            out.println("key2: original");
        }
        System.setProperty("key1", "overridden");

        Map<String, String> properties = new PropertiesBuilder()
                .withPropertiesFile(file.getAbsolutePath())
                .withSystemProperties()
                .getProperties();

        assertEquals(properties.get("key1"), "overridden");
        assertEquals(properties.get("key2"), "original");
    }

    @Test
    public void testDuplicatePropertiesInFileReturnsError()
            throws IOException
    {
        File file = File.createTempFile("config", ".properties", tempDir);
        try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
            out.print("string-value: foo\n");
            out.print("string-value: foo");
        }

        PropertiesBuilder builder = new PropertiesBuilder()
                .withPropertiesFile(file.getAbsolutePath());

        assertEquals(builder.getErrors(), ImmutableList.of("Duplicate configuration property 'string-value' in file " + file.getAbsolutePath()));
    }

    @Test
    public void testLoadsFromJsonFile()
            throws IOException
    {
        File file = File.createTempFile("config", ".json", tempDir);
        try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
            out.print("{\n" +
                    "\"string\": \"f\u014do\",\n" +
                    "\"number\": 2,\n" +
                    "\"bool\": true,\n" +
                    "\"list\": [\"b\", \"a\"],\n" +
                    "\"map\": {\"c\": \"d\"},\n" +
                    "\"map.c\": null\n" + // Ensure entries with null values ignored, even if duplicate
                    "}");
        }

        PropertiesBuilder builder = new PropertiesBuilder()
                .withJsonFile(file.getAbsolutePath());

        assertEquals(builder.getProperties(), ImmutableMap.builder()
                .put("string", "f\u014do")
                .put("number", "2")
                .put("bool", "true")
                .put("list.1", "b")
                .put("list.2", "a")
                .put("map.c", "d")
                .build()
        );
        assertEquals(builder.getExpectToUse(), ImmutableSet.of("string", "number", "bool", "list.1", "list.2", "map.c"));
        assertEquals(builder.getErrors(), ImmutableList.of());
    }

    @Test
    public void testNullJsonFile()
            throws IOException
    {
        final File file = File.createTempFile("config", ".json", tempDir);
        try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
            out.print("null");
        }

        PropertiesBuilder builder = new PropertiesBuilder()
                .withJsonFile(file.getAbsolutePath());

        assertEquals(builder.getProperties(), ImmutableMap.of());
        assertEquals(builder.getExpectToUse(), ImmutableSet.of());
        assertEquals(builder.getErrors(), ImmutableList.of());
    }


    @Test
    public void testDuplicatePropertiesInJsonFileThrowsError()
            throws IOException
    {
        final File file = File.createTempFile("config", ".json", tempDir);
        try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
            out.print("{\n" +
                    "\"string.value\": \"foo\",\n" +
                    "\"string\": {\"value\": \"foo\"}\n" +
                    "}");
        }

        PropertiesBuilder builder = new PropertiesBuilder()
                .withJsonFile(file.getAbsolutePath());

        assertEquals(builder.getErrors(), ImmutableList.of("Duplicate configuration property 'string.value' in file " + file.getAbsolutePath()));
    }

    @Test
    public void testPropertyInMultipleJsonFilesReturnsError()
            throws IOException
    {
        File file = File.createTempFile("config", ".json", tempDir);
        try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
            out.print("{\"string\": \"foo\"}");
        }
        File secondFile = File.createTempFile("config", ".json", tempDir);
        try (PrintStream out = new PrintStream(new FileOutputStream(secondFile))) {
            out.print("{\"string\": \"foo\"}");
        }

        PropertiesBuilder builder = new PropertiesBuilder()
                .withJsonFile(file.getAbsolutePath())
                .withJsonFile(secondFile.getAbsolutePath());

        assertEquals(builder.getErrors(), ImmutableList.of("Duplicate configuration property 'string' in file " + secondFile.getAbsolutePath()));
    }
}
