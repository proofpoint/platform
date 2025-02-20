package com.proofpoint.http.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.proofpoint.json.JsonCodec;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.proofpoint.http.client.FullJsonResponseHandler.JsonResponse;
import static com.proofpoint.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static com.proofpoint.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.testing.TestingResponse.mockResponse;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static java.lang.String.format;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class TestFullJsonResponseHandler
{
    private JsonCodec<User> codec;
    private FullJsonResponseHandler<User> handler;

    @BeforeMethod
    public void setUp()
    {
        codec = JsonCodec.jsonCodec(User.class);
        handler = createFullJsonResponseHandler(codec);
    }

    @Test
    public void testValidJson()
    {
        User user = new User("Joe", 25);
        String json = codec.toJson(user);
        JsonResponse<User> response = handler.handle(null, mockResponse().contentType(JSON_UTF_8).body(json).build());

        assertTrue(response.hasValue());
        assertEquals(response.getJsonBytes(), json.getBytes(StandardCharsets.UTF_8));
        assertEquals(response.getJson(), json);
        assertEquals(response.getValue().getName(), user.getName());
        assertEquals(response.getValue().getAge(), user.getAge());

        assertNotSame(response.getJson(), response.getJson());
        assertNotSame(response.getJsonBytes(), response.getJsonBytes());
        assertNotSame(response.getResponseBytes(), response.getResponseBytes());
        assertNotSame(response.getResponseBody(), response.getResponseBody());

        assertEquals(response.getResponseBytes(), response.getJsonBytes());
        assertEquals(response.getResponseBody(), response.getJson());
    }

    @Test
    public void testInvalidJson()
    {
        String json = "{\"age\": \"foo\"}";
        JsonResponse<User> response = handler.handle(null, mockResponse().contentType(JSON_UTF_8).body(json).build());

        assertFalse(response.hasValue());
        assertEquals(response.getException().getMessage(), format("Unable to create %s from JSON response:\n[%s]", User.class, json));
        assertInstanceOf(response.getException().getCause(), IllegalArgumentException.class);

        assertEquals(response.getException().getCause().getMessage(), "Invalid JSON bytes for [simple type, class com.proofpoint.http.client.TestFullJsonResponseHandler$User]");
        assertEquals(response.getJson(), json);

        assertEquals(response.getResponseBytes(), response.getJsonBytes());
        assertEquals(response.getResponseBody(), response.getJson());
    }

    @Test
    public void testInvalidJsonGetValue()
    {
        String json = "{\"age\": \"foo\"}";
        JsonResponse<User> response = handler.handle(null, mockResponse().contentType(JSON_UTF_8).body(json).build());

        try {
            response.getValue();
            fail("expected exception");
        }
        catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "Response does not contain a JSON value");
            assertEquals(e.getCause(), response.getException());

            assertEquals(response.getJsonBytes(), json.getBytes(StandardCharsets.UTF_8));
            assertEquals(response.getJson(), json);

            assertEquals(response.getResponseBytes(), response.getJsonBytes());
            assertEquals(response.getResponseBody(), response.getJson());
        }
    }

    @Test
    public void testNonJsonResponse()
    {
        JsonResponse<User> response = handler.handle(null, mockResponse().contentType(PLAIN_TEXT_UTF_8).body("hello").build());

        assertFalse(response.hasValue());
        assertNull(response.getException());
        assertNull(response.getJson());
        assertNull(response.getJsonBytes());

        assertEquals(response.getResponseBytes(), "hello".getBytes(StandardCharsets.UTF_8));
        assertEquals(response.getResponseBody(), "hello");
    }

    @Test
    public void testMissingContentType()
    {
        JsonResponse<User> response = handler.handle(null, mockResponse().body("hello").build());

        assertFalse(response.hasValue());
        assertNull(response.getException());
        assertNull(response.getJson());
        assertNull(response.getJsonBytes());

        assertEquals(response.getResponseBytes(), "hello".getBytes(StandardCharsets.UTF_8));
        assertEquals(response.getResponseBody(), "hello");

        assertTrue(response.getHeaders().isEmpty());
    }

    @Test
    public void testJsonErrorResponse()
    {
        String json = "{\"error\": true}";
        JsonResponse<User> response = handler.handle(null, mockResponse().status(INTERNAL_SERVER_ERROR).contentType(JSON_UTF_8).body(json).build());

        assertTrue(response.hasValue());
        assertEquals(response.getJson(), json);
        assertEquals(response.getJsonBytes(), json.getBytes(StandardCharsets.UTF_8));
        assertNull(response.getValue().getName());
        assertEquals(response.getValue().getAge(), 0);

        assertEquals(response.getResponseBytes(), response.getJsonBytes());
        assertEquals(response.getResponseBody(), response.getJson());
    }

    @Test
    public void testJsonReadException()
            throws IOException
    {
        InputStream inputStream = mock(InputStream.class);
        IOException expectedException = new IOException("test exception");
        when(inputStream.read()).thenThrow(expectedException);
        when(inputStream.read(any(byte[].class))).thenThrow(expectedException);
        when(inputStream.read(any(byte[].class), anyInt(), anyInt())).thenThrow(expectedException);

        try {
            handler.handle(prepareGet().setUri(URI.create("https://invalid.invalid/test")).build(), mockResponse()
                    .contentType(JSON_UTF_8)
                    .body(inputStream)
                    .build());
            fail("expected exception");
        }
        catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Failed reading response from server: https://invalid.invalid/test");
            assertSame(e.getCause(), expectedException);
        }
    }

    static class User
    {
        private final String name;
        private final int age;

        @JsonCreator
        User(@JsonProperty("name") String name, @JsonProperty("age") int age)
        {
            this.name = name;
            this.age = age;
        }

        @JsonProperty
        public String getName()
        {
            return name;
        }

        @JsonProperty
        public int getAge()
        {
            return age;
        }
    }
}
