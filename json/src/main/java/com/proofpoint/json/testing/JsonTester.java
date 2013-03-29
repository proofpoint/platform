package com.proofpoint.json.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proofpoint.json.JsonCodec;

import java.io.IOException;

import static com.google.common.base.Throwables.propagate;

public class JsonTester
{
    private JsonTester()
    {
    }

    public static <T> T decodeJson(JsonCodec<T> codec, Object value)
    {
        final String json;
        try {
            json = new ObjectMapper().writeValueAsString(value);
        }
        catch (IOException e) {
            throw propagate(e);
        }
        return codec.fromJson(json);
    }
}
