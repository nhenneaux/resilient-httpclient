package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.DnsLookupWrapper;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Security;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpClientPoolTest {

    @Test
    void getNextHttpClient() throws MalformedURLException, URISyntaxException {
        final List<String> hosts = List.of("openjdk.java.net", "en.wikipedia.org", "cloudflare.com", "facebook.com");
        for (String hostname : hosts) {
            final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
            try (final HttpClientPool httpClientPool = new HttpClientPool(new DnsLookupWrapper(), Executors.newSingleThreadScheduledExecutor(), serverConfiguration)) {
                await().pollDelay(1, TimeUnit.SECONDS).atMost(1, TimeUnit.MINUTES).until(() -> httpClientPool.getNextHttpClient().isPresent());

                final Optional<SingleIpHttpClient> nextHttpClient = httpClientPool.getNextHttpClient();
                final SingleIpHttpClient singleIpHttpClient = nextHttpClient.orElseThrow();
                final HttpClient httpClient = singleIpHttpClient.getHttpClient();
                final int statusCode = httpClient.sendAsync(HttpRequest.newBuilder()
                                .uri(new URL("https", singleIpHttpClient.getInetAddress().getHostAddress(), serverConfiguration.getPort(), serverConfiguration.getHealthPath()).toURI())
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                        .thenApply(HttpResponse::statusCode)
                        .join();
                assertThat(statusCode, allOf(Matchers.greaterThanOrEqualTo(200), Matchers.lessThanOrEqualTo(499)));
            }
        }
    }

    @Test
    void shouldReturnToString() {
        var hostname = "google.com";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        try (final HttpClientPool httpClientPool = new HttpClientPool(new DnsLookupWrapper(), Executors.newScheduledThreadPool(4), serverConfiguration)) {


            assertTrue(httpClientPool.getNextHttpClient().isEmpty());
            assertThat(httpClientPool.check().getDetails().toString(), containsString("SingleIpHttpClient{inetAddress=google.com"));
            assertEquals(HealthCheckResult.HealthStatus.ERROR, httpClientPool.check().getStatus());

            assertThat(httpClientPool.toString(),
                    allOf(containsString("SingleIpHttpClient{inetAddress=google.com"), containsString("HttpClientPool{httpClientsCache=GenericRoundRobinListWithHealthCheck{list=["), containsString("], position=-1}, serverConfiguration=ServerConfiguration{hostname='google.com', port=443, healthPath='', connectionHealthCheckPeriodInSeconds=30, dnsLookupRefreshPeriodInSeconds=300}}")));
        }
    }

    @Test
    void getNextHttpClientNotFound() {
        final String hostname = "not.found.host";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        final HttpClientPool httpClientPool = new HttpClientPool(dnsLookupWrapper, Executors.newSingleThreadScheduledExecutor(), serverConfiguration);

        assertTrue(httpClientPool.getNextHttpClient().isEmpty());
        final HealthCheckResult check = httpClientPool.check();
        assertEquals(List.of(), check.getDetails());
        assertEquals(HealthCheckResult.HealthStatus.ERROR, check.getStatus());
        assertEquals("HealthCheckResult{status=ERROR, details=[]}", check.toString());
        assertEquals("HttpClientPool{httpClientsCache=null, serverConfiguration=ServerConfiguration{hostname='not.found.host', port=443, healthPath='', connectionHealthCheckPeriodInSeconds=30, dnsLookupRefreshPeriodInSeconds=300}}", httpClientPool.toString());

    }

    @Test
    void check() {
        final List<String> hosts = List.of("openjdk.java.net", "en.wikipedia.org", "cloudflare.com", "facebook.com");
        for (String hostname : hosts) {
            final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
            try (final HttpClientPool httpClientPool = new HttpClientPool(new DnsLookupWrapper(), Executors.newSingleThreadScheduledExecutor(), serverConfiguration)) {
                await().pollDelay(1, TimeUnit.SECONDS).atMost(1, TimeUnit.MINUTES).until(
                        () -> {
                            final HealthCheckResult checkResult = httpClientPool.check();
                            return Set.of(HealthCheckResult.HealthStatus.OK, HealthCheckResult.HealthStatus.WARNING).contains(checkResult.getStatus());
                        }
                );
            }
        }
    }

    @Test
    void scheduleRefresh() {
        // Given
        final ServerConfiguration serverConfiguration = new ServerConfiguration("openjdk.java.net");
        final ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        final ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        when(scheduledExecutorService.scheduleAtFixedRate(any(Runnable.class), eq(serverConfiguration.getDnsLookupRefreshPeriodInSeconds()), eq(serverConfiguration.getDnsLookupRefreshPeriodInSeconds()), eq(TimeUnit.SECONDS))).thenAnswer(invocationOnMock -> {
            final Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return scheduledFuture;
        });
        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        // When
        try (final HttpClientPool ignored = new HttpClientPool(dnsLookupWrapper, scheduledExecutorService, serverConfiguration)) {
            // Then
            verify(dnsLookupWrapper, times(2)).getInetAddressesByDnsLookUp(serverConfiguration.getHostname());
        }

        verify(scheduledFuture).cancel(true);
    }

    @Test
    void keepPreviousListWhenNewLookupEmpty() throws UnknownHostException {
        // Given
        final String hostname = "openjdk.java.net";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        final ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        final ScheduledFuture<?> scheduledDnsRefreshFuture = mock(ScheduledFuture.class);
        when(scheduledExecutorService.scheduleAtFixedRate(any(Runnable.class), eq(serverConfiguration.getDnsLookupRefreshPeriodInSeconds()), eq(serverConfiguration.getDnsLookupRefreshPeriodInSeconds()), eq(TimeUnit.SECONDS))).thenAnswer(invocationOnMock -> {
            final Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return scheduledDnsRefreshFuture;
        });

        final ScheduledFuture<?> scheduledHealthSingleClientRefreshFuture = mock(ScheduledFuture.class);
        when(scheduledExecutorService.scheduleAtFixedRate(
                any(Runnable.class),
                eq(0L),
                eq(serverConfiguration.getConnectionHealthCheckPeriodInSeconds()),
                eq(TimeUnit.SECONDS)
        )).thenAnswer(invocationOnMock -> {
            final Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return scheduledHealthSingleClientRefreshFuture;
        });

        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        //noinspection unchecked
        when(dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname)).thenReturn(Set.of(InetAddress.getByName(hostname)), Set.of());
        // When
        try (final HttpClientPool ignored = new HttpClientPool(dnsLookupWrapper, scheduledExecutorService, serverConfiguration)) {
            // Then
            verify(dnsLookupWrapper, times(2)).getInetAddressesByDnsLookUp(serverConfiguration.getHostname());
        }

        verify(scheduledDnsRefreshFuture).cancel(true);
        verify(scheduledHealthSingleClientRefreshFuture).cancel(true);
    }


    @Test
    void updatePreviousListWhenNewLookupResult() throws UnknownHostException {
        // Given
        final String hostname = "openjdk.java.net";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        final ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        final ScheduledFuture<?> scheduledDnsRefreshFuture = mock(ScheduledFuture.class);
        when(scheduledExecutorService.scheduleAtFixedRate(any(Runnable.class), eq(serverConfiguration.getDnsLookupRefreshPeriodInSeconds()), eq(serverConfiguration.getDnsLookupRefreshPeriodInSeconds()), eq(TimeUnit.SECONDS))).thenAnswer(invocationOnMock -> {
            final Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return scheduledDnsRefreshFuture;
        });

        final ScheduledFuture<?> scheduledHealthSingleClientRefreshFuture = mock(ScheduledFuture.class);
        when(scheduledExecutorService.scheduleAtFixedRate(
                any(Runnable.class),
                eq(0L),
                eq(serverConfiguration.getConnectionHealthCheckPeriodInSeconds()),
                eq(TimeUnit.SECONDS)
        )).thenAnswer(invocationOnMock -> {
            final Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return scheduledHealthSingleClientRefreshFuture;
        });

        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        final InetAddress secondAddress = mock(InetAddress.class);
        when(secondAddress.getHostAddress()).thenReturn("10.0.0.255");
        final InetAddress firstAddress = InetAddress.getByName(hostname);
        //noinspection unchecked
        when(dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname)).thenReturn(Set.of(firstAddress), Set.of(secondAddress));
        // When
        try (final HttpClientPool ignored = new HttpClientPool(dnsLookupWrapper, scheduledExecutorService, serverConfiguration)) {
            // Then
            verify(dnsLookupWrapper, times(2)).getInetAddressesByDnsLookUp(serverConfiguration.getHostname());
            @SuppressWarnings("unused") final String hostAddress = verify(secondAddress, times(3)).getHostAddress();
            verify(scheduledHealthSingleClientRefreshFuture).cancel(true);
        }

        verify(scheduledDnsRefreshFuture).cancel(true);
    }

    @Test
    void updatePreviousListWhenNewLookupInvalid() throws UnknownHostException {
        // Given
        final String hostname = "openjdk.java.net";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        final ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        final ScheduledFuture<?> scheduledDnsRefreshFuture = mock(ScheduledFuture.class);
        when(scheduledExecutorService.scheduleAtFixedRate(any(Runnable.class), eq(serverConfiguration.getDnsLookupRefreshPeriodInSeconds()), eq(serverConfiguration.getDnsLookupRefreshPeriodInSeconds()), eq(TimeUnit.SECONDS))).thenAnswer(invocationOnMock -> {
            final Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return scheduledDnsRefreshFuture;
        });

        final ScheduledFuture<?> scheduledHealthSingleClientRefreshFuture = mock(ScheduledFuture.class);
        when(scheduledExecutorService.scheduleAtFixedRate(
                any(Runnable.class),
                eq(0L),
                eq(serverConfiguration.getConnectionHealthCheckPeriodInSeconds()),
                eq(TimeUnit.SECONDS)
        )).thenAnswer(invocationOnMock -> {
            final Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return scheduledHealthSingleClientRefreshFuture;
        });

        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        final InetAddress secondAddress = mock(InetAddress.class);
        final InetAddress firstAddress = InetAddress.getByName(hostname);
        //noinspection unchecked
        when(dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname)).thenReturn(Set.of(firstAddress), Set.of(secondAddress));
        // When
        try (final HttpClientPool ignored = new HttpClientPool(dnsLookupWrapper, scheduledExecutorService, serverConfiguration)) {
            fail();
        } catch (IllegalStateException expected) {
            assertEquals("Cannot build health URI from ServerConfiguration{hostname='openjdk.java.net', port=443, healthPath='', connectionHealthCheckPeriodInSeconds=30, dnsLookupRefreshPeriodInSeconds=300}", expected.getMessage());
        }
        // Then
        verify(dnsLookupWrapper, times(2)).getInetAddressesByDnsLookUp(serverConfiguration.getHostname());
        @SuppressWarnings("unused") final String hostAddress = verify(secondAddress, times(3)).getHostAddress();
    }

    @Test
    void warningHealthWhenOneHostDown() throws UnknownHostException {
        // Given
        final String hostname = "openjdk.java.net";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        final ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        final ScheduledFuture<?> scheduledDnsRefreshFuture = mock(ScheduledFuture.class);
        when(scheduledExecutorService.scheduleAtFixedRate(any(Runnable.class), eq(serverConfiguration.getDnsLookupRefreshPeriodInSeconds()), eq(serverConfiguration.getDnsLookupRefreshPeriodInSeconds()), eq(TimeUnit.SECONDS))).thenAnswer(invocationOnMock -> {
            final Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return scheduledDnsRefreshFuture;
        });

        final ScheduledFuture<?> scheduledHealthSingleClientRefreshFuture = mock(ScheduledFuture.class);
        when(scheduledExecutorService.scheduleAtFixedRate(
                any(Runnable.class),
                eq(0L),
                eq(serverConfiguration.getConnectionHealthCheckPeriodInSeconds()),
                eq(TimeUnit.SECONDS)
        )).thenAnswer(invocationOnMock -> {
            final Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return scheduledHealthSingleClientRefreshFuture;
        });

        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        final InetAddress secondAddress = mock(InetAddress.class);
        when(secondAddress.getHostAddress()).thenReturn("10.0.0.255");
        final InetAddress firstAddress = InetAddress.getByName(hostname);
        when(dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname)).thenReturn(Set.of(firstAddress, secondAddress));
        // When
        try (final HttpClientPool httpClientPool = new HttpClientPool(dnsLookupWrapper, scheduledExecutorService, serverConfiguration)) {
            // Then
            verify(dnsLookupWrapper, times(2)).getInetAddressesByDnsLookUp(serverConfiguration.getHostname());
            @SuppressWarnings("unused") final String hostAddress = verify(secondAddress, times(5)).getHostAddress();

            assertEquals(HealthCheckResult.HealthStatus.WARNING, httpClientPool.check().getStatus());
        }
        verify(scheduledHealthSingleClientRefreshFuture, times(2)).cancel(true);
        verify(scheduledDnsRefreshFuture).cancel(true);
    }

    @Test
    void validatePropertyMinus1() {
        final String key = "validatePropertyMinus1";
        Security.setProperty(key, "-1");
        assertFalse(HttpClientPool.validateProperty(key, 10));
    }

    @Test
    void validatePropertyLowerThanBound() {
        final String key = "validatePropertyLowerThanBound";
        Security.setProperty(key, "5");
        assertTrue(HttpClientPool.validateProperty(key, 10));
    }

    @Test
    void validatePropertyEmpty() {
        final String key = "validatePropertyEmpty";
        Security.setProperty(key, "");
        assertTrue(HttpClientPool.validateProperty(key, 10));
    }

    @Test
    void validatePropertyHigherThanBound() {
        final String key = "validatePropertyHigherThanBound";
        Security.setProperty(key, "11");
        assertFalse(HttpClientPool.validateProperty(key, 10));
    }
}