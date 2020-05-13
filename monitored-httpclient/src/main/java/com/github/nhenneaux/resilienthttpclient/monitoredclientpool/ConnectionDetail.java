package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import java.net.InetAddress;
import java.net.URI;

public class ConnectionDetail {

    private final String hostname;
    private final InetAddress inetAddress;
    private final URI healthUri;
    private final boolean healthy;

    public ConnectionDetail(String hostname, InetAddress inetAddress, URI healthUri, boolean healthy) {
        this.hostname = hostname;
        this.inetAddress = inetAddress;
        this.healthUri = healthUri;
        this.healthy = healthy;
    }

    public String getHostname() {
        return hostname;
    }

    public InetAddress getInetAddress() {
        return inetAddress;
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
                ", inetAddress=" + inetAddress +
                ", healthUri=" + healthUri +
                ", healthy=" + healthy +
                '}';
    }
}
