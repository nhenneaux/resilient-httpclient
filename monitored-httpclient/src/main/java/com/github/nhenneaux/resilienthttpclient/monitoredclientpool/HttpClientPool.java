package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singleipclient.DnsLookupWrapper;
import com.github.nhenneaux.resilienthttpclient.singleipclient.HttpClientForSpecificIpFactory;
import com.github.nhenneaux.resilienthttpclient.singleipclient.ServerConfiguration;

import java.net.InetAddress;
import java.net.http.HttpClient;
import java.security.Security;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A HTTP clients pool which keeps internally a round robin list of HTTP clients.<br>
 * Each HTTP client represents a connection to the acquirer using a distinct IP address, taken from endpoint resolving.<br>
 * The number of distinct HTTP clients in this connection pool is equal to the number of different IP addresses for the given acquirer hostname.
 */
@SuppressWarnings("WeakerAccess")
public class HttpClientPool {

    private static final Logger LOGGER = Logger.getLogger(HttpClientForSpecificIpFactory.class.getSimpleName());


    private final AtomicReference<GenericRoundRobinListWithHealthCheck<HttpClientWithHealth>> httpClientsCache;

    private final ServerConfiguration serverConfiguration;
    private final HttpClient singleHostnameClient;


    HttpClientPool(
            final DnsLookupWrapper dnsLookupWrapper,
            final ScheduledExecutorService scheduledExecutorService,
            final ServerConfiguration serverConfiguration
    ) {
        this.serverConfiguration = serverConfiguration;
        this.httpClientsCache = new AtomicReference<>();
        this.singleHostnameClient = new HttpClientForSpecificIpFactory().buildSingleHostnameHttpClient(serverConfiguration.getHostname());

        checkDnsCacheSecurityProperties();

        refreshTheList(dnsLookupWrapper, serverConfiguration);

        // We schedule a refresh of DNS lookup to catch this change
        // Existing HTTP clients for which InetAddress is still present in the list will be kept
        // HttpClients for which the ip has disappeared will be closed
        // For the new IPs new Http clients will be created
        final long dnsLookupRefreshPeriodInSeconds = serverConfiguration.getDnsLookupRefreshPeriodInSeconds();
        scheduledExecutorService.scheduleAtFixedRate(
                () -> refreshTheList(dnsLookupWrapper, serverConfiguration),
                dnsLookupRefreshPeriodInSeconds,
                dnsLookupRefreshPeriodInSeconds,
                TimeUnit.SECONDS
        );
    }

    private void checkDnsCacheSecurityProperties() {
        final String networkAddressCacheTtl = Security.getProperty("networkaddress.cache.ttl");
        final String networkAddressCacheNegativeTtl = Security.getProperty("networkaddress.cache.negative.ttl");

        if (networkAddressCacheTtl != null && !networkAddressCacheTtl.isEmpty()
                && ("-1".equals(networkAddressCacheTtl) || Integer.parseInt(networkAddressCacheTtl) > 60)
        ) {
            // "-1" means cache forever
            // Default "networkaddress.cache.ttl" is 30 seconds
            LOGGER.log(Level.SEVERE, () -> "The JVM Security property 'networkaddress.cache.ttl' is set to '" + networkAddressCacheTtl + "' while a value greater than '60' is expected.");
        }
        if (networkAddressCacheNegativeTtl != null && !networkAddressCacheNegativeTtl.isEmpty()
                && ("-1".equals(networkAddressCacheNegativeTtl) || Integer.parseInt(networkAddressCacheNegativeTtl) > 11)
        ) {
            // "-1" means cache forever
            // Default "networkaddress.cache.negative.ttl" is 10 seconds
            LOGGER.log(Level.SEVERE, () -> "The JVM Security property 'networkaddress.cache.negative.ttl' is set to '" + networkAddressCacheNegativeTtl + "' while a value greater than '11' is expected.");
        }
    }

    private void refreshTheList(
            final DnsLookupWrapper dnsLookupWrapper,
            final ServerConfiguration serverConfiguration
    ) {
        final List<HttpClientWithHealth> oldListOfClients = Optional.ofNullable(httpClientsCache.get())
                .map(roundRobin -> httpClientsCache.get())
                .orElse(new GenericRoundRobinListWithHealthCheck<>(List.of()))
                .getList();

        final String hostname = serverConfiguration.getHostname();

        final List<InetAddress> inetAddressesByDnsLookUp = dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname);
        if (inetAddressesByDnsLookUp.isEmpty()) {
            LOGGER.log(Level.WARNING, "The DNS lookup has returned an empty list of IPs. Reusing the old list.");
            return;
        }

        // Close those clients whose inet address is not present any more
        oldListOfClients.forEach(oldClient -> {
            final boolean inetAddressIsNotPresent = inetAddressesByDnsLookUp.stream().filter(inetAddress -> inetAddress.getHostAddress().equals(oldClient.getInetAddress().getHostAddress()))
                    .findAny().isEmpty();
            if (inetAddressIsNotPresent) {
                LOGGER.log(Level.INFO, () -> "The IP " + oldClient.getInetAddress().getHostAddress() + " for hostname " + serverConfiguration.getHostname() + " is not present in the DNS resolution list any more, closing the HttpClient");
            }
        });

        httpClientsCache.set(new GenericRoundRobinListWithHealthCheck<>(
                inetAddressesByDnsLookUp
                        .stream()
                        .map(inetAddress -> useOldClientOrCreateNew(
                                singleHostnameClient,
                                inetAddress,
                                oldListOfClients
                        ))
                        .collect(Collectors.toUnmodifiableList())
        ));
    }

    private HttpClientWithHealth useOldClientOrCreateNew(
            final HttpClient httpClient,
            final InetAddress inetAddress,
            final List<HttpClientWithHealth> oldListOfClients
    ) {
        // Try to find the client with the same inetAddress in the old list and reuse it or build a new one
        return oldListOfClients.stream()
                .filter(oldClient -> oldClient.getInetAddress().getHostAddress().equals(inetAddress.getHostAddress()))
                .findAny()
                .orElseGet(() -> {
                    LOGGER.log(Level.INFO, () -> "New IP found: " + inetAddress.getHostAddress() + " for hostname " + serverConfiguration.getHostname() + ", creating a new HttpClient");
                    return new HttpClientWithHealth(
                            httpClient,
                            inetAddress,
                            serverConfiguration);
                });
    }


    /**
     * Take the next HTTP client from the pool.<br>
     * Please note that it uses a round robin internally. So once it reaches the end of the list it starts returning items from the beginning and so on.
     */
    public Optional<HttpClientWithHealth> getNextHttpClient() {
        return httpClientsCache.get().next();
    }

    /**
     * Returns status {@link HealthStatus#OK} if all httpClientsCache are healthy.<br>
     * Returns status {@link HealthStatus#ERROR} if all httpClientsCache are unhealthy.<br>
     * Returns status {@link HealthStatus#WARNING} if only some httpClientsCache are healthy.<br>
     */
    public HealthCheckResult check() {
        final List<HttpClientWithHealth> clients = httpClientsCache.get().getList();
        LOGGER.log(Level.FINE, () -> "Check HTTP clients pool for health connection(s): " + clients);
        final boolean allConnectionsAvailable = clients.stream().allMatch(HttpClientWithHealth::isHealthy);
        final boolean allConnectionsUnavailable = clients.stream().noneMatch(HttpClientWithHealth::isHealthy);

        final HealthStatus status;

        if (allConnectionsUnavailable) {
            status = HealthStatus.ERROR;
        } else if (allConnectionsAvailable) {
            status = HealthStatus.OK;
        } else {
            status = HealthStatus.WARNING;
        }
        LOGGER.log(Level.FINE, () -> "HTTP clients pool health is " + status);

        return new HealthCheckResult(status,
                clients
                        .stream()
                        .map(HttpClientWithHealth::toString)
                        .collect(Collectors.toList()));
    }

    @Override
    public String toString() {
        return "HttpClientPool{" +
                "httpClientsCache=" + httpClientsCache +
                ", serverConfiguration=" + serverConfiguration +
                ", singleHostnameClient=" + singleHostnameClient +
                '}';
    }
}
