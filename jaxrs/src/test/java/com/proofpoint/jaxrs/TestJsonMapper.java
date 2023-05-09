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
package com.proofpoint.jaxrs;

import com.google.common.net.HttpHeaders;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.ObjectMapperProvider;
import jakarta.ws.rs.core.MultivaluedMap;
import org.testng.annotations.BeforeMethod;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestJsonMapper
    extends AbstractMapperTest<JsonMapper>
{
    @BeforeMethod
    public void setup()
    {
        mapper = new JsonMapper(new ObjectMapperProvider().get());
    }

    @Override
    protected void assertEncodedProperly(byte[] encoded, MultivaluedMap<String, Object> headers, String expected)
    {
        JsonCodec<String> jsonCodec = JsonCodec.jsonCodec(String.class);
        String json = new String(encoded, UTF_8);
        assertTrue(!json.contains("<"));
        assertTrue(!json.contains(">"));
        assertTrue(!json.contains("'"));
        assertTrue(!json.contains("&"));
        assertEquals(jsonCodec.fromJson(json), expected);

        assertEquals(headers.getFirst(HttpHeaders.X_CONTENT_TYPE_OPTIONS), "nosniff");
    }
}
