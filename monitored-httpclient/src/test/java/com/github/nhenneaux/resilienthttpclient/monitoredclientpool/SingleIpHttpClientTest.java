package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class SingleIpHttpClientTest {

    @Test
    void shouldCreateClientWithoutRefresh() throws UnknownHostException {
        // Given
        // When
        final HttpClient httpClient = HttpClient.newHttpClient();
        try (final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, InetAddress.getLocalHost(), new ServerConfiguration("com.github.nhenneaux.resilienthttpclient.monitoredclientpool.SingleIpHttpClientTest.shouldCreateClientWithoutRefresh"))) {

            // Then
            assertSame(httpClient, singleIpHttpClient.getHttpClient());
            assertFalse(singleIpHttpClient.isHealthy());
        }

    }

}