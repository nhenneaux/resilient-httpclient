package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
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

class SingleHostHttpClientBuilderIT {
    public static final List<String> PUBLIC_HOST_TO_TEST = List.of("nicolas.henneaux.io","openjdk.org", "github.com", "twitter.com", "cloudflare.com", "facebook.com", "amazon.com", "google.com", "travis-ci.com", "en.wikipedia.org");
    static {
        // Force properties
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", SingleIpHttpRequest.HOST_HEADER);
    }

    @Test @Timeout(61)
    void shouldBuildSingleIpHttpClientAndWorksWithPublicWebsite() {
        // Given
        for (String hostname : PUBLIC_HOST_TO_TEST) {
            final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

            final HttpClient client = SingleHostHttpClientBuilder.newHttpClient(hostname, ip);


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

    @Test @Timeout(61)
    void shouldBuildSingleIpHttpClientAndWorksWithPublicWebsiteWithPort() throws URISyntaxException {
        // Given

        for (String hostname : PUBLIC_HOST_TO_TEST) {
            final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

            final HttpClient client = SingleHostHttpClientBuilder.newHttpClient(hostname, ip);


            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https", "", hostname, 443, "", "", ""))
                    .build();


            // When
            final String response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .join();

            // Then
            assertNotNull(response);
        }
    }

    @Test @Timeout(61)
    void shouldBuildSingleIpHttpClientAndWorksWithHttpClientBuilder() {
        // Given
        final var hostname = PUBLIC_HOST_TO_TEST.get(0);
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

        final HttpClient client = SingleHostHttpClientBuilder.builder(hostname, ip, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))).withTlsNameMatching().withSni().buildWithHostHeader();


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + hostname))
                .build();


        // When
        final String response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .join();

        // Then
        assertNotNull(response);
    }


    @Test @Timeout(61)
    void shouldBuildSingleIpHttpClientAndWorksWithCustomSslContext() throws NoSuchAlgorithmException {
        // Given
        final var hostname =PUBLIC_HOST_TO_TEST.get(1);
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

        final HttpClient client = SingleHostHttpClientBuilder.builder(hostname, ip, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)))
                .withTlsNameMatching(SSLContext.getInstance("TLSv1.2"))
                .build();


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

    @Test @Timeout(61)
    void shouldBuildSingleIpHttpClientAndWorksWithNullTruststore() {
        // Given
        final var hostname = PUBLIC_HOST_TO_TEST.get(0);
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

        final HttpClient client = SingleHostHttpClientBuilder.builder(hostname, ip, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2L))).withTlsNameMatching((KeyStore) null).withSni().buildWithHostHeader();


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

    @Test @Timeout(61)
    void shouldBuildSingleIpHttpClientWithMutualTls() throws Exception {
        // Given
        final var hostname = "client.badssl.com";
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(getClass().getResourceAsStream("/truststore.p12"), "p12-pass".toCharArray());

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] password = "badssl.com".toCharArray();
        keyStore.load(getClass().getResourceAsStream("/badssl.com-client.p12"), password);

        final HttpClient client = SingleHostHttpClientBuilder.builder(hostname, ip, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2L)))
                .withTlsNameMatching(trustStore, keyStore, password)
                .withSni()
                .buildWithHostHeader();


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + hostname))
                .build();


        // When
        final String response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .join();

        // Then
        assertNotNull(response);
    }

    @Test @Timeout(61)
    void shouldBuildSingleIpHttpClientWithMutualTlsCertMissing() throws Exception {
        // Given
        final var hostname = "client-cert-missing.badssl.com";
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(getClass().getResourceAsStream("/truststore.p12"), "p12-pass".toCharArray());

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] password = "badssl.com".toCharArray();
        keyStore.load(getClass().getResourceAsStream("/badssl.com-client.p12"), password);

        final HttpClient client = SingleHostHttpClientBuilder.builder(hostname, ip, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2L)))
                .withTlsNameMatching(trustStore, keyStore, password)
                .withSni()
                .buildWithHostHeader();


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + hostname))
                .build();


        // When
        final String response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .join();

        // Then
        assertNotNull(response);
    }

    @Test @Timeout(61)
    void shouldTestWithSni() {
        // Given
        // Domain is not working when sni is not working correctly
        final var hostname = "24max.de";
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

        final HttpClient client = SingleHostHttpClientBuilder.newHttpClient(hostname, ip);


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


    @Test @Timeout(61)
    void shouldValidateWrongHost() {
        // Given
        String hostname = "wrong.host.badssl.com";
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

        final HttpClient client = SingleHostHttpClientBuilder.newHttpClient(hostname, ip);


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + ip))
                .build();

        final CompletableFuture<String> stringCompletableFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);

        // When
        final CompletionException completionException = assertThrows(CompletionException.class, stringCompletableFuture::join);
        // Then
        assertEquals(SSLHandshakeException.class, completionException.getCause().getClass());
    }

    @Test @Timeout(61)
    void shouldValidateWith1000SAN() {
        // Given
        String hostname = "1000-sans.badssl.com";
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

        final HttpClient client = SingleHostHttpClientBuilder.builder(hostname, ip, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2L))).withTlsNameMatching().buildWithHostHeader();


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + ip))
                .build();

        final CompletableFuture<String> stringCompletableFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);

        // When
        final CompletionException completionException = assertThrows(CompletionException.class, stringCompletableFuture::join);
        // Then
        assertEquals(SSLHandshakeException.class, completionException.getCause().getClass());
    }

    @Test @Timeout(61)
    void shouldValidateNoSubject() {
        // Given
        String hostname = "no-subject.badssl.com";
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

        final HttpClient client = SingleHostHttpClientBuilder.builder(hostname, ip, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2L))).withTlsNameMatching().buildWithHostHeader();


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + ip))
                .build();

        final CompletableFuture<String> stringCompletableFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);

        // When
        final CompletionException completionException = assertThrows(CompletionException.class, stringCompletableFuture::join);
        // Then
        assertEquals(SSLHandshakeException.class, completionException.getCause().getClass());
    }

    @Test @Timeout(61)
    void shouldValidateNoCommonName() {
        // Given
        String hostname = "no-common-name.badssl.com";
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

        final HttpClient client = SingleHostHttpClientBuilder.builder(hostname, ip, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2L))).withTlsNameMatching().buildWithHostHeader();


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + ip))
                .build();
        final CompletableFuture<String> stringCompletableFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);

        // When
        final CompletionException completionException = assertThrows(CompletionException.class, stringCompletableFuture::join);
        // Then
        assertEquals(SSLHandshakeException.class, completionException.getCause().getClass());
    }

    @Test @Timeout(61)
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

    @Test @Timeout(61)
    void unreachableAddress() throws UnknownHostException {
        final HttpClient client = SingleHostHttpClientBuilder.builder("no.http.server", InetAddress.getByName("10.2.3.4"), HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200))).withTlsNameMatching().withSni().build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://10.2.3.4"))
                .build();


        // When
        final CompletableFuture<String> stringCompletableFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);


        // Then
        final ExecutionException executionException = assertThrows(ExecutionException.class, stringCompletableFuture::get);
        assertThat(executionException.getMessage(), Matchers.anyOf(Matchers.equalTo("java.net.http.HttpConnectTimeoutException: HTTP connect timed out"), Matchers.equalTo("java.net.ConnectException: Connection refused"), Matchers.equalTo("java.net.ConnectException: No route to host")));
    }

    @Test @Timeout(61)
    void noSubjectAlternativeName() throws UnknownHostException {
        final HttpClient client = SingleHostHttpClientBuilder.builder("no.http.server", InetAddress.getByName("1.1.1.1"), HttpClient.newBuilder().connectTimeout(Duration.ofMillis(5_000))).withTlsNameMatching().withSni().build();

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

    @Test @Timeout(61)
    void noHttpsServer() {
        // Given
        String hostname = "http.badssl.com";
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

        final HttpClient client = SingleHostHttpClientBuilder.builder(hostname, ip, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2L))).withTlsNameMatching().buildWithHostHeader();


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


    @Test @Timeout(61)
    void shouldHandleNoSuchAlgorithm() {
        final NoSuchAlgorithmException noSuchAlgorithmException = new NoSuchAlgorithmException();
        final IllegalStateException illegalStateException = assertThrows(IllegalStateException.class, () -> SingleHostHttpClientBuilder.RethrowGeneralSecurityException.handleGeneralSecurityException(() -> {
            throw noSuchAlgorithmException;
        }));

        assertSame(noSuchAlgorithmException, illegalStateException.getCause());
    }
}