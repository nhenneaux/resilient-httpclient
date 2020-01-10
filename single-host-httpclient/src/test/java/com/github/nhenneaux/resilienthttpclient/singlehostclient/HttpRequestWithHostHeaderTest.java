package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class HttpRequestWithHostHeaderTest {

    @Test
    void bodyPublisher() {
        final String hostname = UUID.randomUUID().toString();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final HttpRequestWithHostHeader httpRequestWithHostHeader = new HttpRequestWithHostHeader(request, hostname);
        assertSame(request.bodyPublisher(), httpRequestWithHostHeader.bodyPublisher());
    }

    @Test
    void method() {
        final String hostname = UUID.randomUUID().toString();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final HttpRequestWithHostHeader httpRequestWithHostHeader = new HttpRequestWithHostHeader(request, hostname);
        assertSame(request.method(), httpRequestWithHostHeader.method());
    }

    @Test
    void timeout() {
        final String hostname = UUID.randomUUID().toString();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final HttpRequestWithHostHeader httpRequestWithHostHeader = new HttpRequestWithHostHeader(request, hostname);
        assertSame(request.timeout(), httpRequestWithHostHeader.timeout());
    }

    @Test
    void expectContinue() {
        final String hostname = UUID.randomUUID().toString();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final HttpRequestWithHostHeader httpRequestWithHostHeader = new HttpRequestWithHostHeader(request, hostname);
        assertSame(request.expectContinue(), httpRequestWithHostHeader.expectContinue());
    }

    @Test
    void uri() {
        final String hostname = UUID.randomUUID().toString();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final HttpRequestWithHostHeader httpRequestWithHostHeader = new HttpRequestWithHostHeader(request, hostname);
        assertSame(request.uri(), httpRequestWithHostHeader.uri());
    }

    @Test
    void version() {
        final String hostname = UUID.randomUUID().toString();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final HttpRequestWithHostHeader httpRequestWithHostHeader = new HttpRequestWithHostHeader(request, hostname);
        assertSame(request.version(), httpRequestWithHostHeader.version());
    }

    @Test
    void headers() {
        final String hostname = UUID.randomUUID().toString();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://com.github.nhenneaux.resilienthttpclient.singlehostclient.HttpRequestWithHostHeaderTest.junit")).build();
        final HttpRequestWithHostHeader httpRequestWithHostHeader = new HttpRequestWithHostHeader(request, hostname);
        final Map<String, List<String>> headers = httpRequestWithHostHeader.headers().map();
        assertEquals(1, headers.size());
        assertEquals(List.of(hostname), headers.get("host"));
    }
}