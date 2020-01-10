package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.DnsLookupWrapper;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.SingleHostHttpClientBuilder;

import java.net.InetAddress;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.Security;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

/**
 * A HTTP clients pool which keeps internally a round robin list of HTTP clients.<br>
 * Each HTTP client represents a connection to the acquirer using a distinct IP address, taken from endpoint resolving.<br>
 * The number of distinct HTTP clients in this connection pool is equal to the number of different IP addresses for the given acquirer hostname.
 */
public class HttpClientPool implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(HttpClientPool.class.getSimpleName());

    private final AtomicReference<RoundRobinPool> httpClientsCache;

    private final ServerConfiguration serverConfiguration;
    private final ScheduledFuture<?> scheduledFutureDnsRefresh;

    public HttpClientPool(
            final DnsLookupWrapper dnsLookupWrapper,
            final ScheduledExecutorService scheduledExecutorService,
            final ServerConfiguration serverConfiguration
    ) {
        this(dnsLookupWrapper, scheduledExecutorService, serverConfiguration, null);
    }

    public HttpClientPool(
            final DnsLookupWrapper dnsLookupWrapper,
            final ScheduledExecutorService scheduledExecutorService,
            final ServerConfiguration serverConfiguration,
            final KeyStore trustStore
    ) {
        this(dnsLookupWrapper, scheduledExecutorService, serverConfiguration, trustStore, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)));
    }

    public HttpClientPool(DnsLookupWrapper dnsLookupWrapper, ScheduledExecutorService scheduledExecutorService, ServerConfiguration serverConfiguration, KeyStore trustStore, HttpClient.Builder builder) {
        this.serverConfiguration = serverConfiguration;
        this.httpClientsCache = new AtomicReference<>();

        checkDnsCacheSecurityProperties();

        final HttpClient singleHostnameClient = SingleHostHttpClientBuilder.build(serverConfiguration.getHostname(), trustStore, builder);

        // We schedule a refresh of DNS lookup to catch this change
        // Existing HTTP clients for which InetAddress is still present in the list will be kept
        // HttpClients for which the ip has disappeared will be closed
        // For the new IPs new Http clients will be created
        final long dnsLookupRefreshPeriodInSeconds = serverConfiguration.getDnsLookupRefreshPeriodInSeconds();
        this.scheduledFutureDnsRefresh = scheduledExecutorService.scheduleAtFixedRate(
                () -> refreshTheList(dnsLookupWrapper, serverConfiguration, httpClientsCache, singleHostnameClient, scheduledExecutorService),
                dnsLookupRefreshPeriodInSeconds,
                dnsLookupRefreshPeriodInSeconds,
                TimeUnit.SECONDS
        );

        refreshTheList(dnsLookupWrapper, serverConfiguration, httpClientsCache, singleHostnameClient, scheduledExecutorService);
    }

    static boolean validateProperty(String propertyName, int minimumPropertyValueExpected) {
        final String propertyValue = Security.getProperty(propertyName);

        if (propertyValue != null && !propertyValue.isEmpty()
                && ("-1".equals(propertyValue) || Integer.parseInt(propertyValue) > minimumPropertyValueExpected)
        ) {
            LOGGER.log(Level.SEVERE, () -> "The JVM Security property '" + propertyName + "' is set to '" + propertyValue + "' while a value greater than '" + minimumPropertyValueExpected + "' is expected.");
            return false;
        }
        return true;
    }

    private static void refreshTheList(
            final DnsLookupWrapper dnsLookupWrapper,
            final ServerConfiguration serverConfiguration,
            final AtomicReference<RoundRobinPool> httpClientsCache,
            final HttpClient singleHostnameClient,
            final ScheduledExecutorService scheduledExecutorService
    ) {
        final List<SingleIpHttpClient> oldListOfClients = Optional.ofNullable(httpClientsCache.get())
                .map(roundRobin -> httpClientsCache.get())
                .orElse(RoundRobinPool.EMPTY)
                .getList();

        final String hostname = serverConfiguration.getHostname();

        final Set<InetAddress> updatedLookup = dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname);
        if (updatedLookup.isEmpty()) {
            if (oldListOfClients.isEmpty()) {
                LOGGER.log(Level.SEVERE, "The DNS lookup has returned an empty list of IPs. There is no client in the pool.");
            } else {
                LOGGER.log(Level.WARNING, "The DNS lookup has returned an empty list of IPs. Reusing the old list.");
            }
            return;
        }


        httpClientsCache.set(new RoundRobinPool(
                updatedLookup
                        .stream()
                        .map(inetAddress -> useOldClientOrCreateNew(
                                singleHostnameClient,
                                inetAddress,
                                oldListOfClients,
                                serverConfiguration,
                                scheduledExecutorService
                        ))
                        .collect(Collectors.toUnmodifiableList())
        ));

        // Close those clients whose inet address is not present anymore
        oldListOfClients.stream()
                .filter(not(client -> updatedLookup.contains(client.getInetAddress())))
                .forEach(oldClient -> {
                    LOGGER.log(Level.INFO, () -> "The IP " + oldClient.getInetAddress().getHostAddress() + " for hostname " + hostname + " is not present in the DNS resolution list any more, closing the HttpClient");
                    oldClient.close();
                });
    }

    private static SingleIpHttpClient useOldClientOrCreateNew(
            final HttpClient httpClient,
            final InetAddress inetAddress,
            final List<SingleIpHttpClient> oldListOfClients,
            final ServerConfiguration serverConfiguration,
            final ScheduledExecutorService scheduledExecutorService
    ) {
        // Try to find the client with the same inetAddress in the old list and reuse it or build a new one
        return oldListOfClients.stream()
                .filter(oldClient -> oldClient.getInetAddress().getHostAddress().equals(inetAddress.getHostAddress()))
                .findAny()
                .orElseGet(() -> {
                    LOGGER.log(Level.INFO, () -> "New IP found: " + inetAddress.getHostAddress() + " for hostname " + serverConfiguration.getHostname() + ", creating a new HttpClient");
                    return new SingleIpHttpClient(
                            httpClient,
                            inetAddress,
                            serverConfiguration,
                            scheduledExecutorService
                    );
                });
    }

    private void checkDnsCacheSecurityProperties() {
        // Default "networkaddress.cache.ttl" is 30 seconds, "-1" means cache forever
        validateProperty("networkaddress.cache.ttl", 60);
        // Default "networkaddress.cache.negative.ttl" is 10 seconds, "-1" means cache forever
        validateProperty("networkaddress.cache.negative.ttl", 11);
    }


    /**
     * Take the next HTTP client from the pool.<br>
     * Please note that it uses a round robin internally. So once it reaches the end of the list it starts returning items from the beginning and so on.
     */
    public Optional<SingleIpHttpClient> getNextHttpClient() {
        return client().next();
    }

    private RoundRobinPool client() {
        return Optional.ofNullable(httpClientsCache.get()).orElse(RoundRobinPool.EMPTY);
    }

    /**
     * Returns status {@link HealthCheckResult.HealthStatus#OK} if all httpClientsCache are healthy.<br>
     * Returns status {@link HealthCheckResult.HealthStatus#ERROR} if all httpClientsCache are unhealthy.<br>
     * Returns status {@link HealthCheckResult.HealthStatus#WARNING} if only some httpClientsCache are healthy.<br>
     */
    public HealthCheckResult check() {
        final List<SingleIpHttpClient> clients = client().getList();
        LOGGER.log(Level.FINE, () -> "Check HTTP clients pool for health connection(s): " + clients);
        final boolean allConnectionsAvailable = clients.stream().allMatch(SingleIpHttpClient::isHealthy);
        final boolean allConnectionsUnavailable = clients.stream().noneMatch(SingleIpHttpClient::isHealthy);

        final HealthCheckResult.HealthStatus status;

        if (allConnectionsUnavailable) {
            status = HealthCheckResult.HealthStatus.ERROR;
        } else if (allConnectionsAvailable) {
            status = HealthCheckResult.HealthStatus.OK;
        } else {
            status = HealthCheckResult.HealthStatus.WARNING;
        }
        LOGGER.log(Level.FINE, () -> "HTTP clients pool health is " + status);

        return new HealthCheckResult(status,
                clients
                        .stream()
                        .map(SingleIpHttpClient::toString)
                        .collect(Collectors.toList()));
    }

    @Override
    public String toString() {
        return "HttpClientPool{" +
                "httpClientsCache=" + httpClientsCache +
                ", serverConfiguration=" + serverConfiguration +
                '}';
    }

    @Override
    public void close() {
        scheduledFutureDnsRefresh.cancel(true);
        client().getList().forEach(SingleIpHttpClient::close);
    }
}
