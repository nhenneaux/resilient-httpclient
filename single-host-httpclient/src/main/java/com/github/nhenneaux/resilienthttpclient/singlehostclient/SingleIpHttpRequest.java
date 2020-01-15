package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class SingleIpHttpRequest extends HttpRequest {

    static final String HOST_HEADER = "host";

    private final HttpRequest httpRequest;
    private final InetAddress hostAddress;
    private final HttpHeaders headers;

    SingleIpHttpRequest(HttpRequest httpRequest, InetAddress hostAddress, String hostname) {
        this.httpRequest = httpRequest;
        this.hostAddress = hostAddress;
        final Map<String, List<String>> headerMap = new HashMap<>(httpRequest.headers().map());
        headerMap.put(HOST_HEADER, List.of(hostname));
        this.headers = HttpHeaders.of(headerMap, (s, s2) -> true);
    }

    SingleIpHttpRequest(HttpRequest httpRequest, InetAddress hostAddress) {
        this.httpRequest = httpRequest;
        this.hostAddress = hostAddress;
        this.headers = httpRequest.headers();
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
        final URI uri = httpRequest.uri();
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), hostAddress.getHostAddress(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
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
