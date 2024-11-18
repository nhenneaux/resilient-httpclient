package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration.DEFAULT_REQUEST_TRANSFORMER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        Assertions.assertEquals("https", serverConfiguration.getProtocol());
    }

    @Test
    void testUnsupportedProtocol() {
        IllegalArgumentException illegalStateException = assertThrows(IllegalArgumentException.class, () ->
                new ServerConfiguration(
                        "hostname",
                        1234,
                        "/health",
                        444L,
                        555L,
                        111L,
                        0,
                        DEFAULT_REQUEST_TRANSFORMER,
                        "abc"
                ));
        assertEquals("Supported protocols are [http, https], but was: abc", illegalStateException.getMessage());
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
        Assertions.assertEquals("https", serverConfiguration.getProtocol());
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
                0,
                DEFAULT_REQUEST_TRANSFORMER,
                "http"
        );

        // When-Then
        Assertions.assertEquals("/health", serverConfiguration.getHealthPath());
        Assertions.assertEquals("hostname", serverConfiguration.getHostname());
        Assertions.assertEquals(555L, serverConfiguration.getConnectionHealthCheckPeriodInSeconds());
        Assertions.assertEquals(444L, serverConfiguration.getDnsLookupRefreshPeriodInSeconds());
        Assertions.assertEquals(111L, serverConfiguration.getHealthReadTimeoutInMilliseconds());
        Assertions.assertEquals(1234, serverConfiguration.getPort());
        Assertions.assertEquals("http", serverConfiguration.getProtocol());
        Assertions.assertEquals("ServerConfiguration{hostname='hostname', port=1234, healthPath='/health', connectionHealthCheckPeriodInSeconds=555, dnsLookupRefreshPeriodInSeconds=444, healthReadTimeoutInMilliseconds=111, failureResponseCountThreshold= 0, protocol= http}", serverConfiguration.toString());
    }


}