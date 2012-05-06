package com.proofpoint.http.client;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;

import java.net.URI;

@Beta
public class Request
{
    private final URI uri;
    private final String method;
    private final ListMultimap<String, String> headers;
    private final BodyGenerator bodyGenerator;

    public Request(URI uri, String method, ListMultimap<String, String> headers, BodyGenerator bodyGenerator)
    {
        Preconditions.checkNotNull(uri, "uri is null");
        Preconditions.checkNotNull(method, "method is null");

        this.uri = uri;
        this.method = method;
        this.headers = ImmutableListMultimap.copyOf(headers);
        this.bodyGenerator = bodyGenerator;
    }

    public static Request.Builder builder() {
        return new Builder();
    }

    public URI getUri()
    {
        return uri;
    }

    public String getMethod()
    {
        return method;
    }

    public ListMultimap<String, String> getHeaders()
    {
        return headers;
    }

    public BodyGenerator getBodyGenerator()
    {
        return bodyGenerator;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Request");
        sb.append("{uri=").append(uri);
        sb.append(", method='").append(method).append('\'');
        sb.append(", headers=").append(headers);
        sb.append(", bodyGenerator=").append(bodyGenerator);
        sb.append('}');
        return sb.toString();
    }

    @Beta
    public static class Builder
    {
        public static Builder prepareHead() {
            return new Builder().setMethod("HEAD");
        }

        public static Builder prepareGet() {
            return new Builder().setMethod("GET");
        }

        public static Builder preparePost() {
            return new Builder().setMethod("POST");
        }

        public static Builder preparePut() {
            return new Builder().setMethod("PUT");
        }

        public static Builder prepareDelete() {
            return new Builder().setMethod("DELETE");
        }

        private URI uri;
        private String method;
        private final ListMultimap<String, String> headers = ArrayListMultimap.create();
        private BodyGenerator bodyGenerator;

        public Builder setUri(URI uri)
        {
            this.uri = uri;
            return this;
        }

        public Builder setMethod(String method)
        {
            this.method = method;
            return this;
        }

        public Builder setHeader(String name, String value)
        {
            this.headers.removeAll(name);
            this.headers.put(name, value);
            return this;
        }

        public Builder addHeader(String name, String value)
        {
            this.headers.put(name, value);
            return this;
        }

        public Builder setBodyGenerator(BodyGenerator bodyGenerator)
        {
            this.bodyGenerator = bodyGenerator;
            return this;
        }

        public Request build() {
            return new Request(uri, method, headers, bodyGenerator);
        }
    }
}
