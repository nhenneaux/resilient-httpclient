package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ServerConfigurationTest {

    @Test
    void shouldProperlyReturnDefaultValues( ) {
        // Given
        final ServerConfiguration serverConfiguration = new ServerConfiguration("hostname");

        // When-Then
        Assertions.assertEquals("", serverConfiguration.getHealthPath());
        Assertions.assertEquals("hostname", serverConfiguration.getHostname());
        Assertions.assertEquals(30L, serverConfiguration.getConnectionHealthCheckPeriodInSeconds());
        Assertions.assertEquals(300L, serverConfiguration.getDnsLookupRefreshPeriodInSeconds());
        Assertions.assertEquals(-1, serverConfiguration.getPort());
        Assertions.assertEquals(-1, serverConfiguration.getReadTimeoutInMilliseconds());
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
        Assertions.assertEquals(-1, serverConfiguration.getReadTimeoutInMilliseconds());
    }

    @Test
    void shouldProperlyReturnConfiguredValues() {
        // Given
        final ServerConfiguration serverConfiguration = new ServerConfiguration(
                "hostname",
                1234,
                "/health",
                444L,
                555L,
                111L,
                0
        );

        // When-Then
        Assertions.assertEquals("/health", serverConfiguration.getHealthPath());
        Assertions.assertEquals("hostname", serverConfiguration.getHostname());
        Assertions.assertEquals(555L, serverConfiguration.getConnectionHealthCheckPeriodInSeconds());
        Assertions.assertEquals(444L, serverConfiguration.getDnsLookupRefreshPeriodInSeconds());
        Assertions.assertEquals(111L, serverConfiguration.getReadTimeoutInMilliseconds());
        Assertions.assertEquals(1234, serverConfiguration.getPort());
        Assertions.assertEquals("ServerConfiguration{hostname='hostname', port=1234, healthPath='/health', connectionHealthCheckPeriodInSeconds=555, dnsLookupRefreshPeriodInSeconds=444, readTimeoutInMilliseconds=111, failureResponseCountThreshold= 0}", serverConfiguration.toString());
    }


}