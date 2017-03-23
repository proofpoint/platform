package com.proofpoint.http.server;

import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty.server.HttpInput;

import java.io.IOException;
import java.lang.reflect.Field;

class HackHttpInput
    extends HttpInput
{
    private static final Field BLOCK_UNTIL_FIELD;

    static {
        try {
            BLOCK_UNTIL_FIELD = HttpInput.class.getDeclaredField("_blockUntil");
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        BLOCK_UNTIL_FIELD.setAccessible(true);
    }

    HackHttpInput(HttpChannelState state)
    {
        super(state);
    }

    @Override
    public int read(byte[] b, int off, int len)
            throws IOException
    {
        try {
            BLOCK_UNTIL_FIELD.setLong(this, 0);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return super.read(b, off, len);
    }
}
