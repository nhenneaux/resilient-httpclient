package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SingleIpHttpRequestTest {

    @Test
    void bodyPublisher() {
        final String hostname = UUID.randomUUID().toString();
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress, hostname);
        assertSame(request.bodyPublisher(), singleIpHttpRequest.bodyPublisher());
    }

    @Test
    void method() {
        final String hostname = UUID.randomUUID().toString();
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress, hostname);
        assertSame(request.method(), singleIpHttpRequest.method());
    }

    @Test
    void timeout() {
        final String hostname = UUID.randomUUID().toString();
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress, hostname);
        assertSame(request.timeout(), singleIpHttpRequest.timeout());
    }

    @Test
    void expectContinue() {
        final String hostname = UUID.randomUUID().toString();
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress, hostname);
        assertSame(request.expectContinue(), singleIpHttpRequest.expectContinue());
    }

    @Test
    void uri() throws URISyntaxException {
        final String hostname = UUID.randomUUID().toString();
        final InetAddress hostAddress = mock(InetAddress.class);
        when(hostAddress.getHostAddress()).thenReturn("10.1.1.1");
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress, hostname);
        assertEquals(new URI("https://" + hostAddress.getHostAddress()), singleIpHttpRequest.uri());
    }

    @Test
    void version() {
        final String hostname = UUID.randomUUID().toString();
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress, hostname);
        assertSame(request.version(), singleIpHttpRequest.version());
    }

    @Test
    void headers() {
        final String hostname = UUID.randomUUID().toString();
        final InetAddress hostAddress = mock(InetAddress.class);
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress, hostname);
        final Map<String, List<String>> headers = singleIpHttpRequest.headers().map();
        assertEquals(1, headers.size());
        assertEquals(List.of(hostname), headers.get("host"));
    }
}