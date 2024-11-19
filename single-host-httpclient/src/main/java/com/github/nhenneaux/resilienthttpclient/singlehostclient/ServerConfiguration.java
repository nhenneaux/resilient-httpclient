package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import java.net.http.HttpRequest;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ServerConfiguration {

    public static final int DEFAULT_PORT = -1;
    public static final String DEFAULT_HEALTH_PATH = "";
    public static final long DEFAULT_DNS_LOOKUP_REFRESH_PERIOD_IN_SECONDS = TimeUnit.MINUTES.toSeconds(5);
    public static final long DEFAULT_CONNECTION_HEALTH_CHECK_PERIOD_IN_SECONDS = 30;
    public static final long DEFAULT_HEALTH_READ_TIMEOUT_IN_MILLISECONDS = TimeUnit.SECONDS.toMillis(5);
    public static final int DEFAULT_FAILURE_RESPONSE_COUNT_THRESHOLD = -1; // It means no validation by failed response count
    public static final Consumer<HttpRequest.Builder> DEFAULT_REQUEST_TRANSFORMER = null;
    public static final String DEFAULT_PROTOCOL = "https";
    public static final Set<String> SUPPORTED_PROTOCOLS = Set.of("http", "https");

    private final String hostname;
    private final int port;
    private final String healthPath;
    private final long connectionHealthCheckPeriodInSeconds;
    private final long dnsLookupRefreshPeriodInSeconds;
    private final long healthReadTimeoutInMilliseconds;
    private final int failureResponseCountThreshold;
    private final Consumer<HttpRequest.Builder> requestTransformer;
    private final String protocol;

    public ServerConfiguration(String hostname) {
        this(
                hostname,
                DEFAULT_PORT,
                DEFAULT_HEALTH_PATH,
                DEFAULT_DNS_LOOKUP_REFRESH_PERIOD_IN_SECONDS,
                DEFAULT_CONNECTION_HEALTH_CHECK_PERIOD_IN_SECONDS,
                DEFAULT_HEALTH_READ_TIMEOUT_IN_MILLISECONDS,
                DEFAULT_FAILURE_RESPONSE_COUNT_THRESHOLD,
                DEFAULT_REQUEST_TRANSFORMER,
                DEFAULT_PROTOCOL
        );
    }

    public ServerConfiguration(
            String hostname,
            int port
    ) {
        this(hostname, port,
                DEFAULT_HEALTH_PATH,
                DEFAULT_DNS_LOOKUP_REFRESH_PERIOD_IN_SECONDS,
                DEFAULT_CONNECTION_HEALTH_CHECK_PERIOD_IN_SECONDS,
                DEFAULT_HEALTH_READ_TIMEOUT_IN_MILLISECONDS,
                DEFAULT_FAILURE_RESPONSE_COUNT_THRESHOLD,
                DEFAULT_REQUEST_TRANSFORMER,
                DEFAULT_PROTOCOL
        );
    }

    @SuppressWarnings("java:S107")// All parameters are needed
    public ServerConfiguration(
            String hostname,
            int port,
            String healthPath,
            long dnsLookupRefreshPeriodInSeconds,
            long connectionHealthCheckPeriodInSeconds,
            long healthReadTimeoutInMilliseconds,
            int failureResponseCountThreshold,
            Consumer<HttpRequest.Builder> requestTransformer
    ) {
        this(hostname, port, healthPath, dnsLookupRefreshPeriodInSeconds, connectionHealthCheckPeriodInSeconds, healthReadTimeoutInMilliseconds, failureResponseCountThreshold, requestTransformer, DEFAULT_PROTOCOL);
    }

    @SuppressWarnings("java:S107")// All parameters are needed
    public ServerConfiguration(
            String hostname,
            int port,
            String healthPath,
            long dnsLookupRefreshPeriodInSeconds,
            long connectionHealthCheckPeriodInSeconds,
            long healthReadTimeoutInMilliseconds,
            int failureResponseCountThreshold,
            Consumer<HttpRequest.Builder> requestTransformer,
            String protocol
    ) {
        this.hostname = hostname;
        this.port = port;
        this.healthPath = healthPath;
        this.connectionHealthCheckPeriodInSeconds = connectionHealthCheckPeriodInSeconds;
        this.dnsLookupRefreshPeriodInSeconds = dnsLookupRefreshPeriodInSeconds;
        this.healthReadTimeoutInMilliseconds = healthReadTimeoutInMilliseconds;
        this.failureResponseCountThreshold = failureResponseCountThreshold;
        this.requestTransformer = requestTransformer;
        if (protocol == null || !SUPPORTED_PROTOCOLS.contains(protocol)) {
            throw new IllegalArgumentException("Supported protocols are http or https, but was: " + protocol);
        }
        this.protocol = protocol;
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

    public Consumer<HttpRequest.Builder> getRequestTransformer() {
        return requestTransformer;
    }

    /**
     * The protocol to be used in requests: http or https
     */
    public String getProtocol() {
        return protocol;
    }

    @Override
    public String toString() {
        return "ServerConfiguration{" +
                "hostname='" + hostname + '\'' +
                ", port=" + port +
                ", healthPath='" + healthPath + '\'' +
                ", connectionHealthCheckPeriodInSeconds=" + connectionHealthCheckPeriodInSeconds +
                ", dnsLookupRefreshPeriodInSeconds=" + dnsLookupRefreshPeriodInSeconds +
                ", healthReadTimeoutInMilliseconds=" + healthReadTimeoutInMilliseconds +
                ", failureResponseCountThreshold= " + failureResponseCountThreshold +
                ", protocol= " + protocol +
                '}';
    }
}
