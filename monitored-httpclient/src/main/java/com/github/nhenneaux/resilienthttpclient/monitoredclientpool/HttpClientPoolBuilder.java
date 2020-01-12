package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.DnsLookupWrapper;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.SingleHostHttpClientBuilder;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@SuppressWarnings("WeakerAccess") // Used outside library
public class HttpClientPoolBuilder {

    private final ServerConfiguration serverConfiguration;

    private DnsLookupWrapper dnsLookupWrapper;
    private ScheduledExecutorService scheduledExecutorService;
    private SingleHostHttpClientBuilder singleHostHttpClientBuilder;
    private HttpClient httpClient;

    private HttpClientPoolBuilder(final ServerConfiguration serverConfiguration) {
        this.serverConfiguration = serverConfiguration;
    }

    public static HttpClientPoolBuilder builder(final ServerConfiguration serverConfiguration) {
        return new HttpClientPoolBuilder(serverConfiguration);
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

    public HttpClientPoolBuilder withSingleHostHttpClientBuilder(final SingleHostHttpClientBuilder singleHostHttpClientBuilder) {
        this.singleHostHttpClientBuilder = singleHostHttpClientBuilder;
        return this;
    }

    public HttpClientPoolBuilder withHttpClient(final HttpClient httpClient) {
        this.httpClient = httpClient;
        return this;
    }

    public HttpClientPool build() {
        if (dnsLookupWrapper == null) {
            dnsLookupWrapper = new DnsLookupWrapper();
        }
        if (scheduledExecutorService == null) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        }

        if (httpClient == null) {
            if (singleHostHttpClientBuilder == null) {
                httpClient = SingleHostHttpClientBuilder.newHttpClient(serverConfiguration.getHostname());
            } else {
                httpClient = singleHostHttpClientBuilder.build();
            }
        }

        return new HttpClientPool(
                dnsLookupWrapper,
                scheduledExecutorService,
                serverConfiguration,
                httpClient
        );
    }
}
