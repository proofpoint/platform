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
package com.proofpoint.json;

import com.google.common.base.Strings;
import com.google.common.reflect.TypeToken;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.json.JsonCodec.listJsonCodec;
import static com.proofpoint.json.JsonCodec.mapJsonCodec;
import static com.proofpoint.testing.Assertions.assertContains;
import static com.proofpoint.testing.Assertions.assertNotContains;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestJsonCodec
{
    @Test
    public void testJsonCodec()
            throws Exception
    {
        JsonCodec<Person> jsonCodec = jsonCodec(Person.class);

        Person.validatePersonJsonCodec(jsonCodec);
    }

    @Test
    public void testListJsonCodec()
            throws Exception
    {
        JsonCodec<List<Person>> jsonCodec = listJsonCodec(Person.class);

        Person.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testListJsonCodecFromJsonCodec()
            throws Exception
    {
        JsonCodec<List<Person>> jsonCodec = listJsonCodec(jsonCodec(Person.class));

        Person.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testTypeTokenList()
            throws Exception
    {
        JsonCodec<List<Person>> jsonCodec = jsonCodec(new TypeToken<List<Person>>() {});

        Person.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testMapJsonCodec()
            throws Exception
    {
        JsonCodec<Map<String, Person>> jsonCodec = mapJsonCodec(String.class, Person.class);

        Person.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testMapJsonCodecFromJsonCodec()
            throws Exception
    {
        JsonCodec<Map<String, Person>> jsonCodec = mapJsonCodec(String.class, jsonCodec(Person.class));

        Person.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testTypeLiteralMap()
            throws Exception
    {
        JsonCodec<Map<String, Person>> jsonCodec = jsonCodec(new TypeToken<Map<String, Person>>() {});

        Person.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableJsonCodec()
            throws Exception
    {
        JsonCodec<ImmutablePerson> jsonCodec = jsonCodec(ImmutablePerson.class);

        ImmutablePerson.validatePersonJsonCodec(jsonCodec);
    }

    @Test
    public void testAsymmetricJsonCodec()
            throws Exception
    {
        JsonCodec<ImmutablePerson> jsonCodec = jsonCodec(ImmutablePerson.class);
        ImmutablePerson immutablePerson = jsonCodec.fromJson("{ \"notWritable\": \"foo\" }");
        assertNull(immutablePerson.getNotWritable());
    }

    @Test
    public void testImmutableListJsonCodec()
            throws Exception
    {
        JsonCodec<List<ImmutablePerson>> jsonCodec = listJsonCodec(ImmutablePerson.class);

        ImmutablePerson.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableListJsonCodecFromJsonCodec()
            throws Exception
    {
        JsonCodec<List<ImmutablePerson>> jsonCodec = listJsonCodec(jsonCodec(ImmutablePerson.class));

        ImmutablePerson.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableTypeTokenList()
            throws Exception
    {
        JsonCodec<List<ImmutablePerson>> jsonCodec = jsonCodec(new TypeToken<List<ImmutablePerson>>() {});

        ImmutablePerson.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableMapJsonCodec()
            throws Exception
    {
        JsonCodec<Map<String, ImmutablePerson>> jsonCodec = mapJsonCodec(String.class, ImmutablePerson.class);

        ImmutablePerson.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableMapJsonCodecFromJsonCodec()
            throws Exception
    {
        JsonCodec<Map<String, ImmutablePerson>> jsonCodec = mapJsonCodec(String.class, jsonCodec(ImmutablePerson.class));

        ImmutablePerson.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableTypeTokenMap()
            throws Exception
    {
        JsonCodec<Map<String, ImmutablePerson>> jsonCodec = jsonCodec(new TypeToken<Map<String, ImmutablePerson>>() {});

        ImmutablePerson.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testIsPretty()
    {
        JsonCodec<Person> jsonCodec = jsonCodec(Person.class);
        String json = jsonCodec.toJson(new Person().setName("dain").setRocks(true));
        assertContains(json, "\n");
    }

    @Test
    public void testNonPretty()
    {
        JsonCodec<Person> prettyJsonCodec = jsonCodec(Person.class);
        JsonCodec<Person> jsonCodec = prettyJsonCodec.withoutPretty();

        String json = jsonCodec.toJson(new Person().setName("dain").setRocks(true));
        assertNotContains(json, "\n");

        Person.validatePersonJsonCodec(jsonCodec);

        json = prettyJsonCodec.toJson(new Person().setName("dain").setRocks(true));
        assertContains(json, "\n");
    }

    @Test
    public void testToJsonWithLengthLimitSimple()
    {
        JsonCodec<ImmutablePerson> jsonCodec = jsonCodec(ImmutablePerson.class);
        ImmutablePerson person = new ImmutablePerson(Strings.repeat("a", 1000), false);

        assertFalse(jsonCodec.toJsonWithLengthLimit(person, 0).isPresent());
        assertFalse(jsonCodec.toJsonWithLengthLimit(person, 1000).isPresent());
        assertFalse(jsonCodec.toJsonWithLengthLimit(person, 1035).isPresent());
        assertTrue(jsonCodec.toJsonWithLengthLimit(person, 1036).isPresent());
    }

    @Test
    public void testToJsonWithLengthLimitNonAscii()
    {
        JsonCodec<ImmutablePerson> jsonCodec = jsonCodec(ImmutablePerson.class);
        ImmutablePerson person = new ImmutablePerson(Strings.repeat("\u0158", 1000), false);

        assertFalse(jsonCodec.toJsonWithLengthLimit(person, 0).isPresent());
        assertFalse(jsonCodec.toJsonWithLengthLimit(person, 1000).isPresent());
        assertFalse(jsonCodec.toJsonWithLengthLimit(person, 1035).isPresent());
        assertTrue(jsonCodec.toJsonWithLengthLimit(person, 1036).isPresent());
    }

    @Test
    public void testToJsonWithLengthLimitComplex()
    {
        JsonCodec<List<ImmutablePerson>> jsonCodec = listJsonCodec(jsonCodec(ImmutablePerson.class));
        ImmutablePerson person = new ImmutablePerson(Strings.repeat("a", 1000), false);
        List<ImmutablePerson> people = Collections.nCopies(10, person);

        assertFalse(jsonCodec.toJsonWithLengthLimit(people, 0).isPresent());
        assertFalse(jsonCodec.toJsonWithLengthLimit(people, 5000).isPresent());
        assertFalse(jsonCodec.toJsonWithLengthLimit(people, 10381).isPresent());
        assertTrue(jsonCodec.toJsonWithLengthLimit(people, 10382).isPresent());
    }
}
