package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import java.util.concurrent.TimeUnit;

public class ServerConfiguration {

    private static final int DEFAULT_PORT = -1;
    private static final String DEFAULT_HEALTH_PATH = "";
    private static final long DEFAULT_DNS_LOOKUP_REFRESH_PERIOD_IN_SECONDS = TimeUnit.MINUTES.toSeconds(5);
    private static final long DEFAULT_CONNECTION_HEALTH_CHECK_PERIOD_IN_SECONDS = 30;
    private static final long DEFAULT_READ_TIMEOUT_IN_MILLISECONDS = -1; // It means there is no read timeout

    private final String hostname;
    private final int port;
    private final String healthPath;
    private final long connectionHealthCheckPeriodInSeconds;
    private final long dnsLookupRefreshPeriodInSeconds;
    private final long readTimeoutInMilliseconds;

    public ServerConfiguration(String hostname) {
        this(
                hostname,
                DEFAULT_PORT,
                DEFAULT_HEALTH_PATH,
                DEFAULT_DNS_LOOKUP_REFRESH_PERIOD_IN_SECONDS,
                DEFAULT_CONNECTION_HEALTH_CHECK_PERIOD_IN_SECONDS,
                DEFAULT_READ_TIMEOUT_IN_MILLISECONDS
        );
    }

    public ServerConfiguration(
            String hostname,
            int port,
            String healthPath,
            long dnsLookupRefreshPeriodInSeconds,
            long connectionHealthCheckPeriodInSeconds,
            long readTimeoutInMilliseconds
    ) {
        this.hostname = hostname;
        this.port = port;
        this.healthPath = healthPath;
        this.connectionHealthCheckPeriodInSeconds = connectionHealthCheckPeriodInSeconds;
        this.dnsLookupRefreshPeriodInSeconds = dnsLookupRefreshPeriodInSeconds;
        this.readTimeoutInMilliseconds = readTimeoutInMilliseconds;
    }

    /**
     * The hostname of the HTTP client.
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * The TCP port of the HTTP client, -1 if the port is not set.
     */
    public int getPort() {
        return port;
    }

    /**
     * The health path responding with HTTP code 2xx, 3xx, 4xx so that the client is considered healthy.
     */
    public String getHealthPath() {
        return healthPath;
    }

    /**
     * The DNS delay in seconds to refresh the resolution of {@link #getHostname()}.
     */
    public long getDnsLookupRefreshPeriodInSeconds() {
        return dnsLookupRefreshPeriodInSeconds;
    }

    /**
     * The delay in seconds between health checks to {@link #getHealthPath()}.
     */
    public long getConnectionHealthCheckPeriodInSeconds() {
        return connectionHealthCheckPeriodInSeconds;
    }

    /**
     * The read timeout in ms. By default it has a value of "-1" which interpreted as no read timeout specified.
     */
    public long getReadTimeoutInMilliseconds() {
        return readTimeoutInMilliseconds;
    }

    @Override
    public String toString() {
        return "ServerConfiguration{" +
                "hostname='" + hostname + '\'' +
                ", port=" + port +
                ", healthPath='" + healthPath + '\'' +
                ", connectionHealthCheckPeriodInSeconds=" + connectionHealthCheckPeriodInSeconds +
                ", dnsLookupRefreshPeriodInSeconds=" + dnsLookupRefreshPeriodInSeconds +
                ", readTimeoutInMilliseconds=" + readTimeoutInMilliseconds +
                '}';
    }
}
