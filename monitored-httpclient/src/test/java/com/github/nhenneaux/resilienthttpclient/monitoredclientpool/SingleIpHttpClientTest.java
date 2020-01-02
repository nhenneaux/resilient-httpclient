package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.DnsLookupWrapper;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.SingleHostHttpClientProvider;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleIpHttpClientTest {

    @Test
    void shouldBeHealthyWithOneRefresh() {
        System.out.println(System.getProperty("jdk.internal.httpclient.disableHostnameVerification"));
        // Given
        final String hostname = "cloudflare.com";
        final HttpClient httpClient = new SingleHostHttpClientProvider().buildSingleHostnameHttpClient(hostname);
        // When
        try (final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).get(0), new ServerConfiguration(hostname))) {
            // Then
            assertSame(httpClient, singleIpHttpClient.getHttpClient());
            assertTrue(singleIpHttpClient.isHealthy());
        }
    }


    @Test
    void shouldBeUnhealthyWithInvalidAddress() throws UnknownHostException {
        System.out.println(System.getProperty("jdk.internal.httpclient.disableHostnameVerification"));
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
        final IllegalStateException illegalStateException = assertThrows(IllegalStateException.class, () -> new SingleIpHttpClient(httpClient, InetAddress.getLocalHost(), new ServerConfiguration("com.github.nhenneaux.resilienthttpclient.monitoredclientpool.SingleIpHttpClientTest.shouldCreateClientWithoutRefresh", -234, "&dfsfsd", 1, 1)));
        assertEquals("Cannot build health URI from ServerConfiguration{hostname='com.github.nhenneaux.resilienthttpclient.monitoredclientpool.SingleIpHttpClientTest.shouldCreateClientWithoutRefresh', port=-234, healthPath='&dfsfsd', connectionHealthCheckPeriodInSeconds=1, dnsLookupRefreshPeriodInSeconds=1}", illegalStateException.getMessage());
    }
}