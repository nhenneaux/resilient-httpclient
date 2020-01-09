package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        return httpClient.send(new HttpRequestWrapper(request, hostname), responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        return httpClient.sendAsync(new HttpRequestWrapper(request, hostname), responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return httpClient.sendAsync(new HttpRequestWrapper(request, hostname), responseBodyHandler, pushPromiseHandler);
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
        return httpClient.newWebSocketBuilder();
    }

    private static class HttpRequestWrapper extends HttpRequest {
        private final HttpRequest httpRequest;
        private final HttpHeaders headers;

        private HttpRequestWrapper(HttpRequest httpRequest, String hostname) {
            this.httpRequest = httpRequest;
            final Map<String, List<String>> map = new HashMap<>(httpRequest.headers().map());
            map.put("host ", List.of(hostname));
            this.headers = HttpHeaders.of(map, (s, s2) -> true);

        }

        @Override
        public Optional<BodyPublisher> bodyPublisher() {
            return httpRequest.bodyPublisher();
        }

        @Override
        public String method() {
            return httpRequest.method();
        }

        @Override
        public Optional<Duration> timeout() {
            return httpRequest.timeout();
        }

        @Override
        public boolean expectContinue() {
            return httpRequest.expectContinue();
        }

        @Override
        public URI uri() {
            return httpRequest.uri();
        }

        @Override
        public Optional<Version> version() {
            return httpRequest.version();
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }


    }


}
