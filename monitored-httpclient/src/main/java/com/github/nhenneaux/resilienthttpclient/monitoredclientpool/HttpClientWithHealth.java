package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singleipclient.HttpClientForSpecificIpFactory;
import com.github.nhenneaux.resilienthttpclient.singleipclient.ServerConfiguration;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpClientWithHealth implements ItemWithHealth {
    private static final Logger LOGGER = Logger.getLogger(HttpClientForSpecificIpFactory.class.getSimpleName());

    private final HttpClient httpClient;
    private final InetAddress inetAddress;
    private final URI healthUri;

    public HttpClientWithHealth(HttpClient httpClient, InetAddress inetAddress, ServerConfiguration serverConfiguration) {
        this.httpClient = httpClient;
        this.inetAddress = inetAddress;
        try {
            healthUri = new URL("https", inetAddress.getHostAddress(), serverConfiguration.getPort(), serverConfiguration.getHealthPath()).toURI();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalStateException("Cannot build health URI from " + serverConfiguration, e);
        }
    }

    @Override
    public boolean isHealthy() {
        final long start = System.nanoTime();
        try {
            final int statusCode = httpClient.sendAsync(HttpRequest.newBuilder()
                            .uri(healthUri)
                            .build(),
                    HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::statusCode)
                    .join();

            LOGGER.log(Level.INFO, () -> "Checked health for URI " + healthUri + ", status is `" + statusCode + "` in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms.");
            return statusCode >= 200 && statusCode <= 499;
        } catch (RuntimeException e) {
            LOGGER.log(Level.INFO, () -> "Failed to check health for address " + healthUri + ", error is `" + e + "`.");
            return false;
        }
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    public String toString() {
        return "HttpClientWithHealth{" +
                "inetAddress=" + inetAddress +
                '}';
    }
}
