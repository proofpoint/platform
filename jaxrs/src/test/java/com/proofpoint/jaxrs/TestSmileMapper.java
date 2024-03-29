/*
 * Copyright 2014 Proofpoint, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.proofpoint.json.ObjectMapperProvider;
import jakarta.ws.rs.core.MultivaluedMap;
import org.testng.annotations.BeforeMethod;

import java.io.IOException;

import static org.testng.Assert.assertEquals;

public class TestSmileMapper
        extends AbstractMapperTest<SmileMapper>
{
    @BeforeMethod
    public void setup()
    {
        mapper = new SmileMapper(new ObjectMapperProvider().get());
    }

    @Override
    protected void assertEncodedProperly(byte[] encoded, MultivaluedMap<String, Object> headers, String expected)
            throws IOException
    {
        ObjectMapper smileMapper = new ObjectMapper(new SmileFactory());
        assertEquals(smileMapper.readValue(encoded, String.class), expected);
    }
}
