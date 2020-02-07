package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.DnsLookupWrapper;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.SingleHostHttpClientBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SingleIpHttpClientTest {
    @SuppressWarnings("unchecked")
    private static final Class<HttpResponse.BodyHandler<String>> STRING_BODY_HANDLER_CLASS = (Class<HttpResponse.BodyHandler<String>>) HttpResponse.BodyHandlers.ofString().getClass();

    static {
        // Force init of the client without hostname check, otherwise it is cached
        SingleHostHttpClientBuilder.newHttpClient("test", mock(InetAddress.class));
    }

    @Test
    void shouldBeHealthyWithOneRefresh() {
        // Given
        final String hostname = "cloudflare.com";
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();
        final HttpClient httpClient = SingleHostHttpClientBuilder.newHttpClient(hostname, ip);
        // When
        try (final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, ip, new ServerConfiguration(hostname))) {
            // Then
            assertSame(httpClient, singleIpHttpClient.getHttpClient());
            assertTrue(singleIpHttpClient.isHealthy());
        }
    }

    @Test
    void shouldBeUnHealthyWith500Status() {
        // Given
        final String hostname = "cloudflare.com";
        final HttpClient httpClient = mock(HttpClient.class);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        @SuppressWarnings("unchecked") final HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpClient.sendAsync(captor.capture(), any(STRING_BODY_HANDLER_CLASS))).thenReturn(CompletableFuture.completedFuture(httpResponse));
        // When
        try (final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next(), new ServerConfiguration(hostname))) {
            // Then
            assertSame(httpClient, singleIpHttpClient.getHttpClient());
            assertFalse(singleIpHttpClient.isHealthy());
        }
    }

    @Test
    void shouldBeUnHealthyWith100Status() {
        // Given
        final String hostname = "cloudflare.com";
        final HttpClient httpClient = mock(HttpClient.class);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        @SuppressWarnings("unchecked") final HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(100);
        when(httpClient.sendAsync(captor.capture(), any(STRING_BODY_HANDLER_CLASS))).thenReturn(CompletableFuture.completedFuture(httpResponse));
        // When
        try (final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next(), new ServerConfiguration(hostname))) {
            // Then
            assertSame(httpClient, singleIpHttpClient.getHttpClient());
            assertFalse(singleIpHttpClient.isHealthy());
        }
    }


    @Test
    void shouldBeUnhealthyWithInvalidAddress() throws UnknownHostException {
        // Given
        // When
        final HttpClient httpClient = HttpClient.newHttpClient();
        try (final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, InetAddress.getLocalHost(), new ServerConfiguration("com.github.nhenneaux.resilienthttpclient.monitoredclientpool.SingleIpHttpClientTest.shouldCreateClientWithoutRefresh"))) {

            // Then
            assertSame(httpClient, singleIpHttpClient.getHttpClient());
            assertFalse(singleIpHttpClient.isHealthy());
        }

    }

    @Test
    void shouldFailOnMalformedUrl() {
        // Given
        final HttpClient httpClient = HttpClient.newHttpClient();
        // When - Then
        final IllegalStateException illegalStateException = assertThrows(IllegalStateException.class, () -> new SingleIpHttpClient(httpClient, InetAddress.getLocalHost(), new ServerConfiguration("com.github.nhenneaux.resilienthttpclient.monitoredclientpool.SingleIpHttpClientTest.shouldCreateClientWithoutRefresh", -234, "&dfsfsd", 1, 1, -1)));
        assertEquals("Cannot build health URI from ServerConfiguration{hostname='com.github.nhenneaux.resilienthttpclient.monitoredclientpool.SingleIpHttpClientTest.shouldCreateClientWithoutRefresh', port=-234, healthPath='&dfsfsd', connectionHealthCheckPeriodInSeconds=1, dnsLookupRefreshPeriodInSeconds=1}", illegalStateException.getMessage());
    }
}