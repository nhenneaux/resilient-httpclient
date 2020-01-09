package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

class HttpClientWrapper extends HttpClient {
    private final HttpClient httpClient;
    private final String hostname;

    HttpClientWrapper(HttpClient httpClient, String hostname) {
        this.httpClient = httpClient;
        this.hostname = hostname;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return httpClient.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return httpClient.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return httpClient.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return httpClient.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return httpClient.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return httpClient.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return httpClient.authenticator();
    }

    @Override
    public Version version() {
        return httpClient.version();
    }

    @Override
    public Optional<Executor> executor() {
        return httpClient.executor();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        return httpClient.send(new HttpRequestWithHostHeader(request, hostname), responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        return httpClient.sendAsync(new HttpRequestWithHostHeader(request, hostname), responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return httpClient.sendAsync(new HttpRequestWithHostHeader(request, hostname), responseBodyHandler, pushPromiseHandler);
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
        return httpClient.newWebSocketBuilder();
    }


}
