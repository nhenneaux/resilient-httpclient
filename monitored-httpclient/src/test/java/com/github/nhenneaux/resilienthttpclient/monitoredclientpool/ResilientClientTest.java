package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResilientClientTest {

    @Test
    void send() {
        final HttpClient httpClient = mock(HttpClient.class);
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);

        InetAddress hostAddress = mock(InetAddress.class);
        when(hostAddress.getHostAddress()).thenReturn("10.1.1.1");
        final Optional<SingleIpHttpClient> singleIpHttpClient = Optional.of(new SingleIpHttpClient(httpClient, hostAddress, new ServerConfiguration(UUID.randomUUID().toString())));
        when(roundRobinPool.next()).thenReturn(singleIpHttpClient);

        final ResilientClient ResilientClient = new ResilientClient(() -> roundRobinPool);
        final HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.ResilientClientTest.junit")).build();
        final HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();
        final HttpConnectTimeoutException httpConnectTimeoutException = assertThrows(HttpConnectTimeoutException.class, () -> ResilientClient.send(httpRequest, bodyHandler));
        assertEquals("Cannot connect to the HTTP server, tried to connect to the following IP [] to send the HTTP request https://com.github.nhenneaux.resilienthttpclient.singlehostclient.ResilientClientTest.junit GET", httpConnectTimeoutException.getMessage());

    }

    @Test
    void sendAsync() {
        final HttpClient httpClient = mock(HttpClient.class);
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final String hostname = UUID.randomUUID().toString();
        InetAddress hostAddress = mock(InetAddress.class);
        final Optional<SingleIpHttpClient> singleIpHttpClient = Optional.of(new SingleIpHttpClient(httpClient, hostAddress, new ServerConfiguration(hostname)));
        when(roundRobinPool.next()).thenReturn(singleIpHttpClient);

        when(hostAddress.getHostAddress()).thenReturn("10.1.1.1");
        final ResilientClient ResilientClient = new ResilientClient(() -> roundRobinPool);
        final HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.ResilientClientTest.junit")).build();
        final HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();
        ResilientClient.sendAsync(httpRequest, bodyHandler);
        verify(httpClient).sendAsync(httpRequest, bodyHandler);
    }

    @Test
    void testSendAsync() {
        final HttpClient httpClient = mock(HttpClient.class);
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        InetAddress hostAddress = mock(InetAddress.class);
        when(hostAddress.getHostAddress()).thenReturn("10.1.1.1");
        final Optional<SingleIpHttpClient> singleIpHttpClient = Optional.of(new SingleIpHttpClient(httpClient, inetAddress(), new ServerConfiguration(UUID.randomUUID().toString())));
        when(roundRobinPool.next()).thenReturn(singleIpHttpClient);

        final ResilientClient ResilientClient = new ResilientClient(() -> roundRobinPool);
        final HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.ResilientClientTest.junit")).build();
        final HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();
        final HttpResponse.PushPromiseHandler<Void> pushPromiseHandler = HttpResponse.PushPromiseHandler.of(request -> bodyHandler, new ConcurrentHashMap<>());
        ResilientClient.sendAsync(httpRequest, bodyHandler, pushPromiseHandler);
        verify(httpClient).sendAsync(httpRequest, bodyHandler, pushPromiseHandler);
    }


    @Test
    void cookieHandler() {
        final HttpClient httpClient = mock(HttpClient.class);
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Optional<SingleIpHttpClient> singleIpHttpClient = Optional.of(new SingleIpHttpClient(httpClient, inetAddress(), new ServerConfiguration(UUID.randomUUID().toString())));
        when(roundRobinPool.next()).thenReturn(singleIpHttpClient);
        final ResilientClient ResilientClient = new ResilientClient(() -> roundRobinPool);
        ResilientClient.cookieHandler();
        verify(httpClient).cookieHandler();
    }

    private InetAddress inetAddress() {
        final InetAddress inetAddress = mock(InetAddress.class);
        when(inetAddress.getHostAddress()).thenReturn("10.255.1.1");
        return inetAddress;
    }

    @Test
    void connectTimeout() {
        final HttpClient httpClient = mock(HttpClient.class);
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Optional<SingleIpHttpClient> singleIpHttpClient = Optional.of(new SingleIpHttpClient(httpClient, inetAddress(), new ServerConfiguration(UUID.randomUUID().toString())));
        when(roundRobinPool.next()).thenReturn(singleIpHttpClient);
        final ResilientClient ResilientClient = new ResilientClient(() -> roundRobinPool);
        ResilientClient.connectTimeout();
        verify(httpClient).connectTimeout();
    }

    @Test
    void followRedirects() {
        final HttpClient httpClient = mock(HttpClient.class);
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Optional<SingleIpHttpClient> singleIpHttpClient = Optional.of(new SingleIpHttpClient(httpClient, inetAddress(), new ServerConfiguration(UUID.randomUUID().toString())));
        when(roundRobinPool.next()).thenReturn(singleIpHttpClient);
        final ResilientClient ResilientClient = new ResilientClient(() -> roundRobinPool);
        ResilientClient.followRedirects();
        verify(httpClient).followRedirects();
    }

    @Test
    void proxy() {
        final HttpClient httpClient = mock(HttpClient.class);
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Optional<SingleIpHttpClient> singleIpHttpClient = Optional.of(new SingleIpHttpClient(httpClient, inetAddress(), new ServerConfiguration(UUID.randomUUID().toString())));
        when(roundRobinPool.next()).thenReturn(singleIpHttpClient);

        final ResilientClient ResilientClient = new ResilientClient(() -> roundRobinPool);
        ResilientClient.proxy();
        verify(httpClient).proxy();
    }

    @Test
    void sslContext() {
        final HttpClient httpClient = mock(HttpClient.class);
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Optional<SingleIpHttpClient> singleIpHttpClient = Optional.of(new SingleIpHttpClient(httpClient, inetAddress(), new ServerConfiguration(UUID.randomUUID().toString())));
        when(roundRobinPool.next()).thenReturn(singleIpHttpClient);

        final ResilientClient ResilientClient = new ResilientClient(() -> roundRobinPool);
        ResilientClient.sslContext();
        verify(httpClient).sslContext();
    }

    @Test
    void sslParameters() {
        final HttpClient httpClient = mock(HttpClient.class);
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Optional<SingleIpHttpClient> singleIpHttpClient = Optional.of(new SingleIpHttpClient(httpClient, inetAddress(), new ServerConfiguration(UUID.randomUUID().toString())));
        when(roundRobinPool.next()).thenReturn(singleIpHttpClient);

        final ResilientClient ResilientClient = new ResilientClient(() -> roundRobinPool);
        ResilientClient.sslParameters();
        verify(httpClient).sslParameters();
    }

    @Test
    void authenticator() {
        final HttpClient httpClient = mock(HttpClient.class);
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Optional<SingleIpHttpClient> singleIpHttpClient = Optional.of(new SingleIpHttpClient(httpClient, inetAddress(), new ServerConfiguration(UUID.randomUUID().toString())));
        when(roundRobinPool.next()).thenReturn(singleIpHttpClient);

        final ResilientClient ResilientClient = new ResilientClient(() -> roundRobinPool);
        ResilientClient.authenticator();
        verify(httpClient).authenticator();
    }

    @Test
    void version() {
        final HttpClient httpClient = mock(HttpClient.class);
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Optional<SingleIpHttpClient> singleIpHttpClient = Optional.of(new SingleIpHttpClient(httpClient, inetAddress(), new ServerConfiguration(UUID.randomUUID().toString())));
        when(roundRobinPool.next()).thenReturn(singleIpHttpClient);

        final ResilientClient ResilientClient = new ResilientClient(() -> roundRobinPool);
        ResilientClient.version();
        verify(httpClient).version();
    }

    @Test
    void executor() {
        final HttpClient httpClient = mock(HttpClient.class);
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Optional<SingleIpHttpClient> singleIpHttpClient = Optional.of(new SingleIpHttpClient(httpClient, inetAddress(), new ServerConfiguration(UUID.randomUUID().toString())));
        when(roundRobinPool.next()).thenReturn(singleIpHttpClient);

        final ResilientClient ResilientClient = new ResilientClient(() -> roundRobinPool);
        ResilientClient.executor();
        verify(httpClient).executor();
    }


    @Test
    void newWebSocketBuilder() {
        final HttpClient httpClient = mock(HttpClient.class);
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Optional<SingleIpHttpClient> singleIpHttpClient = Optional.of(new SingleIpHttpClient(httpClient, inetAddress(), new ServerConfiguration(UUID.randomUUID().toString())));
        when(roundRobinPool.next()).thenReturn(singleIpHttpClient);

        final ResilientClient ResilientClient = new ResilientClient(() -> roundRobinPool);
        ResilientClient.newWebSocketBuilder();
        verify(httpClient).newWebSocketBuilder();
    }
}