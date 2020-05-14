package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import java.net.URI;

public class ConnectionDetail {

    private final String hostname;
    private final String hostAddress;
    private final URI healthUri;
    private final boolean healthy;

    public ConnectionDetail(String hostname, String hostAddress, URI healthUri, boolean healthy) {
        this.hostname = hostname;
        this.hostAddress = hostAddress;
        this.healthUri = healthUri;
        this.healthy = healthy;
    }

    public String getHostname() {
        return hostname;
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public URI getHealthUri() {
        return healthUri;
    }

    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public String toString() {
        return "ConnectionDetail{" +
                "hostname='" + hostname + '\'' +
                ", hostAddress=" + hostAddress +
                ", healthUri=" + healthUri +
                ", healthy=" + healthy +
                '}';
    }
}
