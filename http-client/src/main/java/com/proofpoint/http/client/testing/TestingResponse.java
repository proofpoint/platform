package com.proofpoint.http.client.testing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CountingInputStream;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.proofpoint.http.client.HeaderName;
import com.proofpoint.http.client.HttpStatus;
import com.proofpoint.http.client.Response;
import com.proofpoint.json.ObjectMapperProvider;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class TestingResponse
        implements Response
{
    private final HttpStatus status;
    private final ListMultimap<HeaderName, String> headers;
    private final CountingInputStream countingInputStream;

    private TestingResponse(HttpStatus status, ListMultimap<String, String> headers, byte[] bytes)
    {
        this(status, headers, new ByteArrayInputStream(requireNonNull(bytes, "bytes is null")));
    }

    private TestingResponse(HttpStatus status, ListMultimap<String, String> headers, InputStream input)
    {
        this.status = requireNonNull(status, "status is null");
        this.headers = ImmutableListMultimap.copyOf(toHeaderMap(requireNonNull(headers, "headers is null")));
        this.countingInputStream = new CountingInputStream(requireNonNull(input, "input is null"));
    }

    @Override
    public int getStatusCode()
    {
        return status.code();
    }

    @Override
    public String getStatusMessage()
    {
        return status.reason();
    }

    @Override
    public ListMultimap<HeaderName, String> getHeaders()
    {
        return headers;
    }

    @Override
    public long getBytesRead()
    {
        return countingInputStream.getCount();
    }

    @Override
    public InputStream getInputStream()
    {
        return countingInputStream;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("statusCode", getStatusCode())
                .add("statusMessage", getStatusMessage())
                .add("headers", getHeaders())
                .toString();
    }

    public static ListMultimap<String, String> contentType(MediaType type)
    {
        return ImmutableListMultimap.of(HttpHeaders.CONTENT_TYPE, type.toString());
    }

    /**
     * Returns a response with the specified status.
     */
    public static Response mockResponse(HttpStatus status)
    {
        return new TestingResponse(status, ImmutableListMultimap.of(), new byte[0]);
    }

    /**
     * Returns a new builder.
     */
    public static Builder mockResponse()
    {
        return new Builder();
    }

    /**
     * A builder for creating {@link TestingResponse} instances.
     */
    public static class Builder
    {
        private static final byte[] ZERO_LENGTH_BYTES = new byte[0];

        private HttpStatus status;
        private final ListMultimap<String, String> headers = ArrayListMultimap.create();
        private byte[] bytes;
        private InputStream inputStream;
        private String defaultContentType;

        private Builder()
        {
        }

        /**
         * Sets the response's status.
         *
         * If this method is not called, the builder uses {@link HttpStatus#OK}
         * if the body is set or {@link HttpStatus#NO_CONTENT} if the body is not set.
         */
        public Builder status(HttpStatus status)
        {
            checkState(this.status == null, "status is already set");
            this.status = requireNonNull(status, "status is null");
            return this;
        }

        /**
         * Adds a header to the response. May be called multiple times.
         */
        public Builder header(String field, String value)
        {
            headers.put(requireNonNull(field, "field is null"), requireNonNull(value, "value is null"));
            return this;
        }

        /**
         * Adds headers to the response. May be called multiple times.
         */
        public Builder headers(ListMultimap<String, String> headers)
        {
            this.headers.putAll(requireNonNull(headers, "headers is null"));
            return this;
        }

        /**
         * Adds a Content-Type: header to the response.
         */
        public Builder contentType(MediaType type)
        {
            return header(HttpHeaders.CONTENT_TYPE, type.toString());
        }

        /**
         * Adds a Content-Type: header to the response.
         */
        public Builder contentType(String type)
        {
            return header(HttpHeaders.CONTENT_TYPE, type);
        }

        /**
         * Sets the response's body to a byte array.
         */
        public Builder body(byte[] bytes)
        {
            requireNonNull(bytes, "bytes is null");
            checkState(this.bytes == null && this.inputStream == null, "body is already set");
            this.bytes = Arrays.copyOf(bytes, bytes.length);
            return this;
        }

        /**
         * Sets the response's body to the UTF-8 encoding of a string
         */
        public Builder body(String content)
        {
            requireNonNull(content, "content is null");
            checkState(this.bytes == null && this.inputStream == null, "body is already set");
            bytes = content.getBytes(UTF_8);
            return this;
        }

        /**
         * Sets the response's body to an {@link InputStream}.
         */
        public Builder body(InputStream inputStream)
        {
            checkState(this.bytes == null && this.inputStream == null, "body is already set");
            this.inputStream = requireNonNull(inputStream, "inputStream is null");
            return this;
        }

        /**
         * Sets the response's body to the JSON encoding of an entity. Defaults the
         * Content-Type: header to "application/json; charset=utf-8".
         */
        public Builder jsonBody(@Nullable Object entity)
        {
            checkState(this.bytes == null && this.inputStream == null, "body is already set");
            defaultContentType = APPLICATION_JSON;
            try {
                bytes = new ObjectMapperProvider().get().writeValueAsBytes(entity);
            }
            catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        /**
         * Returns a newly created TestingResponse.
         */
        public TestingResponse build()
        {
            if (status == null) {
                if (bytes == null && this.inputStream == null) {
                    status = HttpStatus.NO_CONTENT;
                }
                else {
                    status = HttpStatus.OK;
                }
            }

            if (defaultContentType != null) {
                boolean haveType = false;
                for (String header : headers.keys()) {
                    if ("content-type".equalsIgnoreCase(header)) {
                        haveType = true;
                        break;
                    }
                }
                if (!haveType) {
                    header("Content-Type", defaultContentType);
                }
            }

            if (inputStream != null) {
                return new TestingResponse(status, headers, inputStream);
            }

            return new TestingResponse(status, headers, requireNonNullElse(bytes, ZERO_LENGTH_BYTES));
        }
    }

    private static ListMultimap<HeaderName, String> toHeaderMap(ListMultimap<String, String> headers)
    {
        ImmutableListMultimap.Builder<HeaderName, String> builder = ImmutableListMultimap.builder();
        for (Map.Entry<String, String> entry : headers.entries()) {
            builder.put(HeaderName.of(entry.getKey()), entry.getValue());
        }
        return builder.build();
    }
}
