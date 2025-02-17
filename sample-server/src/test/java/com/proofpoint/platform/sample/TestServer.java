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
package com.proofpoint.platform.sample;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import java.net.URI;
import java.util.Map;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.Request.Builder.prepareDelete;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.Request.Builder.preparePost;
import static com.proofpoint.http.client.Request.Builder.preparePut;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.json.JsonCodec.mapJsonCodec;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;

public class TestServer
{
    private static final int NOT_ALLOWED = 405;

    private final Map<String, Object> personJsonStructure = ImmutableMap.of(
            "name", "Mr Foo",
            "email", "foo@example.com"
    );
    private final JsonCodec<Map<String, Object>> mapCodec = mapJsonCodec(String.class, Object.class);
    private final HttpClient client = new JettyHttpClient();

    private LifeCycleManager lifeCycleManager;
    private TestingHttpServer server;
    private PersonStore store;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        new TestingHttpServerModule(),
                        new JsonModule(),
                        new JaxrsModule(),
                        new ReportingModule(),
                        new TestingMBeanModule(),
                        new MainModule()
                )
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
        store = injector.getInstance(PersonStore.class);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
            throws Exception
    {
        if (lifeCycleManager != null) {
            lifeCycleManager.stop();
        }
    }

    @AfterClass(alwaysRun = true)
    public void teardownClass()
    {
        Closeables.closeQuietly(client);
    }

    @Test
    public void testEmpty()
    {
        Map<String, Object> response = client.execute(
                prepareGet().setUri(uriFor("/v1/person")).build(),
                createJsonResponseHandler(mapCodec, OK.getStatusCode()));

        assertThat(response).isEmpty();
    }

    @Test
    public void testGetAll()
    {
        store.put("bar", new Person("bar@example.com", "Mr Bar"));
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Object expected = ImmutableMap.of(
                "foo", ImmutableMap.of("name", "Mr Foo", "email", "foo@example.com"),
                "bar", ImmutableMap.of("name", "Mr Bar", "email", "bar@example.com")
        );

        Object actual = client.execute(
                prepareGet().setUri(uriFor("/v1/person")).build(),
                createJsonResponseHandler(mapCodec, OK.getStatusCode()));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testGetNotFound()
    {
        URI requestUri = uriFor("/v1/person/foo");

        StatusResponse response = client.execute(
                prepareGet().setUri(requestUri).build(),
                createStatusResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND.getStatusCode());
    }

    @Test
    public void testGetSingle()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        URI requestUri = uriFor("/v1/person/foo");

        Map<String, String> expected = ImmutableMap.of("name", "Mr Foo", "email", "foo@example.com");

        Map<String, Object> actual = client.execute(
                prepareGet().setUri(requestUri).build(),
                createJsonResponseHandler(mapCodec, OK.getStatusCode()));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testPutAdd()
    {
        StringResponse response = client.execute(
                preparePut()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .setBodySource(jsonBodyGenerator(mapCodec, personJsonStructure))
                        .build(),
                createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(CREATED.getStatusCode());
        assertThat(response.getHeader(CONTENT_TYPE)).isNull();
        assertThat(response.getBody()).isEmpty();

        assertThat(store.get("foo")).isEqualTo(new Person("foo@example.com", "Mr Foo"));
    }

    @Test
    public void testPutReplace()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        StringResponse response = client.execute(
                preparePut()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .setBodySource(jsonBodyGenerator(mapCodec, personJsonStructure))
                        .build(),
                createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(NO_CONTENT.getStatusCode());
        assertThat(response.getHeader(CONTENT_TYPE)).isNull();
        assertThat(response.getBody()).isEmpty();

        assertThat(store.get("foo")).isEqualTo(new Person("foo@example.com", "Mr Foo"));
    }

    @Test
    public void testDelete()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        StringResponse response = client.execute(
                prepareDelete()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .build(),
                createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(NO_CONTENT.getStatusCode());
        assertThat(response.getHeader(CONTENT_TYPE)).isNull();
        assertThat(response.getBody()).isEmpty();

        assertThat(store.get("foo")).isNull();
    }

    @Test
    public void testDeleteMissing()
    {
        StringResponse response = client.execute(
                prepareDelete()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .build(),
                createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND.getStatusCode());
    }

    @Test
    public void testPostNotAllowed()
    {
        StatusResponse response = client.execute(
                preparePost()
                        .setUri(uriFor("/v1/person/foo"))
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .setBodySource(jsonBodyGenerator(mapCodec, personJsonStructure))
                        .build(),
                createStatusResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(NOT_ALLOWED);

        assertThat(store.get("foo")).isNull();
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }
}
