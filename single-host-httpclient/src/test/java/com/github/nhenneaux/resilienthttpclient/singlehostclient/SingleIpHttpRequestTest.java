package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SingleIpHttpRequestTest {

    @Test @Timeout(61)
    void bodyPublisher() {
        final InetAddress hostAddress = getAddress();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress);
        assertSame(request.bodyPublisher(), singleIpHttpRequest.bodyPublisher());
    }

    private static InetAddress getAddress() {
        return Inet4Address.getLoopbackAddress();
    }

    @Test @Timeout(61)
    void method() {
        final InetAddress hostAddress = getAddress();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress);
        assertSame(request.method(), singleIpHttpRequest.method());
    }

    @Test @Timeout(61)
    void timeout() {
        final InetAddress hostAddress = getAddress();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress);
        assertSame(request.timeout(), singleIpHttpRequest.timeout());
    }

    @Test @Timeout(61)
    void expectContinue() {
        final InetAddress hostAddress = getAddress();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress);
        assertSame(request.expectContinue(), singleIpHttpRequest.expectContinue());
    }

    @Test @Timeout(61)
    void uri() throws URISyntaxException, UnknownHostException {
        final String hostname = UUID.randomUUID().toString();
        final InetAddress hostAddress = InetAddress.getByAddress(hostname, new byte[]{10, 1, 1, 1});
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress);
        assertEquals(new URI("https://" + hostAddress.getHostAddress()), singleIpHttpRequest.uri());
    }

    @Test @Timeout(61)
    void uriInvalidUrl() {
        final var uri = URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit");
        final HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, null);
        final IllegalStateException illegalStateException = assertThrows(IllegalStateException.class, () -> SingleIpHttpRequest.newUriWithAddress(uri, null));
        assertEquals(URISyntaxException.class, illegalStateException.getCause().getClass());
        assertEquals("Cannot build uri https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junitwith address null", illegalStateException.getMessage());
    }

    @Test @Timeout(61)
    void version() {
        final InetAddress hostAddress = getAddress();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress);
        assertSame(request.version(), singleIpHttpRequest.version());
    }

    @Test @Timeout(61)
    void headers() {
        final String hostname = UUID.randomUUID().toString();
        final InetAddress hostAddress = getAddress();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress, hostname);
        final Map<String, List<String>> headers = singleIpHttpRequest.headers().map();
        assertEquals(1, headers.size());
        assertEquals(List.of(hostname), headers.get("host"));
    }

    @Test @Timeout(61)
    void properToString() throws UnknownHostException {
        final String hostname = UUID.randomUUID().toString();
        final InetAddress hostAddress = InetAddress.getByName("localhost");
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.properToString.junit")).build();
        final SingleIpHttpRequest singleIpHttpRequest = new SingleIpHttpRequest(request, hostAddress, hostname);
        assertEquals("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.properToString.junit https://127.0.0.1 GET", singleIpHttpRequest.toString());
    }
}