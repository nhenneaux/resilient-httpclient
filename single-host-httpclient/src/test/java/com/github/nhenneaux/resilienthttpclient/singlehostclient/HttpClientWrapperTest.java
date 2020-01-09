package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HttpClientWrapperTest {

    static {
        // Force init of the client without hostname check, otherwise it is cached
        SingleHostHttpClientBuilder.builder("test").withTlsNameMatching().build();
    }

    @Test
    void send() throws IOException, InterruptedException {
        final HttpClient httpClient = mock(HttpClient.class);
        final String hostname = UUID.randomUUID().toString();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostname);
        final HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpClientWrapperTest.junit")).build();
        final HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();
        httpClientWrapper.send(httpRequest, bodyHandler);
        verify(httpClient).send(new HttpRequestWithHostHeader(httpRequest, hostname), bodyHandler);

    }

    @Test
    void sendAsync() {
        final HttpClient httpClient = mock(HttpClient.class);
        final String hostname = UUID.randomUUID().toString();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostname);
        final HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpClientWrapperTest.junit")).build();
        final HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();
        httpClientWrapper.sendAsync(httpRequest, bodyHandler);
        verify(httpClient).sendAsync(new HttpRequestWithHostHeader(httpRequest, hostname), bodyHandler);
    }

    @Test
    void testSendAsync() {
        final HttpClient httpClient = mock(HttpClient.class);
        final String hostname = UUID.randomUUID().toString();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostname);
        final HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpClientWrapperTest.junit")).build();
        final HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();
        final HttpResponse.PushPromiseHandler<Void> pushPromiseHandler = HttpResponse.PushPromiseHandler.of(request -> bodyHandler, new ConcurrentHashMap<>());
        httpClientWrapper.sendAsync(httpRequest, bodyHandler, pushPromiseHandler);
        verify(httpClient).sendAsync(new HttpRequestWithHostHeader(httpRequest, hostname), bodyHandler, pushPromiseHandler);
    }


    @Test
    void cookieHandler() {
        final HttpClient httpClient = mock(HttpClient.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, UUID.randomUUID().toString());
        httpClientWrapper.cookieHandler();
        verify(httpClient).cookieHandler();
    }

    @Test
    void connectTimeout() {
        final HttpClient httpClient = mock(HttpClient.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, UUID.randomUUID().toString());
        httpClientWrapper.connectTimeout();
        verify(httpClient).connectTimeout();
    }

    @Test
    void followRedirects() {
        final HttpClient httpClient = mock(HttpClient.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, UUID.randomUUID().toString());
        httpClientWrapper.followRedirects();
        verify(httpClient).followRedirects();
    }

    @Test
    void proxy() {
        final HttpClient httpClient = mock(HttpClient.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, UUID.randomUUID().toString());
        httpClientWrapper.proxy();
        verify(httpClient).proxy();
    }

    @Test
    void sslContext() {
        final HttpClient httpClient = mock(HttpClient.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, UUID.randomUUID().toString());
        httpClientWrapper.sslContext();
        verify(httpClient).sslContext();
    }

    @Test
    void sslParameters() {
        final HttpClient httpClient = mock(HttpClient.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, UUID.randomUUID().toString());
        httpClientWrapper.sslParameters();
        verify(httpClient).sslParameters();
    }

    @Test
    void authenticator() {
        final HttpClient httpClient = mock(HttpClient.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, UUID.randomUUID().toString());
        httpClientWrapper.authenticator();
        verify(httpClient).authenticator();
    }

    @Test
    void version() {
        final HttpClient httpClient = mock(HttpClient.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, UUID.randomUUID().toString());
        httpClientWrapper.version();
        verify(httpClient).version();
    }

    @Test
    void executor() {
        final HttpClient httpClient = mock(HttpClient.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, UUID.randomUUID().toString());
        httpClientWrapper.executor();
        verify(httpClient).executor();
    }


    @Test
    void newWebSocketBuilder() {
        final HttpClient httpClient = mock(HttpClient.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, UUID.randomUUID().toString());
        httpClientWrapper.newWebSocketBuilder();
        verify(httpClient).newWebSocketBuilder();
    }
}