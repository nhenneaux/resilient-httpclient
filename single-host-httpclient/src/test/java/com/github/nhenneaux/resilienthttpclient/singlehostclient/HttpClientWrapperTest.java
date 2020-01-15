package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpClientWrapperTest {
    static {
        // Force properties
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", SingleIpHttpRequest.HOST_HEADER);
    }

    @Test
    void send() throws IOException, InterruptedException {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = mock(InetAddress.class);
        when(hostAddress.getHostAddress()).thenReturn("10.1.1.1");
        final String hostname = UUID.randomUUID().toString();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostAddress, hostname);
        final HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpClientWrapperTest.junit")).build();
        final HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();
        httpClientWrapper.send(httpRequest, bodyHandler);
        verify(httpClient).send(new SingleIpHttpRequest(httpRequest, hostAddress, hostname), bodyHandler);

    }

    @Test
    void sendAsync() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = mock(InetAddress.class);
        when(hostAddress.getHostAddress()).thenReturn("10.1.1.1");
        final String hostname = UUID.randomUUID().toString();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostAddress, hostname);
        final HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpClientWrapperTest.junit")).build();
        final HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();
        httpClientWrapper.sendAsync(httpRequest, bodyHandler);
        verify(httpClient).sendAsync(new SingleIpHttpRequest(httpRequest, hostAddress, hostname), bodyHandler);
    }

    @Test
    void testSendAsync() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = mock(InetAddress.class);
        when(hostAddress.getHostAddress()).thenReturn("10.1.1.1");
        final String hostname = UUID.randomUUID().toString();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostAddress, hostname);
        final HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpClientWrapperTest.junit")).build();
        final HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();
        final HttpResponse.PushPromiseHandler<Void> pushPromiseHandler = HttpResponse.PushPromiseHandler.of(request -> bodyHandler, new ConcurrentHashMap<>());
        httpClientWrapper.sendAsync(httpRequest, bodyHandler, pushPromiseHandler);
        verify(httpClient).sendAsync(new SingleIpHttpRequest(httpRequest, hostAddress, hostname), bodyHandler, pushPromiseHandler);
    }


    @Test
    void cookieHandler() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostAddress, UUID.randomUUID().toString());
        httpClientWrapper.cookieHandler();
        verify(httpClient).cookieHandler();
    }

    @Test
    void connectTimeout() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostAddress, UUID.randomUUID().toString());
        httpClientWrapper.connectTimeout();
        verify(httpClient).connectTimeout();
    }

    @Test
    void followRedirects() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostAddress, UUID.randomUUID().toString());
        httpClientWrapper.followRedirects();
        verify(httpClient).followRedirects();
    }

    @Test
    void proxy() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostAddress, UUID.randomUUID().toString());
        httpClientWrapper.proxy();
        verify(httpClient).proxy();
    }

    @Test
    void sslContext() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostAddress, UUID.randomUUID().toString());
        httpClientWrapper.sslContext();
        verify(httpClient).sslContext();
    }

    @Test
    void sslParameters() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostAddress, UUID.randomUUID().toString());
        httpClientWrapper.sslParameters();
        verify(httpClient).sslParameters();
    }

    @Test
    void authenticator() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostAddress, UUID.randomUUID().toString());
        httpClientWrapper.authenticator();
        verify(httpClient).authenticator();
    }

    @Test
    void version() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostAddress, UUID.randomUUID().toString());
        httpClientWrapper.version();
        verify(httpClient).version();
    }

    @Test
    void executor() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostAddress, UUID.randomUUID().toString());
        httpClientWrapper.executor();
        verify(httpClient).executor();
    }


    @Test
    void newWebSocketBuilder() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, hostAddress, UUID.randomUUID().toString());
        httpClientWrapper.newWebSocketBuilder();
        verify(httpClient).newWebSocketBuilder();
    }
}