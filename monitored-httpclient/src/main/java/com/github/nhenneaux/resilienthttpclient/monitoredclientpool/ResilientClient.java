package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.ConnectException;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

class ResilientClient extends HttpClient {

    private static final Logger LOGGER = Logger.getLogger(ResilientClient.class.getName());
    private final Supplier<RoundRobinPool> httpClient;

    ResilientClient(Supplier<RoundRobinPool> httpClient) {
        this.httpClient = httpClient;
    }


    @Override
    public Optional<CookieHandler> cookieHandler() {
        return httpClient().cookieHandler();
    }

    private HttpClient httpClient() {
        return httpClient.get().next().orElseThrow().getHttpClient();
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
        final ArrayList<InetAddress> tried = new ArrayList<>();
        for (SingleIpHttpClient singleIpHttpClient : httpClient.get().getList()) {
            try {
                return singleIpHttpClient.getHttpClient().send(request, responseBodyHandler);
            } catch (HttpConnectTimeoutException e) {
                LOGGER.warning(() -> "Got a connect timeout when trying to connect to " + singleIpHttpClient.getInetAddress() + ", already tried " + tried);
                tried.add(singleIpHttpClient.getInetAddress());
            }
        }
        throw new HttpConnectTimeoutException("Cannot connect to the HTTP server, tried to connect to the following IP " + tried + " to send the HTTP request " + request);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        return handleConnectTimeout(httpclient -> httpclient.sendAsync(request, responseBodyHandler), httpClient.get().getList());

    }

    private <T> CompletableFuture<HttpResponse<T>> handleConnectTimeout(Function<HttpClient, CompletableFuture<HttpResponse<T>>> send, List<SingleIpHttpClient> list) {
        return send.apply(list.iterator().next().getHttpClient())
                .exceptionally(throwable -> {
                    if (throwable.getCause() instanceof HttpConnectTimeoutException || throwable.getCause() instanceof ConnectException) {
                        if (list.size() == 1) {
                            throw new IllegalStateException(throwable.getCause());
                        }
                        return handleConnectTimeout(send, list.subList(1, list.size())).join();
                    }
                    if (throwable instanceof Error) {
                        throw (Error) throwable;
                    }
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new IllegalStateException(throwable);
                });
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return handleConnectTimeout(httpclient -> httpclient.sendAsync(request, responseBodyHandler, pushPromiseHandler), httpClient.get().getList());
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
        return httpClient().newWebSocketBuilder();
    }
}
