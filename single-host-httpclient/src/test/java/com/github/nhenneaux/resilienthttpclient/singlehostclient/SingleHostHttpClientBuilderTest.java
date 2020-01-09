package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SingleHostHttpClientBuilderTest {
    static {
        // Force init of the client without hostname check, otherwise it is cached
        SingleHostHttpClientBuilder.build("test");
    }

    @Test
    void shouldBuildSingleIpHttpClientAndWorksWithPublicWebsite() {
        // Given
        final List<String> hosts = List.of("openjdk.java.net", "github.com", "twitter.com", "cloudflare.com", "facebook.com", "amazon.com", "google.com", "travis-ci.com", "en.wikipedia.org");
        for (String hostname : hosts) {
            final String ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next().getHostAddress();

            final HttpClient client = SingleHostHttpClientBuilder.build(hostname);


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
    void shouldTestWithSni() {
        // Given
        // Domain is not working when sni is not working correctly
        final var hostname = "24max.de";
        final String ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next().getHostAddress();

        final HttpClient client = SingleHostHttpClientBuilder.build(hostname);


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

    @Test
    void shouldResetPropertyForHostnameVerification() {
        // Given
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());
        try {

            var hostname = "openjdk.java.net";
            final String ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next().getHostAddress();

            final HttpClient client = SingleHostHttpClientBuilder.build(hostname);


            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + ip))
                    .build();


            // When
            final String response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .join();

            // Then
            assertNotNull(response);
            assertEquals(Boolean.TRUE.toString(), System.getProperty("jdk.internal.httpclient.disableHostnameVerification"));
        } finally {

            System.clearProperty("jdk.internal.httpclient.disableHostnameVerification");
        }

    }

    @Test
    void shouldValidateWrongHost() {
        // Given
        String hostname = "wrong.host.badssl.com";
        final String ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next().getHostAddress();

        final HttpClient client = SingleHostHttpClientBuilder.build(hostname);


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + ip))
                .build();


        // When
        final CompletionException completionException = assertThrows(CompletionException.class, () -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .join());
        // Then
        assertEquals(SSLHandshakeException.class, completionException.getCause().getClass());
    }

    @Test
    void shouldValidateWith1000SAN() {
        // Given
        String hostname = "1000-sans.badssl.com";
        final String ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next().getHostAddress();

        final HttpClient client = SingleHostHttpClientBuilder.builder(hostname).withTlsNameMatching().buildWithHostHeader();


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + ip))
                .build();


        // When
        final CompletionException completionException = assertThrows(CompletionException.class, () -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .join());
        // Then
        assertEquals(SSLHandshakeException.class, completionException.getCause().getClass());
    }

    @Test
    void shouldValidateNoSubject() {
        // Given
        String hostname = "no-subject.badssl.com";
        final String ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next().getHostAddress();

        final HttpClient client = SingleHostHttpClientBuilder.builder(hostname).withTlsNameMatching().buildWithHostHeader();


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + ip))
                .build();


        // When
        final CompletionException completionException = assertThrows(CompletionException.class, () -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .join());
        // Then
        assertEquals(SSLHandshakeException.class, completionException.getCause().getClass());
    }

    @Test
    void shouldValidateNoCommonName() {
        // Given
        String hostname = "no-common-name.badssl.com";
        final String ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next().getHostAddress();

        final HttpClient client = SingleHostHttpClientBuilder.builder(hostname).withTlsNameMatching().buildWithHostHeader();


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + ip))
                .build();


        // When
        final CompletionException completionException = assertThrows(CompletionException.class, () -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .join());
        // Then
        assertEquals(SSLHandshakeException.class, completionException.getCause().getClass());
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
        final HttpClient client = SingleHostHttpClientBuilder.build("no.http.server", null, HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://10.2.3.4"))
                .build();


        // When
        final CompletableFuture<String> stringCompletableFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);


        // Then
        final ExecutionException executionException = assertThrows(ExecutionException.class, stringCompletableFuture::get);
        assertThat(executionException.getMessage(), Matchers.anyOf(Matchers.equalTo("java.net.http.HttpConnectTimeoutException: HTTP connect timed out"), Matchers.equalTo("java.net.ConnectException: Connection refused")));
    }

    @Test
    void noSubjectAlternativeName() {
        final HttpClient client = SingleHostHttpClientBuilder.build("no.http.server", null, HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1_000)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://1.1.1.1"))
                .build();


        // When
        final CompletableFuture<String> stringCompletableFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);


        // Then
        final ExecutionException executionException = assertThrows(ExecutionException.class, stringCompletableFuture::get);
        assertEquals("javax.net.ssl.SSLHandshakeException: No subject alternative DNS name matching no.http.server found.", executionException.getMessage());
    }

    @Test
    void noHttpsServer() {
        // Given
        String hostname = "http.badssl.com";
        final String ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next().getHostAddress();

        final HttpClient client = SingleHostHttpClientBuilder.builder(hostname).withTlsNameMatching().buildWithHostHeader();


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + ip))
                .build();

        // When
        final CompletableFuture<String> stringCompletableFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);


        // Then
        final ExecutionException executionException = assertThrows(ExecutionException.class, stringCompletableFuture::get);
        assertEquals("javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target", executionException.getMessage());
    }


    @Test
    void shouldHandleNoSuchAlgorithm() {
        final NoSuchAlgorithmException noSuchAlgorithmException = new NoSuchAlgorithmException();
        final IllegalStateException illegalStateException = assertThrows(IllegalStateException.class, () -> SingleHostHttpClientBuilder.RethrowGeneralSecurityException.handleGeneralSecurityException(() -> {
            throw noSuchAlgorithmException;
        }));

        assertSame(noSuchAlgorithmException, illegalStateException.getCause());
    }
}