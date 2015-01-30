/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.event.client;

import com.proofpoint.http.client.DynamicBodySource.Writer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Throwables.propagate;

public class TestJsonEventWriterEventWriter
    extends AbstractTestJsonEventWriter
{
    @Override
    <T> void writeEvents(final Iterable<T> events, String token, final OutputStream out)
            throws IOException
    {
        final AtomicBoolean closed = new AtomicBoolean(false);
        Writer writer = eventWriter.createEventWriter(events.iterator(), token, new OutputStream()
        {
            @Override
            public void write(int b)
                    throws IOException
            {
                out.write(b);
            }

            @Override
            public void write(byte[] b)
                    throws IOException
            {
                out.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len)
                    throws IOException
            {
                out.write(b, off, len);
            }

            @Override
            public void flush()
                    throws IOException
            {
                out.flush();
            }

            @Override
            public void close()
            {
                closed.set(true);
            }
        });

        while (!closed.get()) {
            try {
                writer.write();
            }
            catch (IOException e) {
                throw e;
            }
            catch (Exception e) {
                throw propagate(e);
            }
        }

        if (writer instanceof AutoCloseable) {
            try {
                ((AutoCloseable) writer).close();
            }
            catch (Exception e) {
                throw propagate(e);
            }
        }
    }
}
