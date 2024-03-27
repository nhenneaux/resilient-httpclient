package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import java.util.concurrent.TimeUnit;

public class ServerConfiguration {

    private static final int DEFAULT_PORT = -1;
    private static final String DEFAULT_HEALTH_PATH = "";
    private static final String DEFAULT_HEALTH_CHECK_REQUEST_BODY = "";
    private static final long DEFAULT_DNS_LOOKUP_REFRESH_PERIOD_IN_SECONDS = TimeUnit.MINUTES.toSeconds(5);
    private static final long DEFAULT_CONNECTION_HEALTH_CHECK_PERIOD_IN_SECONDS = 30;
    private static final long DEFAULT_HEALTH_READ_TIMEOUT_IN_MILLISECONDS = TimeUnit.SECONDS.toMillis(5);
    private static final int DEFAULT_FAILURE_RESPONSE_COUNT_THRESHOLD = -1; // It means no validation by failed response count

    private final String hostname;
    private final int port;
    private final String healthPath;
    private final String healthCheckRequestBody;
    private final long connectionHealthCheckPeriodInSeconds;
    private final long dnsLookupRefreshPeriodInSeconds;
    private final long healthReadTimeoutInMilliseconds;
    private final int failureResponseCountThreshold;

    public ServerConfiguration(String hostname) {
        this(
                hostname,
                DEFAULT_PORT,
                DEFAULT_HEALTH_PATH,
                DEFAULT_HEALTH_CHECK_REQUEST_BODY,
                DEFAULT_DNS_LOOKUP_REFRESH_PERIOD_IN_SECONDS,
                DEFAULT_CONNECTION_HEALTH_CHECK_PERIOD_IN_SECONDS,
                DEFAULT_HEALTH_READ_TIMEOUT_IN_MILLISECONDS,
                DEFAULT_FAILURE_RESPONSE_COUNT_THRESHOLD
        );
    }

    public ServerConfiguration(
            String hostname,
            int port
    ) {
        this(hostname, port,
                DEFAULT_HEALTH_PATH,
                DEFAULT_HEALTH_CHECK_REQUEST_BODY,
                DEFAULT_DNS_LOOKUP_REFRESH_PERIOD_IN_SECONDS,
                DEFAULT_CONNECTION_HEALTH_CHECK_PERIOD_IN_SECONDS,
                DEFAULT_HEALTH_READ_TIMEOUT_IN_MILLISECONDS,
                DEFAULT_FAILURE_RESPONSE_COUNT_THRESHOLD);
    }

    public ServerConfiguration(
            String hostname,
            int port,
            String healthPath,
            String healthCheckRequestBody,
            long dnsLookupRefreshPeriodInSeconds,
            long connectionHealthCheckPeriodInSeconds,
            long healthReadTimeoutInMilliseconds,
            int failureResponseCountThreshold
    ) {
        this.hostname = hostname;
        this.port = port;
        this.healthPath = healthPath;
        this.healthCheckRequestBody = healthCheckRequestBody;
        this.connectionHealthCheckPeriodInSeconds = connectionHealthCheckPeriodInSeconds;
        this.dnsLookupRefreshPeriodInSeconds = dnsLookupRefreshPeriodInSeconds;
        this.healthReadTimeoutInMilliseconds = healthReadTimeoutInMilliseconds;
        this.failureResponseCountThreshold = failureResponseCountThreshold;

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
     * The healthCheckRequestBody object to be used for the POST healthcheck.
     */
    public String getHealthCheckRequestBody() {
        return healthCheckRequestBody;
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
     * The read timeout in ms
     */
    public long getHealthReadTimeoutInMilliseconds() {
        return healthReadTimeoutInMilliseconds;
    }

    /**
     * Threshold to create new client instead of using existing one.
     * Calculated as total request count - success response count.
     * A value of "-1" {i.e. default} indicates no validation by failure count.
     */
    public int getFailureResponseCountThreshold() {
        return failureResponseCountThreshold;
    }

    @Override
    public String toString() {
        return "ServerConfiguration{" +
               "hostname='" + hostname + '\'' +
               ", port=" + port +
               ", healthPath='" + healthPath + '\'' +
               ", healthCheckRequestBody='" + healthCheckRequestBody + '\'' +
               ", connectionHealthCheckPeriodInSeconds=" + connectionHealthCheckPeriodInSeconds +
               ", dnsLookupRefreshPeriodInSeconds=" + dnsLookupRefreshPeriodInSeconds +
               ", healthReadTimeoutInMilliseconds=" + healthReadTimeoutInMilliseconds +
               ", failureResponseCountThreshold= " + failureResponseCountThreshold +
               '}';
    }
}
