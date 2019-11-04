package com.github.nhenneaux.resilienthttpclient.singleipclient;

import java.util.concurrent.TimeUnit;

public class ServerConfiguration {

    private static final long DEFAULT_ACQUIRER_DNS_LOOKUP_REFRESH_PERIOD_IN_SECONDS = TimeUnit.MINUTES.toSeconds(5);


    private final String hostname;
    private final int port;
    private final String healthPath;

    private final long dnsLookupRefreshPeriodInSeconds;


    public ServerConfiguration(String hostname) {
        this(hostname, 443, "", DEFAULT_ACQUIRER_DNS_LOOKUP_REFRESH_PERIOD_IN_SECONDS);
    }

    public ServerConfiguration(String hostname, int port, String healthPath, long dnsLookupRefreshPeriodInSeconds) {
        this.hostname = hostname;
        this.port = port;
        this.healthPath = healthPath;
        this.dnsLookupRefreshPeriodInSeconds = dnsLookupRefreshPeriodInSeconds;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public String getHealthPath() {
        return healthPath;
    }

    public long getDnsLookupRefreshPeriodInSeconds() {
        return dnsLookupRefreshPeriodInSeconds;
    }

    @Override
    public String toString() {
        return "ServerConfiguration{" +
                "hostname='" + hostname + '\'' +
                ", port=" + port +
                ", healthPath='" + healthPath + '\'' +
                ", dnsLookupRefreshPeriodInSeconds=" + dnsLookupRefreshPeriodInSeconds +
                '}';
    }
}
