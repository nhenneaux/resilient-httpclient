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
        Assertions.assertEquals(443, serverConfiguration.getPort());
    }

    @Test
    void shouldProperlyReturnConfiguredValues( ) {
        // Given
        final ServerConfiguration serverConfiguration = new ServerConfiguration(
                "hostname",
                1234,
                "/health",
                444L,
                555L
        );

        // When-Then
        Assertions.assertEquals("/health", serverConfiguration.getHealthPath());
        Assertions.assertEquals("hostname", serverConfiguration.getHostname());
        Assertions.assertEquals(555L, serverConfiguration.getConnectionHealthCheckPeriodInSeconds());
        Assertions.assertEquals(444L, serverConfiguration.getDnsLookupRefreshPeriodInSeconds());
        Assertions.assertEquals(1234, serverConfiguration.getPort());
    }


}