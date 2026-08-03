package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.DnsLookupWrapper;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration.DEFAULT_REQUEST_TRANSFORMER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A DNS answer is capped and rotates, so an address can be missing from consecutive answers while its
 * target is still there. Membership is therefore decided by the health check, not by the answer.
 *
 * <p>Refreshes are driven by hand: the scheduled executor is a mock that captures the DNS refresh task
 * instead of running it on a timer.
 */
class HttpClientPoolTruncatedDnsTest {

    private static final String HOSTNAME = "localhost";

    private final Map<InetAddress, AtomicInteger> clientsCreatedPerAddress = new ConcurrentHashMap<>();
    private final Set<InetAddress> unhealthyAddresses = ConcurrentHashMap.newKeySet();
    private final Map<InetAddress, ScheduledFuture<?>> healthChecksPerAddress = new ConcurrentHashMap<>();
    private final AtomicReference<InetAddress> addressBeingCreated = new AtomicReference<>();
    private final AtomicReference<Set<InetAddress>> lookupAnswer = new AtomicReference<>();
    private final AtomicReference<Runnable> dnsRefresh = new AtomicReference<>();
    private final Map<InetAddress, List<Integer>> healthStatuses = new ConcurrentHashMap<>();
    private final Map<InetAddress, Integer> addressesServedWhenClosed = new ConcurrentHashMap<>();
    private final AtomicReference<HttpClientPool> poolUnderTest = new AtomicReference<>();

    @Test
    void shouldKeepAHealthyClientMissingFromTheAnswer() throws UnknownHostException {
        final InetAddress rotatingOut = address("127.0.0.3");
        lookupAnswer.set(addresses("127.0.0.1", "127.0.0.2", "127.0.0.3"));
        try (HttpClientPool pool = pool()) {
            final ScheduledFuture<?> healthCheck = healthChecksPerAddress.get(rotatingOut);

            lookupAnswer.set(addresses("127.0.0.1", "127.0.0.2"));
            refresh();

            verify(healthCheck, never()).cancel(true);
            assertEquals(3, distinctAddressesServed(pool), "the absent client should still take its turn");

            lookupAnswer.set(addresses("127.0.0.1", "127.0.0.2", "127.0.0.3"));
            refresh();

            assertEquals(1, clientsCreated(rotatingOut), "the client should have been kept, not rebuilt");
        }
    }

    @Test
    void shouldKeepAHealthyClientMissingFromEveryFurtherAnswer() throws UnknownHostException {
        lookupAnswer.set(addresses("127.0.0.1", "127.0.0.2", "127.0.0.3"));
        try (HttpClientPool pool = pool()) {
            lookupAnswer.set(addresses("127.0.0.1", "127.0.0.2"));
            refresh();
            refresh();
            refresh();

            assertEquals(1, clientsCreated(address("127.0.0.3")));
            assertEquals(3, distinctAddressesServed(pool), "health decides membership, not the answer");
        }
    }

    @Test
    void shouldCloseAnAbsentClientThatIsNotHealthy() throws UnknownHostException {
        final InetAddress unhealthy = address("127.0.0.3");
        unhealthyAddresses.add(unhealthy);
        lookupAnswer.set(addresses("127.0.0.1", "127.0.0.2", "127.0.0.3"));
        try (HttpClientPool pool = pool()) {
            final ScheduledFuture<?> healthCheck = healthChecksPerAddress.get(unhealthy);

            lookupAnswer.set(addresses("127.0.0.1", "127.0.0.2"));
            refresh();

            verify(healthCheck).cancel(true);
            assertEquals(2, distinctAddressesServed(pool));

            lookupAnswer.set(addresses("127.0.0.1", "127.0.0.2", "127.0.0.3"));
            refresh();

            assertEquals(2, clientsCreated(unhealthy), "an unhealthy client should have been closed");
        }
    }

    @Test
    void shouldPublishTheNewPoolBeforeClosingARemovedClient() throws UnknownHostException {
        final InetAddress overThreshold = address("127.0.0.3");
        healthStatuses.put(overThreshold, List.of(500, 200));
        lookupAnswer.set(addresses("127.0.0.1", "127.0.0.2", "127.0.0.3"));
        try (HttpClientPool pool = pool(1)) {
            // the first health check failed, the next succeeds, so it ends up healthy but over the threshold
            assertEquals(3, distinctAddressesServed(pool));

            lookupAnswer.set(addresses("127.0.0.1", "127.0.0.2"));
            refresh();

            verify(healthChecksPerAddress.get(overThreshold)).cancel(true);
            assertEquals(2, addressesServedWhenClosed.get(overThreshold), "the new pool must already be published when a removed client is closed");
        }
    }

    @Test
    void shouldCloseAHealthyClientMissingFromTheAnswerWhenNotConfiguredToKeepIt() throws UnknownHostException {
        final InetAddress rotatingOut = address("127.0.0.3");
        lookupAnswer.set(addresses("127.0.0.1", "127.0.0.2", "127.0.0.3"));
        try (HttpClientPool pool = pool(-1, false)) {
            final ScheduledFuture<?> healthCheck = healthChecksPerAddress.get(rotatingOut);

            lookupAnswer.set(addresses("127.0.0.1", "127.0.0.2"));
            refresh();

            verify(healthCheck).cancel(true);
            assertEquals(2, distinctAddressesServed(pool), "the default is to close a client that left the lookup");
        }
    }

    private HttpClientPool pool() {
        return pool(-1);
    }

    private HttpClientPool pool(int failureResponseCountThreshold) {
        return pool(failureResponseCountThreshold, true);
    }

    private HttpClientPool pool(int failureResponseCountThreshold, boolean keepHealthyClientsAbsentFromDnsLookup) {
        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        when(dnsLookupWrapper.getInetAddressesByDnsLookUp(HOSTNAME)).thenAnswer(invocation -> lookupAnswer.get());

        final ServerConfiguration serverConfiguration = new ServerConfiguration(
                HOSTNAME, 80, "/health", 300, 30, 1_000, failureResponseCountThreshold, DEFAULT_REQUEST_TRANSFORMER, "http", keepHealthyClientsAbsentFromDnsLookup);

        final HttpClientPool pool = new HttpClientPool(dnsLookupWrapper, handDrivenScheduler(), serverConfiguration, this::createClient);
        poolUnderTest.set(pool);
        return pool;
    }

    /**
     * Runs a task submitted with a zero initial delay, as a real scheduler would, and captures the one
     * with a delay, which is the DNS refresh.
     */
    private ScheduledExecutorService handDrivenScheduler() {
        final ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        doAnswer(invocation -> {
            final Runnable task = invocation.getArgument(0);
            final ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
            if (invocation.<Long>getArgument(1) == 0L) {
                task.run();
                final InetAddress address = addressBeingCreated.get();
                healthChecksPerAddress.put(address, scheduledFuture);
                doAnswer(cancel -> recordAddressesServed(address)).when(scheduledFuture).cancel(true);
            } else {
                dnsRefresh.set(task);
            }
            return scheduledFuture;
        }).when(scheduledExecutorService).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
        return scheduledExecutorService;
    }

    /** Captures what the pool serves at the moment a client is closed, to pin the publish before close order. */
    private boolean recordAddressesServed(InetAddress closedAddress) {
        final HttpClientPool pool = poolUnderTest.get();
        if (pool != null) {
            addressesServedWhenClosed.put(closedAddress, distinctAddressesServed(pool));
        }
        return true;
    }

    private void refresh() {
        dnsRefresh.get().run();
    }

    private HttpClient createClient(InetAddress inetAddress) {
        addressBeingCreated.set(inetAddress);
        clientsCreatedPerAddress.computeIfAbsent(inetAddress, ignored -> new AtomicInteger()).incrementAndGet();
        final HttpClient httpClient = mock(HttpClient.class);
        final HttpResponse<Void> httpResponse = mock();
        final AtomicInteger healthChecks = new AtomicInteger();
        doAnswer(invocation -> statusFor(inetAddress, healthChecks.getAndIncrement())).when(httpResponse).statusCode();
        doReturn(CompletableFuture.completedFuture(httpResponse)).when(httpClient).sendAsync(any(), any());
        return httpClient;
    }

    private int statusFor(InetAddress inetAddress, int healthCheck) {
        final List<Integer> sequence = healthStatuses.get(inetAddress);
        if (sequence == null) {
            return unhealthyAddresses.contains(inetAddress) ? 500 : 200;
        }
        return sequence.get(Math.min(healthCheck, sequence.size() - 1));
    }

    private int clientsCreated(InetAddress inetAddress) {
        return clientsCreatedPerAddress.getOrDefault(inetAddress, new AtomicInteger()).get();
    }

    private static int distinctAddressesServed(HttpClientPool pool) {
        final Set<InetAddress> served = new LinkedHashSet<>();
        for (int i = 0; i < 20; i++) {
            pool.getNextHttpClient().ifPresent(client -> served.add(client.getInetAddress()));
        }
        return served.size();
    }

    private static Set<InetAddress> addresses(String... hostAddresses) throws UnknownHostException {
        final Set<InetAddress> inetAddresses = new LinkedHashSet<>();
        for (String hostAddress : hostAddresses) {
            inetAddresses.add(address(hostAddress));
        }
        return inetAddresses;
    }

    private static InetAddress address(String hostAddress) throws UnknownHostException {
        return InetAddress.getByName(hostAddress);
    }
}
