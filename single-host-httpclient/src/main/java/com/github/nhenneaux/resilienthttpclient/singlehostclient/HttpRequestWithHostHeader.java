package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class HttpRequestWithHostHeader extends HttpRequest {

    static final String HOST_HEADER = "host";

    private final HttpRequest httpRequest;
    private final HttpHeaders headers;

    HttpRequestWithHostHeader(HttpRequest httpRequest, String hostname) {
        this.httpRequest = httpRequest;
        final Map<String, List<String>> headerMap = new HashMap<>(httpRequest.headers().map());
        headerMap.put(HOST_HEADER, List.of(hostname));
        this.headers = HttpHeaders.of(headerMap, (s, s2) -> true);

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
    public Optional<HttpClient.Version> version() {
        return httpRequest.version();
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }


}
