package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Logger;

class ResilientClient extends HttpClient {

    static final int MAX_RETRY = 3;
    private static final Logger LOGGER = Logger.getLogger(ResilientClient.class.getName());
    private final Supplier<SingleIpHttpClient> httpClient;

    ResilientClient(Supplier<SingleIpHttpClient> httpClient) {
        this.httpClient = httpClient;
    }


    @Override
    public Optional<CookieHandler> cookieHandler() {
        return httpClient().cookieHandler();
    }

    private HttpClient httpClient() {
        return httpClient.get().getHttpClient();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return httpClient().connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return httpClient().followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return httpClient().proxy();
    }

    @Override
    public SSLContext sslContext() {
        return httpClient().sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return httpClient().sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return httpClient().authenticator();
    }

    @Override
    public Version version() {
        return httpClient().version();
    }

    @Override
    public Optional<Executor> executor() {
        return httpClient().executor();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        int i = 0;
        final ArrayList<InetAddress> tried = new ArrayList<>();
        do {
            final SingleIpHttpClient singleIpHttpClient = httpClient.get();
            try {
                return singleIpHttpClient.getHttpClient().send(request, responseBodyHandler);
            } catch (HttpConnectTimeoutException e) {
                LOGGER.warning(() -> "Got a connect timeout when trying to connect to " + singleIpHttpClient.getInetAddress() + ", already tried " + tried);
                tried.add(singleIpHttpClient.getInetAddress());
            }
            i++;
        } while (i < MAX_RETRY);
        throw new HttpConnectTimeoutException("Cannot connect to the HTTP server, tried to connect to the following IP " + tried + " to send the HTTP request " + request);

    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        // TODO handle connect timeout
        return httpClient().sendAsync(request, responseBodyHandler);

    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return httpClient().sendAsync(request, responseBodyHandler, pushPromiseHandler);
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
        return httpClient().newWebSocketBuilder();
    }
}
