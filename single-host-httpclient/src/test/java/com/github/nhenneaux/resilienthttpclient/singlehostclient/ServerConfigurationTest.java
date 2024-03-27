package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration.DEFAULT_REQUEST_TRANSFORMER;

class ServerConfigurationTest {

    @Test
    void shouldProperlyReturnDefaultValues() {
        // Given
        final ServerConfiguration serverConfiguration = new ServerConfiguration("hostname");

        // When-Then
        Assertions.assertEquals("", serverConfiguration.getHealthPath());
        Assertions.assertEquals("hostname", serverConfiguration.getHostname());
        Assertions.assertEquals(30L, serverConfiguration.getConnectionHealthCheckPeriodInSeconds());
        Assertions.assertEquals(300L, serverConfiguration.getDnsLookupRefreshPeriodInSeconds());
        Assertions.assertEquals(-1, serverConfiguration.getPort());
        Assertions.assertEquals(5000, serverConfiguration.getHealthReadTimeoutInMilliseconds());
        Assertions.assertEquals(-1, serverConfiguration.getFailureResponseCountThreshold());
    }

    @Test
    void shouldProperlyReturnDefaultValuesWithPort() {
        // Given
        final ServerConfiguration serverConfiguration = new ServerConfiguration("hostname", 1234);

        // When-Then
        Assertions.assertEquals("", serverConfiguration.getHealthPath());
        Assertions.assertEquals("hostname", serverConfiguration.getHostname());
        Assertions.assertEquals(30L, serverConfiguration.getConnectionHealthCheckPeriodInSeconds());
        Assertions.assertEquals(300L, serverConfiguration.getDnsLookupRefreshPeriodInSeconds());
        Assertions.assertEquals(1234, serverConfiguration.getPort());
        Assertions.assertEquals(5000, serverConfiguration.getHealthReadTimeoutInMilliseconds());
        Assertions.assertEquals(-1, serverConfiguration.getFailureResponseCountThreshold());
    }

    @Test
    void shouldProperlyReturnConfiguredValues() {
        // Given
        final ServerConfiguration serverConfiguration = new ServerConfiguration(
                "hostname",
                1234,
                "/health",
                "49070868",
                444L,
                555L,
                111L,
                0,
                DEFAULT_REQUEST_TRANSFORMER
        );

        // When-Then
        Assertions.assertEquals("/health", serverConfiguration.getHealthPath());
        Assertions.assertEquals("hostname", serverConfiguration.getHostname());
        Assertions.assertEquals(555L, serverConfiguration.getConnectionHealthCheckPeriodInSeconds());
        Assertions.assertEquals(444L, serverConfiguration.getDnsLookupRefreshPeriodInSeconds());
        Assertions.assertEquals(111L, serverConfiguration.getHealthReadTimeoutInMilliseconds());
        Assertions.assertEquals(1234, serverConfiguration.getPort());
        Assertions.assertEquals("ServerConfiguration{hostname='hostname', port=1234, healthPath='/health', healthCheckRequestBody='49070868', connectionHealthCheckPeriodInSeconds=555, dnsLookupRefreshPeriodInSeconds=444, healthReadTimeoutInMilliseconds=111, failureResponseCountThreshold= 0}", serverConfiguration.toString());
    }


}