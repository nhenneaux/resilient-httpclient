package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;

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
import java.util.logging.Level;
import java.util.logging.Logger;

public class SingleIpHttpClient implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(SingleIpHttpClient.class.getSimpleName());

    private final HttpClient httpClient;
    private final InetAddress inetAddress;
    private final URI healthUri;
    private final AtomicBoolean healthy;
    private final Future<?> scheduledFuture;
    private final ServerConfiguration serverConfiguration;

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
        Objects.requireNonNull(serverConfiguration);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.inetAddress = Objects.requireNonNull(inetAddress);
        this.healthUri = healthUri(inetAddress, serverConfiguration);

        this.serverConfiguration = serverConfiguration;
        this.healthy = new AtomicBoolean();

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
        Objects.requireNonNull(serverConfiguration);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.inetAddress = Objects.requireNonNull(inetAddress);
        this.healthUri = healthUri(inetAddress, serverConfiguration);

        this.serverConfiguration = serverConfiguration;
        this.healthy = new AtomicBoolean();
        this.scheduledFuture = CompletableFuture.completedFuture(null);
        checkHealthStatus();
    }

    private URI healthUri(InetAddress inetAddress, ServerConfiguration serverConfiguration) {
        try {
            return new URL("https", inetAddress.getHostAddress(), serverConfiguration.getPort(), serverConfiguration.getHealthPath()).toURI();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalStateException("Cannot build health URI from " + serverConfiguration, e);
        }
    }

    public boolean isHealthy() {
        return healthy.get();
    }

    /**
     * Determine whether this client is able to reach the given IP address through HTTP protocol and get a valid HTTP response, i.e. with status between 200 and 499.
     */
    void checkHealthStatus() {
        final long start = System.nanoTime();
        try {
            final HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder().uri(healthUri);
            if (serverConfiguration.getReadTimeoutInSeconds() >= 0) {
                httpRequestBuilder.timeout(Duration.ofSeconds(serverConfiguration.getReadTimeoutInSeconds()));
            }
            final int statusCode = httpClient.sendAsync(httpRequestBuilder
                            .build(),
                    HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::statusCode)
                    .join();

            LOGGER.log(Level.INFO, () -> "Checked health for URI " + healthUri + ", status is `" + statusCode + "`" + timingLogStatement(start));
            healthy.set(statusCode >= 200 && statusCode <= 499);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to check health for address " + healthUri + ", error is `" + e + "`" + timingLogStatement(start), e);
            healthy.set(false);
        }
    }

    private String timingLogStatement(long start) {
        return " in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms.";
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    public String toString() {
        return "SingleIpHttpClient{" +
                "inetAddress=" + inetAddress +
                ", healthUri=" + healthUri +
                '}';
    }

    @Override
    public void close() {
        scheduledFuture.cancel(true);
    }

}
