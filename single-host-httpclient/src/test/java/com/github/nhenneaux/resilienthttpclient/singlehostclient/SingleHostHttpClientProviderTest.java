package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SingleHostHttpClientProviderTest {

    @Test
    void shouldBuildSingleIpHttpClientAndWorksWithPublicWebsite() {
        // Given
        final List<String> hosts = List.of("openjdk.java.net", "travis-ci.com", "github.com", "facebook.com");
        for (String hostname : hosts) {
            final String ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next().getHostAddress();

            final HttpClient client = new SingleHostHttpClientProvider().buildSingleHostnameHttpClient(hostname);


            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + ip))
                    .build();


            // When
            final String response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .join();

            // Then
            assertNotNull(response);
        }


    }

    @Test
    void unknownHost() {
        // Given
        final String hostname = "notfound.unit";
        final DnsLookupWrapper dnsLookupWrapper = new DnsLookupWrapper();
        // When
        final IllegalStateException illegalStateException = assertThrows(IllegalStateException.class, () -> dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname));

        // Then
        assertEquals("Cannot perform a DNS lookup for the hostname: notfound.unit.", illegalStateException.getMessage());
        assertEquals(UnknownHostException.class, illegalStateException.getCause().getClass());
    }

    @Test
    void unreachableAddress() {
        final HttpClient client = new SingleHostHttpClientProvider().buildSingleHostnameHttpClient("no.http.server", null, HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://10.2.3.4"))
                .build();


        // When
        final CompletableFuture<String> stringCompletableFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);


        // Then
        final ExecutionException executionException = assertThrows(ExecutionException.class, stringCompletableFuture::get);
        assertEquals("java.net.http.HttpConnectTimeoutException: HTTP connect timed out", executionException.getMessage());
    }

    @Test
    void noHttpServer() {
        final HttpClient client = new SingleHostHttpClientProvider().buildSingleHostnameHttpClient("no.http.server", null, HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://1.1.1.1"))
                .build();


        // When
        final CompletableFuture<String> stringCompletableFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);


        // Then
        final ExecutionException executionException = assertThrows(ExecutionException.class, stringCompletableFuture::get);
        assertEquals("java.net.http.HttpConnectTimeoutException: HTTP connect timed out", executionException.getMessage());
    }
}