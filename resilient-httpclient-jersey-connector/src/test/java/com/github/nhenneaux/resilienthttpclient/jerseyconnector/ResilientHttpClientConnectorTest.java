package com.github.nhenneaux.resilienthttpclient.jerseyconnector;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.junit.jupiter.api.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ResilientHttpClientConnectorTest {

    private static final NoOpCallback CALLBACK = new NoOpCallback();
    private static final String JSON = "\n" +
            "\n" +
            "{\n" +
            "  \"id\": \"7875be4b-917d-4aff-8cc4-5606c36bf418\",\n" +
            "  \"name\": \"Sample Postman Collection\",\n" +
            "  \"description\": \"A sample collection to demonstrate collections as a set of related requests\",\n" +
            "  \"order\": [\n" +
            "    \"4d9134be-e8bf-4693-9cd7-1c0fc66ae739\",\n" +
            "    \"141ba274-cc50-4377-a59c-e080066f375e\"\n" +
            "  ],\n" +
            "  \"folders\": [],\n" +
            "  \"requests\": [\n" +
            "    {\n" +
            "      \"id\": \"4d9134be-e8bf-4693-9cd7-1c0fc66ae739\",\n" +
            "      \"name\": \"A simple GET request\",\n" +
            "      \"collectionId\": \"877b9dae-a50e-4152-9b89-870c37216f78\",\n" +
            "      \"method\": \"GET\",\n" +
            "      \"headers\": \"\",\n" +
            "      \"data\": [],\n" +
            "      \"rawModeData\": \"\",\n" +
            "      \"tests\": \"tests['response code is 200'] = (responseCode.code === 200);\",\n" +
            "      \"preRequestScript\": \"\",\n" +
            "      \"url\": \"https://postman-echo.com/get?source=newman-sample-github-collection\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": \"141ba274-cc50-4377-a59c-e080066f375e\",\n" +
            "      \"name\": \"A simple POST request\",\n" +
            "      \"collectionId\": \"877b9dae-a50e-4152-9b89-870c37216f78\",\n" +
            "      \"method\": \"POST\",\n" +
            "      \"headers\": \"Content-Type: text/plain\",\n" +
            "      \"dataMode\": \"raw\",\n" +
            "      \"data\": [],\n" +
            "      \"rawModeData\": \"Duis posuere augue vel cursus pharetra. In luctus a ex nec pretium...\",\n" +
            "      \"url\": \"https://postman-echo.com/post\"\n" +
            "    }\n" +
            "  ]\n" +
            "}\n" +
            "\n";

    @Test
    void shouldWorkWithJaxRsClientForStatus() {
        final Client client = ClientBuilder.newClient(new ClientConfig().connectorProvider((jaxRsClient, config) -> new ResilientHttpClientConnector(HttpClient.newBuilder().sslContext(jaxRsClient.getSslContext()).build())));
        final WebTarget target = client.target("https://devoxx.be");
        final Response response = target.request().get();
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldWorkWithJaxRsClientForStatusAsync() throws InterruptedException, ExecutionException, TimeoutException {
        final Client client = ClientBuilder.newClient(new ClientConfig().connectorProvider((jaxRsClient, config) -> new ResilientHttpClientConnector(HttpClient.newBuilder().sslContext(jaxRsClient.getSslContext()).build())));
        final WebTarget target = client.target("https://devoxx.be");
        final Response response = target.request().async().get().get(2, TimeUnit.SECONDS);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldWorkWithJaxRsClientForStatusAsyncWithCallback() throws InterruptedException, ExecutionException, TimeoutException {
        final Client client = ClientBuilder.newClient(new ClientConfig().connectorProvider((jaxRsClient, config) -> new ResilientHttpClientConnector(HttpClient.newBuilder().sslContext(jaxRsClient.getSslContext()).build())));
        final WebTarget target = client.target("https://devoxx.be");
        final Response response = target.request().async().get(CALLBACK).get(2, TimeUnit.SECONDS);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldWorkWithJaxRsClientForStatusAsyncWithCallbackCheck() throws InterruptedException, ExecutionException, TimeoutException {
        final Client client = ClientBuilder.newClient(new ClientConfig().connectorProvider((jaxRsClient, config) -> new ResilientHttpClientConnector(HttpClient.newBuilder().sslContext(jaxRsClient.getSslContext()).build())));
        final WebTarget target = client.target("https://devoxx.be");
        final AtomicReference<Response> objectAtomicReference = new AtomicReference<>();
        final Response response = target.request().async().get(new InvocationCallback<Response>() {
            @Override
            public void completed(Response response) {
                objectAtomicReference.set(response);
            }

            @Override
            public void failed(Throwable throwable) {

            }
        }).get(2, TimeUnit.SECONDS);

        assertSame(response, objectAtomicReference.get());
        assertEquals(200, response.getStatus());
        assertEquals(200, objectAtomicReference.get().getStatus());
    }

    @Test
    void shouldWorkWithJaxRsClientForString() {
        final Client client = ClientBuilder.newClient(new ClientConfig().connectorProvider((jaxRsClient, config) -> new ResilientHttpClientConnector(HttpClient.newBuilder().sslContext(jaxRsClient.getSslContext()).build())));
        final WebTarget target = client.target("https://devoxx.be");
        final Response response = target.request().get();
        assertNotNull(response.readEntity(String.class));
    }

    @Test
    void shouldWorkWithJaxRsClientWithPost() {
        final Client client = ClientBuilder.newClient(new ClientConfig().connectorProvider((jaxRsClient, config) -> new ResilientHttpClientConnector(HttpClient.newBuilder().sslContext(jaxRsClient.getSslContext()).build())));
        final WebTarget target = client.target("https://postman-echo.com/post");
        final Form form = new Form();
        form.param("foo1", "bar1");
        form.param("foo2", "bar2");
        final Response response = target.request().post(Entity.form(form));

        assertEquals(200, response.getStatus());
        assertNotNull(response.readEntity(String.class));
    }

    @Test
    void shouldWorkWithJaxRsClientWithJsonPost() {
        final Client client = ClientBuilder.newClient(new ClientConfig().connectorProvider((jaxRsClient, config) -> new ResilientHttpClientConnector(HttpClient.newBuilder().sslContext(jaxRsClient.getSslContext()).build())));
        final WebTarget target = client.target("https://postman-echo.com/post");
        final Response response = target.request().post(Entity.entity(JSON, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(200, response.getStatus());
        assertNotNull(response.readEntity(String.class));
    }

    @Test
    void shouldWorkWithJaxRsClientWithJsonPostAsync() throws ExecutionException, InterruptedException, TimeoutException {
        final Client client = ClientBuilder.newClient(new ClientConfig().connectorProvider((jaxRsClient, config) -> new ResilientHttpClientConnector(HttpClient.newBuilder().sslContext(jaxRsClient.getSslContext()).build())));
        final WebTarget target = client.target("https://postman-echo.com/post");
        final Future<Response> responseFuture = target.request().async().post(Entity.entity(JSON, MediaType.APPLICATION_JSON_TYPE));
        final Response response = responseFuture.get(2, TimeUnit.SECONDS);
        assertEquals(200, response.getStatus());
        assertNotNull(response.readEntity(String.class));
    }

    @Test
    void shouldWorkWithJaxRsClientWithJsonPostAsyncWithCallback() throws ExecutionException, InterruptedException, TimeoutException {
        final Client client = ClientBuilder.newClient(new ClientConfig().connectorProvider((jaxRsClient, config) -> new ResilientHttpClientConnector(HttpClient.newBuilder().sslContext(jaxRsClient.getSslContext()).build())));
        final WebTarget target = client.target("https://postman-echo.com/post");
        final Future<Response> responseFuture = target.request().async().post(Entity.entity(JSON, MediaType.APPLICATION_JSON_TYPE), CALLBACK);
        final Response response = responseFuture.get(2, TimeUnit.SECONDS);
        assertEquals(200, response.getStatus());
        assertNotNull(response.readEntity(String.class));
    }

    @Test
    void shouldWorkWithJaxRsClientWithJsonPostAsyncWithCallbackCheck() throws ExecutionException, InterruptedException, TimeoutException {
        final Client client = ClientBuilder.newClient(new ClientConfig().connectorProvider((jaxRsClient, config) -> new ResilientHttpClientConnector(HttpClient.newBuilder().sslContext(jaxRsClient.getSslContext()).build())));
        final WebTarget target = client.target("https://postman-echo.com/post");

        final AtomicReference<Response> objectAtomicReference = new AtomicReference<>();
        final Future<Response> responseFuture = target.request().async().post(Entity.entity(JSON, MediaType.APPLICATION_JSON_TYPE), new InvocationCallback<>() {
            @Override
            public void completed(Response response) {
                objectAtomicReference.set(response);
            }

            @Override
            public void failed(Throwable throwable) {

            }
        });
        final Response response = responseFuture.get(2, TimeUnit.SECONDS);

        assertSame(response, objectAtomicReference.get());
        assertEquals(200, response.getStatus());
        assertEquals(200, objectAtomicReference.get().getStatus());
        assertNotNull(response.readEntity(String.class));
    }

    @Test
    void shouldWorkWithJaxRsClientWithStreamPost() throws IOException {
        final ClientConfig configuration = new ClientConfig().connectorProvider((jaxRsClient, config) -> new ResilientHttpClientConnector(HttpClient.newBuilder().sslContext(jaxRsClient.getSslContext()).build()));
        configuration.register(MultiPartFeature.class);
        final Client client = ClientBuilder.newClient(configuration);
        final WebTarget target = client.target("https://postman-echo.com/post");

        final Path file = Files.createTempFile("shouldWorkWithJaxRsClientWithStreamPost", ".json");
        Files.write(file, JSON.getBytes(StandardCharsets.UTF_8));

        MultiPart multiPart = new MultiPart();
        multiPart.setMediaType(MediaType.MULTIPART_FORM_DATA_TYPE);

        FileDataBodyPart fileDataBodyPart = new FileDataBodyPart("file",
                file.toFile(),
                MediaType.APPLICATION_OCTET_STREAM_TYPE);
        multiPart.bodyPart(fileDataBodyPart);

        Response response = target.request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(multiPart, multiPart.getMediaType()));
        assertEquals(500, response.getStatus());
        assertNotNull(response.readEntity(String.class));
    }

    private static class NoOpCallback implements InvocationCallback<Response> {
        @Override
        public void completed(Response o) {
            // Completing the response
        }

        @Override
        public void failed(Throwable throwable) {
            // Failed to response

        }
    }
}