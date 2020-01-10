package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.DnsLookupWrapper;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.SingleHostHttpClientBuilder;

import java.net.http.HttpClient;
import java.security.KeyStore;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class HttpClientPoolBuilder {

    private DnsLookupWrapper dnsLookupWrapper;
    private ScheduledExecutorService scheduledExecutorService;
    private ServerConfiguration serverConfiguration;
    private KeyStore trustStore;
    private HttpClient.Builder httpClientBuilder;
    private SingleHostHttpClientBuilder singleHostHttpClientBuilder;

    public static HttpClientPoolBuilder builder() {
        return new HttpClientPoolBuilder();
    }

    /**
     * Build an HttpClientPoolBuilder with all default properties and components with the given hostname
     *
     * @param hostname the hostname
     */
    public static HttpClientPoolBuilder defaultBuilder(final String hostname) {
        return new HttpClientPoolBuilder()
                .withDefaultServerConfiguration(hostname)
                .withDefaultDnsLookupWrapper()
                .withDefaultScheduledExecutorService();
    }

    public HttpClientPoolBuilder withDnsLookupWrapper(final DnsLookupWrapper dnsLookupWrapper) {
        this.dnsLookupWrapper = dnsLookupWrapper;
        return this;
    }

    /**
     * Adds a default DnsLookupWrapper instance
     */
    public HttpClientPoolBuilder withDefaultDnsLookupWrapper() {
        this.dnsLookupWrapper = new DnsLookupWrapper();
        return this;
    }

    public HttpClientPoolBuilder withScheduledExecutorService(final ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
        return this;
    }

    /**
     * Adds a default single-thread scheduled executor
     */
    public HttpClientPoolBuilder withDefaultScheduledExecutorService() {
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        return this;
    }

    public HttpClientPoolBuilder withServerConfiguration(final ServerConfiguration serverConfiguration) {
        this.serverConfiguration = serverConfiguration;
        return this;
    }

    /**
     * Adds a server configuration with default properties, but the hostname has to be specified
     *
     * @param hostname the hostname
     */
    public HttpClientPoolBuilder withDefaultServerConfiguration(final String hostname) {
        this.serverConfiguration = new ServerConfiguration(hostname);
        return this;
    }

    public HttpClientPoolBuilder withTrustStore(final KeyStore trustStore) {
        this.trustStore = trustStore;
        return this;
    }

    public HttpClientPoolBuilder withHttpClientBuilder(final HttpClient.Builder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
        return this;
    }

    public HttpClientPoolBuilder withSingleHostHttpClientBuilder(final SingleHostHttpClientBuilder singleHostHttpClientBuilder) {
        this.singleHostHttpClientBuilder = singleHostHttpClientBuilder;
        return this;
    }

    @SuppressWarnings("AccessStaticViaInstance")
    public HttpClientPool build() {
        if (dnsLookupWrapper == null) {
            dnsLookupWrapper = new DnsLookupWrapper();
        }
        if (scheduledExecutorService == null) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        }
        Objects.requireNonNull(serverConfiguration, "ServerConfiguration is not specified. Use default one or specify your one.");

        final HttpClient singleHostnameClient;
        if (singleHostHttpClientBuilder != null) {
            if (trustStore == null && httpClientBuilder == null) {
                singleHostnameClient = singleHostHttpClientBuilder.build(serverConfiguration.getHostname());
            } else if (trustStore != null && httpClientBuilder == null) {
                singleHostnameClient = singleHostHttpClientBuilder.build(serverConfiguration.getHostname(), trustStore);
            } else if (trustStore != null) {
                singleHostnameClient = singleHostHttpClientBuilder.build(serverConfiguration.getHostname(), trustStore, httpClientBuilder);
            } else {
                singleHostnameClient = singleHostHttpClientBuilder.build(serverConfiguration.getHostname(), httpClientBuilder);
            }
        } else {
            if (trustStore == null && httpClientBuilder == null) {
                singleHostnameClient = SingleHostHttpClientBuilder.build(serverConfiguration.getHostname());
            } else if (trustStore != null && httpClientBuilder == null) {
                singleHostnameClient = SingleHostHttpClientBuilder.build(serverConfiguration.getHostname(), trustStore);
            } else if (trustStore != null) {
                singleHostnameClient = SingleHostHttpClientBuilder.build(serverConfiguration.getHostname(), trustStore, httpClientBuilder);
            } else {
                singleHostnameClient = SingleHostHttpClientBuilder.build(serverConfiguration.getHostname(), httpClientBuilder);
            }
        }

        return new HttpClientPool(
                dnsLookupWrapper,
                scheduledExecutorService,
                serverConfiguration,
                singleHostnameClient
        );
    }
}
