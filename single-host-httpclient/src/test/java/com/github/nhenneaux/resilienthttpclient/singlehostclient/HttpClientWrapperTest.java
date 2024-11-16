package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HttpClientWrapperTest {
    static {
        // Force properties
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", SingleIpHttpRequest.HOST_HEADER);
    }

    @Test @Timeout(61)
    void send() throws IOException, InterruptedException {
        final HttpClient httpClient = mock(HttpClient.class);

        final String hostname = UUID.randomUUID().toString();
        final InetAddress hostAddress = InetAddress.getByAddress(hostname, new byte[]{10, 1, 1, 1});
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, httpRequest -> new SingleIpHttpRequest(httpRequest, hostAddress, hostname));
        final HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpClientWrapperTest.junit")).build();
        final HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();
        httpClientWrapper.send(httpRequest, bodyHandler);
        verify(httpClient).send(new SingleIpHttpRequest(httpRequest, hostAddress, hostname), bodyHandler);

    }

    @Test @Timeout(61)
    void sendAsync() throws UnknownHostException {
        final HttpClient httpClient = mock(HttpClient.class);
        final String hostname = UUID.randomUUID().toString();
        final InetAddress hostAddress = InetAddress.getByAddress(hostname, new byte[]{10, 1, 1, 1});
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, httpRequest -> new SingleIpHttpRequest(httpRequest, hostAddress, hostname));
        final HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpClientWrapperTest.junit")).build();
        final HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();
        httpClientWrapper.sendAsync(httpRequest, bodyHandler);
        verify(httpClient).sendAsync(new SingleIpHttpRequest(httpRequest, hostAddress, hostname), bodyHandler);
    }

    @Test @Timeout(61)
    void testSendAsync() throws UnknownHostException {
        final HttpClient httpClient = mock(HttpClient.class);

        final String hostname = UUID.randomUUID().toString();

        final InetAddress hostAddress = InetAddress.getByAddress(hostname, new byte[]{10, 1, 1, 1});

        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, httpRequest -> new SingleIpHttpRequest(httpRequest, hostAddress, hostname));
        final HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpClientWrapperTest.junit")).build();
        final HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();
        final HttpResponse.PushPromiseHandler<Void> pushPromiseHandler = HttpResponse.PushPromiseHandler.of(request -> bodyHandler, new ConcurrentHashMap<>());
        httpClientWrapper.sendAsync(httpRequest, bodyHandler, pushPromiseHandler);
        verify(httpClient).sendAsync(new SingleIpHttpRequest(httpRequest, hostAddress, hostname), bodyHandler, pushPromiseHandler);
    }


    @Test @Timeout(61)
    void cookieHandler() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = getAddress();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, httpRequest -> new SingleIpHttpRequest(httpRequest, hostAddress, UUID.randomUUID().toString()));
        httpClientWrapper.cookieHandler();
        verify(httpClient).cookieHandler();
    }

    @Test @Timeout(61)
    void connectTimeout() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = getAddress();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, httpRequest -> new SingleIpHttpRequest(httpRequest, hostAddress, UUID.randomUUID().toString()));
        httpClientWrapper.connectTimeout();
        verify(httpClient).connectTimeout();
    }

    @Test @Timeout(61)
    void followRedirects() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = getAddress();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, httpRequest -> new SingleIpHttpRequest(httpRequest, hostAddress, UUID.randomUUID().toString()));
        httpClientWrapper.followRedirects();
        verify(httpClient).followRedirects();
    }

    private static InetAddress getAddress() {
        return InetAddress.getLoopbackAddress();
    }

    @Test @Timeout(61)
    void proxy() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = getAddress();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, httpRequest -> new SingleIpHttpRequest(httpRequest, hostAddress, UUID.randomUUID().toString()));
        httpClientWrapper.proxy();
        verify(httpClient).proxy();
    }

    @Test @Timeout(61)
    void sslContext() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = getAddress();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, httpRequest -> new SingleIpHttpRequest(httpRequest, hostAddress, UUID.randomUUID().toString()));
        httpClientWrapper.sslContext();
        verify(httpClient).sslContext();
    }

    @Test @Timeout(61)
    void sslParameters() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = getAddress();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, httpRequest -> new SingleIpHttpRequest(httpRequest, hostAddress, UUID.randomUUID().toString()));
        httpClientWrapper.sslParameters();
        verify(httpClient).sslParameters();
    }

    @Test @Timeout(61)
    void authenticator() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = getAddress();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, httpRequest -> new SingleIpHttpRequest(httpRequest, hostAddress, UUID.randomUUID().toString()));
        httpClientWrapper.authenticator();
        verify(httpClient).authenticator();
    }

    @Test @Timeout(61)
    void version() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = getAddress();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, httpRequest -> new SingleIpHttpRequest(httpRequest, hostAddress, UUID.randomUUID().toString()));
        httpClientWrapper.version();
        verify(httpClient).version();
    }

    @Test @Timeout(61)
    void executor() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = getAddress();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, httpRequest -> new SingleIpHttpRequest(httpRequest, hostAddress, UUID.randomUUID().toString()));
        httpClientWrapper.executor();
        verify(httpClient).executor();
    }


    @Test @Timeout(61)
    void newWebSocketBuilder() {
        final HttpClient httpClient = mock(HttpClient.class);
        final InetAddress hostAddress = getAddress();
        final HttpClientWrapper httpClientWrapper = new HttpClientWrapper(httpClient, httpRequest -> new SingleIpHttpRequest(httpRequest, hostAddress, UUID.randomUUID().toString()));
        httpClientWrapper.newWebSocketBuilder();
        verify(httpClient).newWebSocketBuilder();
    }
}