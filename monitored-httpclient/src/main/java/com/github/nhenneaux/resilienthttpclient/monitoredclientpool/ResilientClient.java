package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.lang.System.Logger;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level;

class ResilientClient extends HttpClient {

    private static final Logger LOGGER = System.getLogger(ResilientClient.class.getName());
    private static final Set<Class<?>> CONNECT_EXCEPTION_CLASS = Set.of(HttpConnectTimeoutException.class, ConnectException.class);
    private final Supplier<RoundRobinPool> roundRobinPoolSupplier;

    ResilientClient(Supplier<RoundRobinPool> roundRobinPoolSupplier) {
        this.roundRobinPoolSupplier = roundRobinPoolSupplier;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return httpClient().cookieHandler();
    }

    private static SingleIpHttpClient singleIpHttpClient(RoundRobinPool roundRobinPool) {
        return roundRobinPool.next().orElseThrow(() -> new IllegalStateException("There is no healthy connection to send the request"));
    }

    static <T> CompletableFuture<HttpResponse<T>> handleConnectTimeout(Function<HttpClient, CompletableFuture<HttpResponse<T>>> send, RoundRobinPool roundRobinPool) {
        final SingleIpHttpClient firstClient = singleIpHttpClient(roundRobinPool);
        return handleConnectTimeout(send, roundRobinPool, firstClient, new ArrayList<>());

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

    @SuppressWarnings("squid:S3864")// Usage of peek method is correct here
    private static <T> CompletableFuture<HttpResponse<T>> handleConnectTimeout(
            Function<HttpClient, CompletableFuture<HttpResponse<T>>> send,
            RoundRobinPool roundRobinPool,
            SingleIpHttpClient firstClient,
            List<InetAddress> triedAddress
    ) {
        final long healthyNodes = roundRobinPool.getList().stream().filter(SingleIpHttpClient::isHealthy).count();
        if (triedAddress.size() >= healthyNodes) {
            final CompletableFuture<HttpResponse<T>> httpResponseCompletableFuture = new CompletableFuture<>();
            httpResponseCompletableFuture.completeExceptionally(new HttpConnectTimeoutException("Cannot connect to the server, the following address were tried without success " + triedAddress + "."));
            return httpResponseCompletableFuture;
        }

        return Optional.of(firstClient)
                .filter(ignored -> triedAddress.isEmpty())
                .or(roundRobinPool::next)
                .stream()
                .peek(singleIpHttpClient -> triedAddress.add(singleIpHttpClient.getInetAddress()))
                .map(singleIpHttpClient -> new ClientWithResponseFuture<>(singleIpHttpClient, send.apply(singleIpHttpClient.getHttpClient())))
                .map(clientWithResponseFuture -> addExceptionHandlerFuture(send, roundRobinPool, firstClient, triedAddress, clientWithResponseFuture))
                .map(ResilientClient::addCounterRefresherFuture)
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Cannot connect to the server, the following address were tried without success " + triedAddress + "."));
    }

    private static <T> ClientWithResponseFuture<T> addExceptionHandlerFuture(final Function<HttpClient, CompletableFuture<HttpResponse<T>>> send,
                                                                             final RoundRobinPool roundRobinPool,
                                                                             final SingleIpHttpClient firstClient,
                                                                             final List<InetAddress> triedAddress,
                                                                             final ClientWithResponseFuture<T> clientWithResponseFuture) {

        final CompletableFuture<HttpResponse<T>> httpResponseCompletableFuture = clientWithResponseFuture.httpResponseFuture
                .exceptionally(throwable -> {
                    if (Optional.ofNullable(throwable.getCause())
                            .map(Object::getClass)
                            .filter(CONNECT_EXCEPTION_CLASS::contains)
                            .isPresent()
                    ) {
                        return handleConnectTimeout(send, roundRobinPool, firstClient, triedAddress).join();
                    }

                    if (throwable instanceof Error) {
                        throw (Error) throwable;
                    }
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new IllegalStateException(throwable);
                });

        return clientWithResponseFuture.withResponseFuture(httpResponseCompletableFuture);
    }

    private static <T> CompletableFuture<HttpResponse<T>> addCounterRefresherFuture(final ClientWithResponseFuture<T> clientWithResponseFuture) {
        return clientWithResponseFuture.httpResponseFuture
                .whenComplete((httpResponse, throwable) -> {
                    if (throwable != null || httpResponse == null) {
                        clientWithResponseFuture.singleIpHttpClient.incrementFailureCount();
                    } else {
                        clientWithResponseFuture.singleIpHttpClient.refreshFailureCountWithStatusCode(httpResponse.statusCode());
                    }
                });
    }

    private HttpClient httpClient() {
        return singleIpHttpClient(roundRobinPoolSupplier.get()).getHttpClient();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        final RoundRobinPool roundRobinPool = roundRobinPoolSupplier.get();
        final SingleIpHttpClient firstClient = roundRobinPool.next().orElseThrow(() -> new IllegalStateException("There is no healthy connection to send the request in the pool " + roundRobinPool));
        final long healthyNodes = roundRobinPool.getList().stream().filter(SingleIpHttpClient::isHealthy).count();
        final List<InetAddress> tried = new ArrayList<>();


        SingleIpHttpClient client = firstClient;
        while (tried.size() < healthyNodes) {
            try {
                final HttpResponse<T> httpResponse = client.getHttpClient().send(request, responseBodyHandler);

                client.refreshFailureCountWithStatusCode(httpResponse.statusCode());
                return httpResponse;
            } catch (HttpConnectTimeoutException | ConnectException e) {
                client.incrementFailureCount();

                var finalClient = client;
                LOGGER.log(Level.WARNING, () -> "Got a connect timeout when trying to connect to " + finalClient.getInetAddress() + ", already tried " + tried);
                tried.add(finalClient.getInetAddress());
                final Optional<SingleIpHttpClient> nextClient = roundRobinPool.next();
                if (nextClient.isEmpty()) {
                    final HttpConnectTimeoutException httpConnectTimeoutException = new HttpConnectTimeoutException("Cannot connect to the HTTP server, tried to connect to the following IP " + tried + " to send the HTTP request " + request);
                    httpConnectTimeoutException.initCause(e);
                    throw httpConnectTimeoutException;
                }
                client = nextClient.get();
            }
        }
        throw new HttpConnectTimeoutException("Cannot connect to the HTTP server, tried to connect to the following IP " + tried + " to send the HTTP request " + request);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        return handleConnectTimeout(httpclient -> httpclient.sendAsync(request, responseBodyHandler), roundRobinPoolSupplier.get());

    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return handleConnectTimeout(httpclient -> httpclient.sendAsync(request, responseBodyHandler, pushPromiseHandler), roundRobinPoolSupplier.get());
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
        return httpClient().newWebSocketBuilder();
    }

    private static class ClientWithResponseFuture<T> {

        private final SingleIpHttpClient singleIpHttpClient;
        private final CompletableFuture<HttpResponse<T>> httpResponseFuture;

        ClientWithResponseFuture(final SingleIpHttpClient singleIpHttpClient, final CompletableFuture<HttpResponse<T>> httpResponseFuture) {
            this.singleIpHttpClient = singleIpHttpClient;
            this.httpResponseFuture = httpResponseFuture;
        }

        ClientWithResponseFuture<T> withResponseFuture(final CompletableFuture<HttpResponse<T>> httpResponseFuture) {
            return new ClientWithResponseFuture<>(this.singleIpHttpClient, httpResponseFuture);
        }
    }
}
