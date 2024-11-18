package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
import static org.hamcrest.Matchers.allOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SingleHostHttpClientBuilderTest {
    public static final List<String> PUBLIC_HOST_TO_TEST = List.of(
            "openjdk.org",
            "github.com",
            "twitter.com",
            "cloudflare.com",
            "facebook.com",
            "amazon.com",
            "en.wikipedia.org");
    public static final List<String> PUBLIC_HOST_TO_TEST_WITH_SNI = List.of(
            "nicolas.henneaux.io",
            "google.com",
            "travis-ci.com");

    static {
        // Force properties
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", SingleIpHttpRequest.HOST_HEADER);
    }

    public static List<String> publicHosts() {
        return PUBLIC_HOST_TO_TEST;
    }

    public static List<String> publicSpecificHosts() {
        return PUBLIC_HOST_TO_TEST_WITH_SNI;
    }

    @ParameterizedTest
    @Timeout(61)
    @MethodSource("publicHosts")
    void shouldBuildSingleIpHttpClientAndWorksWithPublicWebsite(String hostname) {
        // Given
        System.out.println("Validate " + hostname);
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

    @ParameterizedTest
    @Timeout(61)
    @MethodSource("publicSpecificHosts")
    void shouldBuildSingleIpHttpClientAndWorksWithSpecificPublicWebsite(String hostname) {
        if (List.of(22, 23).contains(Runtime.version().feature())) {
            // Failing in Java 22-23, regression in JDK https://bugs.openjdk.org/browse/JDK-8346705
            return;
        }
        // Given
        System.out.println("Validate " + hostname);
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();

        final HttpClient client = SingleHostHttpClientBuilder.newHttpClient(hostname, ip);


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + ip))
                .build();


        // When
        final int statusCode = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::statusCode)
                .join();

        // Then
        assertThat(statusCode, allOf(Matchers.greaterThanOrEqualTo(200), Matchers.lessThanOrEqualTo(499)));
    }

    @ParameterizedTest
    @Timeout(61)
    @MethodSource("publicHosts")
    void shouldBuildSingleIpHttpClientAndWorksWithPublicWebsiteWithPort(String hostname) throws URISyntaxException {
        // Given

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

    @Test
    @Timeout(61)
    void shouldBuildSingleIpHttpClientAndWorksWithHttpClientBuilder() {
        // Given
        final var hostname = oneHostname();
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

    private static String oneHostname() {
        return PUBLIC_HOST_TO_TEST.get(0);
    }


    @Test
    @Timeout(61)
    void shouldBuildSingleIpHttpClientAndWorksWithCustomSslContext() throws NoSuchAlgorithmException {
        // Given
        final var hostname = PUBLIC_HOST_TO_TEST.get(1);
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

    @Test
    @Timeout(61)
    void shouldBuildSingleIpHttpClientAndWorksWithNullTruststore() {
        // Given
        final var hostname = oneHostname();
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

    @Test
    @Timeout(61)
    void shouldBuildSingleIpHttpClientWithMutualTls() throws Exception {
        if (List.of(22, 23).contains(Runtime.version().feature())) {
            // Failing in Java 22-23, regression in JDK https://bugs.openjdk.org/browse/JDK-8346705
           return;
        }
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

    @Test
    @Timeout(61)
    void shouldBuildSingleIpHttpClientWithMutualTlsCertMissing() throws Exception {
        if (List.of(22, 23).contains(Runtime.version().feature())) {
            // Failing in Java 22-23, regression in JDK https://bugs.openjdk.org/browse/JDK-8346705
            return;
        }
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

    @Test
    @Timeout(61)
    void shouldTestWithSni() {
        if (List.of(22, 23).contains(Runtime.version().feature())) {
            // Failing in Java 22-23, regression in JDK https://bugs.openjdk.org/browse/JDK-8346705
            return;
        }
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


    public static List<String> hostValidateCertificates() {
        return List.of("wrong.host.badssl.com", "1000-sans.badssl.com", "no-subject.badssl.com", "no-common-name.badssl.com");
    }

    @ParameterizedTest
    @MethodSource("hostValidateCertificates")
    @Timeout(61)
    void shouldValidateCertificate(String hostname) {
        // Given
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

    @Test
    @Timeout(61)
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
    @Timeout(61)
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

    @Test
    @Timeout(61)
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
        assertThat(
                executionException.getMessage(),
                Matchers.oneOf("javax.net.ssl.SSLHandshakeException: No subject alternative DNS name matching no.http.server found.",
                        "javax.net.ssl.SSLHandshakeException: (certificate_unknown) No subject alternative DNS name matching no.http.server found."
                )
        );
    }

    @Test
    @Timeout(61)
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
        assertThat(
                executionException.getMessage(),
                Matchers.oneOf("javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target",
                        "javax.net.ssl.SSLHandshakeException: (certificate_unknown) PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target"
                )
        );
    }


    @Test
    @Timeout(61)
    void shouldHandleNoSuchAlgorithm() {
        final NoSuchAlgorithmException noSuchAlgorithmException = new NoSuchAlgorithmException();
        final IllegalStateException illegalStateException = assertThrows(IllegalStateException.class, () -> SingleHostHttpClientBuilder.RethrowGeneralSecurityException.handleGeneralSecurityException(() -> {
            throw noSuchAlgorithmException;
        }));

        assertSame(noSuchAlgorithmException, illegalStateException.getCause());
    }
}