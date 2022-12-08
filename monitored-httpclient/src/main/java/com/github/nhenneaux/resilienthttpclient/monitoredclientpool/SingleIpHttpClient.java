package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;

import java.lang.System.Logger;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.Logger.Level;

public class SingleIpHttpClient implements AutoCloseable {

    private static final Logger LOGGER = System.getLogger(SingleIpHttpClient.class.getSimpleName());

    private final HttpClient httpClient;
    private final InetAddress inetAddress;
    private final URI healthUri;
    private final AtomicBoolean healthy;
    private final Future<?> scheduledFuture;
    private final ServerConfiguration serverConfiguration;
    private final AtomicInteger failedResponseCount;

    /**
     * Create a new instance of the client and schedule a task to refresh is healthiness.
     *
     * @param httpClient               the underlying HTTP client
     * @param inetAddress              the target IP address
     * @param serverConfiguration      the configuration of the server
     * @param scheduledExecutorService the scheduled executor service to schedule the refresh.
     */
    public SingleIpHttpClient(
            HttpClient httpClient,
            InetAddress inetAddress,
            ServerConfiguration serverConfiguration,
            ScheduledExecutorService scheduledExecutorService
    ) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.inetAddress = Objects.requireNonNull(inetAddress);
        this.healthUri = healthUri(Objects.requireNonNull(serverConfiguration));
        this.serverConfiguration = serverConfiguration;
        this.healthy = new AtomicBoolean();
        this.failedResponseCount = new AtomicInteger(0);

        final long connectionHealthCheckPeriodInSeconds = serverConfiguration.getConnectionHealthCheckPeriodInSeconds();
        this.scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(
                this::checkHealthStatus,
                0L,
                connectionHealthCheckPeriodInSeconds,
                TimeUnit.SECONDS
        );
    }

    /**
     * Create a new instance and check its health once.
     *
     * @param httpClient          the underlying HTTP client
     * @param inetAddress         the target IP address
     * @param serverConfiguration the configuration of the server
     */
    public SingleIpHttpClient(
            HttpClient httpClient,
            InetAddress inetAddress,
            ServerConfiguration serverConfiguration
    ) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.inetAddress = Objects.requireNonNull(inetAddress);
        this.healthUri = healthUri(Objects.requireNonNull(serverConfiguration));
        this.serverConfiguration = serverConfiguration;
        this.healthy = new AtomicBoolean();
        this.failedResponseCount = new AtomicInteger(0);

        this.scheduledFuture = CompletableFuture.completedFuture(null);
        checkHealthStatus();
    }

    private URI healthUri(ServerConfiguration serverConfiguration) {
        try {
            return new URL("https", serverConfiguration.getHostname(), serverConfiguration.getPort(), serverConfiguration.getHealthPath()).toURI();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalArgumentException("Cannot build health URI from " + serverConfiguration, e);
        }
    }

    /**
     * If called and the previous health status was unhealthy, then a new health check is performed.
     */
    public boolean isHealthy() {
        if (!healthy.get()) {
            checkHealthStatus();
        }
        return healthy.get();
    }

    /**
     * Determine whether this client is able to reach the given IP address through HTTP protocol and get a valid HTTP response, i.e. with status between 200 and 499.
     */
    void checkHealthStatus() {
        final long start = System.nanoTime();
        try {
            final HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder().uri(healthUri);
            if (serverConfiguration.getHealthReadTimeoutInMilliseconds() >= 0) {
                httpRequestBuilder.timeout(Duration.ofMillis(serverConfiguration.getHealthReadTimeoutInMilliseconds()));
            }
            final int statusCode = httpClient.sendAsync(httpRequestBuilder.build(), HttpResponse.BodyHandlers.discarding())
                    .thenApply(HttpResponse::statusCode)
                    .join();

            LOGGER.log(Level.INFO, () -> "Checked health for URI " + healthUri + ", status is `" + statusCode + "`" + timingLogStatement(start));

            healthy.set(isSuccessCode(statusCode));
            refreshFailureCountWithStatusCode(statusCode);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, () -> "Failed to check health for address " + healthUri + ", error is `" + e + "`" + timingLogStatement(start), e);
            healthy.set(false);
            incrementFailureCount();
        }
    }

    /**
     * Validates if the client is within failed response count threshold.
     */
    boolean shouldBeRefreshed() {
        final int failureResponseCountThreshold = serverConfiguration.getFailureResponseCountThreshold();

        return failureResponseCountThreshold != -1 && failedResponseCount.get() >= failureResponseCountThreshold;
    }

    void refreshFailureCountWithStatusCode(final int statusCode) {
        if (!isSuccessCode(statusCode)) {
            incrementFailureCount();
        }
    }

    void incrementFailureCount() {
        failedResponseCount.incrementAndGet();
    }

    private String timingLogStatement(long start) {
        return " in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms.";
    }

    private boolean isSuccessCode(final int httpStatusCode) {
        return 200 <= httpStatusCode && httpStatusCode <= 499;
    }

    InetAddress getInetAddress() {
        return inetAddress;
    }

    AtomicBoolean getHealthy() {
        return healthy;
    }

    URI getHealthUri() {
        return healthUri;
    }

    String getHostname() {
        return serverConfiguration.getHostname();
    }

    int getFailedResponseCount() {
        return failedResponseCount.get();
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    public String toString() {
        return "SingleIpHttpClient{" +
                "inetAddress=" + inetAddress +
                ", healthy=" + healthy +
                ", hostname=" + serverConfiguration.getHostname() +
                ", healthUri=" + healthUri +
                ", failedResponseCount=" + failedResponseCount.get() +
                '}';
    }

    @Override
    public void close() {
        scheduledFuture.cancel(true);
    }

}
