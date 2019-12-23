package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import java.util.concurrent.TimeUnit;

public class ServerConfiguration {

    private static final long DEFAULT_DNS_LOOKUP_REFRESH_PERIOD_IN_SECONDS = TimeUnit.MINUTES.toSeconds(5);
    private static final long DEFAULT_CONNECTION_HEALTH_CHECK_PERIOD_IN_SECONDS = 30;

    private final String hostname;
    private final int port;
    private final String healthPath;
    private final long connectionHealthCheckPeriodInSeconds;
    private final long dnsLookupRefreshPeriodInSeconds;


    public ServerConfiguration(String hostname) {
        this(hostname, 443, "", DEFAULT_DNS_LOOKUP_REFRESH_PERIOD_IN_SECONDS, DEFAULT_CONNECTION_HEALTH_CHECK_PERIOD_IN_SECONDS);
    }

    public ServerConfiguration(String hostname, int port, String healthPath, long dnsLookupRefreshPeriodInSeconds, long connectionHealthCheckPeriodInSeconds) {
        this.hostname = hostname;
        this.port = port;
        this.healthPath = healthPath;
        this.connectionHealthCheckPeriodInSeconds = connectionHealthCheckPeriodInSeconds;
        this.dnsLookupRefreshPeriodInSeconds = dnsLookupRefreshPeriodInSeconds;
    }

    /**
     * The hostname of the HTTP client.
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * THE TCP port of the HTTP client.
     */
    public int getPort() {
        return port;
    }

    /**
     * Thea health path responding with HTTP code 2xx, 3xx, 4xx so that the client is considered healthy.
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

    @Override
    public String toString() {
        return "ServerConfiguration{" +
                "hostname='" + hostname + '\'' +
                ", port=" + port +
                ", healthPath='" + healthPath + '\'' +
                ", connectionHealthCheckPeriodInSeconds=" + connectionHealthCheckPeriodInSeconds +
                ", dnsLookupRefreshPeriodInSeconds=" + dnsLookupRefreshPeriodInSeconds +
                '}';
    }

}
