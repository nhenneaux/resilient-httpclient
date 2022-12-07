package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.DnsLookupWrapper;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.SingleHostHttpClientBuilder;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.Security;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpClientPoolTest {

    private static final Set<HealthCheckResult.HealthStatus> NOT_ERROR = Set.of(HealthCheckResult.HealthStatus.OK, HealthCheckResult.HealthStatus.WARNING);

    static {
        // Force properties
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Host");
    }

    @Test
    void getNextHttpClient() throws MalformedURLException, URISyntaxException {
        final List<String> hosts = List.of("openjdk.org", "github.com", "twitter.com", "cloudflare.com", "facebook.com", "amazon.com", "google.com", "travis-ci.com", "en.wikipedia.org");
        for (String hostname : hosts) {
            final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
            try (HttpClientPool httpClientPool = HttpClientPool.newHttpClientPool(serverConfiguration)) {
                await().pollDelay(1, TimeUnit.SECONDS).atMost(1, TimeUnit.MINUTES).until(httpClientPool::getNextHttpClient, Optional::isPresent);

                final Optional<SingleIpHttpClient> nextHttpClient = httpClientPool.getNextHttpClient();
                final SingleIpHttpClient singleIpHttpClient = nextHttpClient.orElseThrow();
                final HttpClient httpClient = singleIpHttpClient.getHttpClient();
                final int statusCode = httpClient.sendAsync(HttpRequest.newBuilder()
                                .uri(new URL("https", hostname, -1, serverConfiguration.getHealthPath()).toURI())
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                        .thenApply(HttpResponse::statusCode)
                        .join();
                assertThat(statusCode, allOf(Matchers.greaterThanOrEqualTo(200), Matchers.lessThanOrEqualTo(499)));
            }
        }
    }

    @Test
    void resilientClient() throws MalformedURLException, URISyntaxException {
        final List<String> hosts = List.of("openjdk.org", "github.com", "twitter.com", "cloudflare.com", "facebook.com", "amazon.com", "google.com", "travis-ci.com", "en.wikipedia.org");
        for (String hostname : hosts) {
            final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname, 443);
            try (HttpClientPool httpClientPool = HttpClientPool.newHttpClientPool(serverConfiguration)) {
                await().pollDelay(1, TimeUnit.SECONDS).atMost(1, TimeUnit.MINUTES).until(httpClientPool::getNextHttpClient, Optional::isPresent);

                final HttpClient httpClient = httpClientPool.resilientClient();
                final int statusCode = httpClient.sendAsync(HttpRequest.newBuilder()
                                .uri(new URL("https", serverConfiguration.getHostname(), serverConfiguration.getPort(), serverConfiguration.getHealthPath()).toURI())
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                        .thenApply(HttpResponse::statusCode)
                        .join();
                assertThat(statusCode, allOf(Matchers.greaterThanOrEqualTo(200), Matchers.lessThanOrEqualTo(499)));
            }
        }
    }

    @Test
    void shouldUseCustomSingleHostHttpClientBuilder() throws MalformedURLException, URISyntaxException {
        String hostname = "openjdk.org";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        try (HttpClientPool httpClientPool = HttpClientPool
                .builder(serverConfiguration)
                .withSingleHostHttpClient(inetAddress ->
                        SingleHostHttpClientBuilder
                                .builder(serverConfiguration.getHostname(), new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next(), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2L)))
                                .withTlsNameMatching()
                                .withSni()
                                .buildWithHostHeader()
                )
                .build()
        ) {
            await().pollDelay(1, TimeUnit.SECONDS).atMost(1, TimeUnit.MINUTES).until(() -> httpClientPool.getNextHttpClient().isPresent());

            final Optional<SingleIpHttpClient> nextHttpClient = httpClientPool.getNextHttpClient();
            final SingleIpHttpClient singleIpHttpClient = nextHttpClient.orElseThrow();
            final HttpClient httpClient = singleIpHttpClient.getHttpClient();
            final int statusCode = httpClient.sendAsync(HttpRequest.newBuilder()
                            .uri(new URL("https", hostname, serverConfiguration.getPort(), serverConfiguration.getHealthPath()).toURI())
                            .build(),
                    HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::statusCode)
                    .join();
            assertThat(statusCode, allOf(Matchers.greaterThanOrEqualTo(200), Matchers.lessThanOrEqualTo(499)));
        }
    }

    @Test
    void shouldUseNullTruststore() throws MalformedURLException, URISyntaxException {
        String hostname = "openjdk.org";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        try (HttpClientPool httpClientPool = HttpClientPool
                .builder(serverConfiguration)
                .withSingleHostHttpClient(inetAddress ->
                        SingleHostHttpClientBuilder
                                .builder(hostname, new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next(), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2L)))
                                .withTlsNameMatching((KeyStore) null)
                                .withSni()
                                .build()
                )
                .build()) {
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

    @Test
    void shouldReturnToString() {
        var hostname = "google.com";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        try (HttpClientPool httpClientPool = HttpClientPool.builder(serverConfiguration)
                .withScheduledExecutorService(Executors.newScheduledThreadPool(4))
                .build()
        ) {
            assertFalse(httpClientPool.getNextHttpClient().isEmpty());
            assertThat(httpClientPool.check().getDetails().toString(), stringContainsInOrder("[ConnectionDetail{hostname='google.com', hostAddress=", ", healthUri=https://", ", healthy=true}"));

            assertThat(httpClientPool.toString(),
                    allOf(containsString("SingleIpHttpClient{inetAddress=google.com"),
                            containsString("HttpClientPool{httpClientsCache=GenericRoundRobinListWithHealthCheck{list=["),
                            containsString("], position=0}, serverConfiguration=ServerConfiguration{hostname='google.com', port=-1, healthPath=''"),
                            containsString("connectionHealthCheckPeriodInSeconds=30, dnsLookupRefreshPeriodInSeconds=300, readTimeoutInMilliseconds=-1, failureResponseCountThreshold= -1}}")));
        }
    }

    @Test
    void getNextHttpClientNotFound() {
        final String hostname = "not.found.host";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        final HttpClientPool httpClientPool = HttpClientPool.builder(serverConfiguration).withDnsLookupWrapper(dnsLookupWrapper).build();

        assertTrue(httpClientPool.getNextHttpClient().isEmpty());
        final HealthCheckResult check = httpClientPool.check();
        assertEquals(List.of(), check.getDetails());
        assertEquals(HealthCheckResult.HealthStatus.ERROR, check.getStatus());
        assertEquals("HealthCheckResult{status=ERROR, details=[]}", check.toString());
        assertEquals("HttpClientPool{httpClientsCache=null, serverConfiguration=ServerConfiguration{hostname='not.found.host', port=-1, healthPath='', connectionHealthCheckPeriodInSeconds=30, dnsLookupRefreshPeriodInSeconds=300, readTimeoutInMilliseconds=-1, failureResponseCountThreshold= -1}}", httpClientPool.toString());

    }

    @Test
    void check() {
        final List<String> hosts = List.of("openjdk.org", "en.wikipedia.org", "cloudflare.com", "facebook.com");
        for (String hostname : hosts) {
            final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
            try (HttpClientPool httpClientPool = HttpClientPool.builder(serverConfiguration).build()) {
                await().pollDelay(1, TimeUnit.SECONDS)
                        .atMost(1, TimeUnit.MINUTES)
                        .until(
                                httpClientPool::check,
                                checkResult -> NOT_ERROR.contains(checkResult.getStatus())

                        );
            }
        }
    }

    @Test
    void checkInJson() throws JsonProcessingException {
        final ServerConfiguration serverConfiguration = new ServerConfiguration("openjdk.org");
        try (HttpClientPool httpClientPool = HttpClientPool.builder(serverConfiguration).build()) {
            final HealthCheckResult result = await()
                    .pollDelay(1, TimeUnit.SECONDS)
                    .atMost(1, TimeUnit.MINUTES).until(
                            httpClientPool::check,
                            checkResult -> NOT_ERROR.contains(checkResult.getStatus())

                    );
            assertThat(objectMapper().writeValueAsString(result), stringContainsInOrder("\"details\":[{\"hostname\":\"openjdk.org\",\"hostAddress\":\"", "\",\"healthUri\":\"", "\",\"healthy\":true}"));
        }
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }


    @Test
    void scheduleRefresh() {
        // Given
        final ServerConfiguration serverConfiguration = new ServerConfiguration("openjdk.org");
        final ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        final ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        when(scheduledExecutorService.scheduleAtFixedRate(any(Runnable.class), eq(serverConfiguration.getDnsLookupRefreshPeriodInSeconds()), eq(serverConfiguration.getDnsLookupRefreshPeriodInSeconds()), eq(TimeUnit.SECONDS))).thenAnswer(invocationOnMock -> {
            final Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return scheduledFuture;
        });
        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        // When
        try (HttpClientPool ignored = HttpClientPool.builder(serverConfiguration)
                .withDnsLookupWrapper(dnsLookupWrapper)
                .withScheduledExecutorService(scheduledExecutorService)
                .build()) {
            // Then
            verify(dnsLookupWrapper, times(2)).getInetAddressesByDnsLookUp(serverConfiguration.getHostname());
        }

        verify(scheduledFuture).cancel(true);
    }


    @Test
    void keepPreviousListWhenNewLookupEmpty() throws UnknownHostException {
        // Given
        final String hostname = "openjdk.org";
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

        mockDns(dnsLookupWrapper, InetAddress.getByName(hostname), Set.of());
        // When
        try (HttpClientPool ignored = HttpClientPool.builder(serverConfiguration)
                .withDnsLookupWrapper(dnsLookupWrapper)
                .withScheduledExecutorService(scheduledExecutorService)
                .build()) {
            // Then
            verify(dnsLookupWrapper, times(2)).getInetAddressesByDnsLookUp(serverConfiguration.getHostname());
        }

        verify(scheduledDnsRefreshFuture).cancel(true);
        verify(scheduledHealthSingleClientRefreshFuture).cancel(true);
    }

    @SuppressWarnings("unchecked")
    private void mockDns(DnsLookupWrapper dnsLookupWrapper, InetAddress byName, Set<InetAddress> of) {
        when(dnsLookupWrapper.getInetAddressesByDnsLookUp("openjdk.org")).thenReturn(Set.of(byName), of);
    }

    @Test
    void updatePreviousListWhenNewLookupResult() throws UnknownHostException {
        // Given
        final String hostname = "openjdk.org";
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
        final InetAddress secondAddress = getInetAddress();
        final InetAddress firstAddress = InetAddress.getByName(hostname);

        mockDns(dnsLookupWrapper, firstAddress, Set.of(secondAddress));
        // When
        try (HttpClientPool ignored = HttpClientPool.builder(serverConfiguration)
                .withDnsLookupWrapper(dnsLookupWrapper)
                .withScheduledExecutorService(scheduledExecutorService)
                .build()) {
            // Then
            verify(dnsLookupWrapper, times(2)).getInetAddressesByDnsLookUp(serverConfiguration.getHostname());
            verify(scheduledHealthSingleClientRefreshFuture).cancel(true);
        }

        verify(scheduledDnsRefreshFuture).cancel(true);
    }

    private static InetAddress getInetAddress() throws UnknownHostException {
        return InetAddress.getByAddress(new byte[]{10,0,0,127});
    }

    @Test
    void updatePreviousListWhenNewLookupInvalid() throws UnknownHostException {
        // Given
        final String hostname = "openjdk.org";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        final ScheduledExecutorService scheduledExecutorService = mockScheduledExecutorService(serverConfiguration);

        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        final InetAddress secondAddress = InetAddress.getLoopbackAddress();
        final InetAddress firstAddress = InetAddress.getByName(hostname);

        mockDns(dnsLookupWrapper, firstAddress, Set.of(secondAddress));
        // When
        HttpClientPool.builder(serverConfiguration)
                .withDnsLookupWrapper(dnsLookupWrapper)
                .withScheduledExecutorService(scheduledExecutorService)
                .build();
        // Then
        verify(dnsLookupWrapper, times(2)).getInetAddressesByDnsLookUp(serverConfiguration.getHostname());

    }

    @Test
    void warningHealthWhenOneHostDown() throws UnknownHostException {
        // Given
        final String hostname = "openjdk.org";
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
        final InetAddress secondAddress = getInetAddress();
        final InetAddress firstAddress = InetAddress.getByName(hostname);
        when(dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname)).thenReturn(Set.of(firstAddress, secondAddress));
        // When
        try (final HttpClientPool httpClientPool = new HttpClientPool(
                dnsLookupWrapper,
                scheduledExecutorService,
                serverConfiguration,
                inetAddress -> SingleHostHttpClientBuilder
                        .builder(hostname, inetAddress, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1L)))
                        .withTlsNameMatching((KeyStore) null)
                        .withSni()
                        .buildWithHostHeader())
        ) {
            // Then
            verify(dnsLookupWrapper, times(2)).getInetAddressesByDnsLookUp(serverConfiguration.getHostname());

            await()
                    .atMost(Duration.ofSeconds(5L))
                    .until(() -> httpClientPool.check().getStatus() == HealthCheckResult.HealthStatus.WARNING);

            assertEquals(HealthCheckResult.HealthStatus.WARNING, httpClientPool.check().getStatus());
        }
        verify(scheduledHealthSingleClientRefreshFuture, times(2)).cancel(true);
        verify(scheduledDnsRefreshFuture).cancel(true);
    }

    @Test
    void shouldUseDnsFailsafe() throws IOException, InterruptedException {
        // Given
        final String hostname = "openjdk.org";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        final ScheduledExecutorService scheduledExecutorService = mockScheduledExecutorService(serverConfiguration);

        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        final InetAddress firstAddress = getInetAddress();
        final InetAddress secondAddress = InetAddress.getByName(hostname);
        when(dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname)).thenReturn(new CopyOnWriteArraySet<>(Arrays.asList(firstAddress, secondAddress)));
        // When
        try (final HttpClientPool httpClientPool = new HttpClientPool(
                dnsLookupWrapper,
                scheduledExecutorService,
                serverConfiguration,
                inetAddress -> SingleHostHttpClientBuilder
                        .builder(hostname, inetAddress, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1L)))
                        .withTlsNameMatching((KeyStore) null)
                        .withSni()
                        .buildWithHostHeader())
        ) {
            // Then
            final HttpClient httpClient = httpClientPool.resilientClient();
            final HttpResponse<Void> httpResponse = httpClient.send(HttpRequest.newBuilder().GET().uri(URI.create("https://openjdk.org")).build(), HttpResponse.BodyHandlers.discarding());


            assertThat(httpResponse.statusCode(), allOf(Matchers.greaterThanOrEqualTo(200), Matchers.lessThanOrEqualTo(499)));

        }
    }

    @Test
    @Timeout(20)
    void shouldUseDnsFailsafeAsync() throws IOException {
        // Given
        final String hostname = "openjdk.org";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        final ScheduledExecutorService scheduledExecutorService = mockScheduledExecutorService(serverConfiguration);

        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        final InetAddress firstAddress = getInetAddress();
        final InetAddress secondAddress = InetAddress.getByName(hostname);
        when(dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname)).thenReturn(new CopyOnWriteArraySet<>(Arrays.asList(firstAddress, secondAddress)));
        // When
        try (final HttpClientPool httpClientPool = new HttpClientPool(
                dnsLookupWrapper,
                scheduledExecutorService,
                serverConfiguration,
                inetAddress -> SingleHostHttpClientBuilder
                        .builder(hostname, inetAddress, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1L)))
                        .withTlsNameMatching((KeyStore) null)
                        .withSni()
                        .buildWithHostHeader())
        ) {
            // Then
            final HttpClient httpClient = httpClientPool.resilientClient();
            final CompletableFuture<HttpResponse<Void>> httpResponseAsync = httpClient.sendAsync(HttpRequest.newBuilder().GET().uri(URI.create("https://openjdk.org")).build(), HttpResponse.BodyHandlers.discarding());

            final HttpResponse<Void> httpResponse = httpResponseAsync.join();
            assertThat(httpResponse.statusCode(), allOf(Matchers.greaterThanOrEqualTo(200), Matchers.lessThanOrEqualTo(499)));

        }
    }

    private ScheduledExecutorService mockScheduledExecutorService(ServerConfiguration serverConfiguration) {
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
        return scheduledExecutorService;
    }

    @Test
    @Timeout(20)
    void shouldConnectTimeout() {
        // Given
        final String hostname = "amazon.com";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        mockScheduledExecutorService(serverConfiguration);

        final DnsLookupWrapper dnsLookupWrapper = new DnsLookupWrapper();
        // When
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Set<InetAddress> addresses = dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname);
        @SuppressWarnings("unchecked") final Optional<SingleIpHttpClient>[] optionals = addresses.stream()
                .skip(1)
                .map(address -> createSingleClient(hostname, serverConfiguration, address))
                .toArray(Optional[]::new);
        final Optional<SingleIpHttpClient> firstSingleClient = createSingleClient(hostname, serverConfiguration, addresses.iterator().next());
        when(roundRobinPool.next()).thenReturn(firstSingleClient, optionals);
        final List<Optional<SingleIpHttpClient>> clients = new ArrayList<>(Arrays.asList(optionals));
        clients.add(firstSingleClient);
        when(roundRobinPool.getList()).thenReturn(clients.stream().map(Optional::get).collect(Collectors.toList()));

        // Then
        final HttpClient httpClient = new ResilientClient(() -> roundRobinPool);
        final CompletableFuture<HttpResponse<Void>> httpResponseAsync = httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create("https://" + hostname)).build(), HttpResponse.BodyHandlers.discarding());

        final CompletionException executionException = assertThrows(CompletionException.class, httpResponseAsync::join);
        assertEquals("Cannot connect to the server, the following address were tried without success " + addresses + ".", executionException.getCause().getMessage());

    }

    @Test
    @Timeout(20)
    void shouldConnectTimeoutSync() {
        // Given
        final String hostname = "amazon.com";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        mockScheduledExecutorService(serverConfiguration);

        final DnsLookupWrapper dnsLookupWrapper = new DnsLookupWrapper();
        // When
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Set<InetAddress> addresses = dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname);
        @SuppressWarnings("unchecked") final Optional<SingleIpHttpClient>[] optionals = addresses.stream()
                .skip(1)
                .map(address -> createSingleClient(hostname, serverConfiguration, address))
                .toArray(Optional[]::new);
        final Optional<SingleIpHttpClient> firstSingleClient = createSingleClient(hostname, serverConfiguration, addresses.iterator().next());
        when(roundRobinPool.next()).thenReturn(firstSingleClient, optionals);
        final List<Optional<SingleIpHttpClient>> clients = new ArrayList<>(Arrays.asList(optionals));
        clients.add(firstSingleClient);
        when(roundRobinPool.getList()).thenReturn(clients.stream().map(Optional::get).collect(Collectors.toList()));

        // Then
        final HttpClient httpClient = new ResilientClient(() -> roundRobinPool);

        final HttpConnectTimeoutException httpConnectTimeoutException = assertThrows(HttpConnectTimeoutException.class, () -> httpClient.send(HttpRequest.newBuilder().uri(URI.create("https://" + hostname)).build(), HttpResponse.BodyHandlers.discarding()));
        assertEquals("Cannot connect to the HTTP server, tried to connect to the following IP " + addresses + " to send the HTTP request https://amazon.com GET", httpConnectTimeoutException.getMessage());

    }

    @Test
    @Timeout(20)
    void shouldConnectTimeoutDuplicateAddressList() {
        // Given
        final String hostname = "amazon.com";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        mockScheduledExecutorService(serverConfiguration);

        final DnsLookupWrapper dnsLookupWrapper = new DnsLookupWrapper();
        // When
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Set<InetAddress> addresses = dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname);
        @SuppressWarnings("unchecked") final Optional<SingleIpHttpClient>[] optionals = addresses.stream()
                .skip(1)
                .map(address -> createSingleClient(hostname, serverConfiguration, address))
                .toArray(Optional[]::new);
        final Optional<SingleIpHttpClient> firstSingleClient = createSingleClient(hostname, serverConfiguration, addresses.iterator().next());
        final List<Optional<SingleIpHttpClient>> optionalList = Arrays.asList(optionals);
        final List<Optional<SingleIpHttpClient>> optionalsDuplicate = new ArrayList<>(optionalList);
        optionalsDuplicate.addAll(optionalList);
        @SuppressWarnings("unchecked") final Optional<SingleIpHttpClient>[] duplicateClientsArray = optionalsDuplicate.toArray(Optional[]::new);
        when(roundRobinPool.next()).thenReturn(firstSingleClient, duplicateClientsArray);
        final List<Optional<SingleIpHttpClient>> clients = new ArrayList<>(optionalList);
        clients.add(firstSingleClient);
        when(roundRobinPool.getList()).thenReturn(clients.stream().map(Optional::get).collect(Collectors.toList()));

        // Then
        final HttpClient httpClient = new ResilientClient(() -> roundRobinPool);
        final CompletableFuture<HttpResponse<Void>> httpResponseAsync = httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create("https://" + hostname)).build(), HttpResponse.BodyHandlers.discarding());

        final CompletionException executionException = assertThrows(CompletionException.class, httpResponseAsync::join);
        assertEquals("Cannot connect to the server, the following address were tried without success " + addresses + ".", executionException.getCause().getMessage());

    }

    @Test
    @Timeout(20)
    void shouldConnectTimeoutEmptyElement() {
        // Given
        final String hostname = "amazon.com";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        mockScheduledExecutorService(serverConfiguration);

        final DnsLookupWrapper dnsLookupWrapper = new DnsLookupWrapper();
        // When
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Set<InetAddress> addresses = dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname);
        @SuppressWarnings("unchecked") final Optional<SingleIpHttpClient>[] optionals = addresses.stream()
                .skip(1)
                .map(address -> createSingleClient(hostname, serverConfiguration, address))
                .toArray(Optional[]::new);
        final Optional<SingleIpHttpClient> firstSingleClient = createSingleClient(hostname, serverConfiguration, addresses.iterator().next());
        final List<Optional<SingleIpHttpClient>> optionalList = Arrays.asList(optionals);
        final List<Optional<SingleIpHttpClient>> optionalsDuplicate = new ArrayList<>(optionalList);
        optionalsDuplicate.add(Optional.empty());
        @SuppressWarnings("unchecked") final Optional<SingleIpHttpClient>[] clientsArrayWithEmpty = optionalsDuplicate.toArray(Optional[]::new);
        when(roundRobinPool.next()).thenReturn(firstSingleClient, clientsArrayWithEmpty);
        final List<Optional<SingleIpHttpClient>> clients = new ArrayList<>(optionalList);
        clients.add(firstSingleClient);
        when(roundRobinPool.getList()).thenReturn(clients.stream().map(Optional::get).collect(Collectors.toList()));

        // Then
        final HttpClient httpClient = new ResilientClient(() -> roundRobinPool);

        final HttpConnectTimeoutException httpConnectTimeoutException = assertThrows(HttpConnectTimeoutException.class, () -> httpClient.send(HttpRequest.newBuilder().uri(URI.create("https://" + hostname)).build(), HttpResponse.BodyHandlers.discarding()));
        assertEquals("Cannot connect to the HTTP server, tried to connect to the following IP " + addresses + " to send the HTTP request https://amazon.com GET", httpConnectTimeoutException.getMessage());

    }

    private Optional<SingleIpHttpClient> createSingleClient(String hostname, ServerConfiguration serverConfiguration, InetAddress address) {
        final SingleIpHttpClient singleIpHttpClient = spy(new SingleIpHttpClient(
                SingleHostHttpClientBuilder
                        .builder(hostname, address, HttpClient.newBuilder().connectTimeout(Duration.ofMillis(5L)))
                        .withTlsNameMatching((KeyStore) null)
                        .withSni()
                        .buildWithHostHeader(),
                address,
                serverConfiguration));
        when(singleIpHttpClient.isHealthy()).thenReturn(Boolean.TRUE);
        return Optional.of(singleIpHttpClient);
    }


    @Test
    @Timeout(20)
    void shouldUseDnsFailsafeAsyncWithPushPromise() throws IOException {
        // Given
        final String hostname = "openjdk.org";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        final ScheduledExecutorService scheduledExecutorService = mockScheduledExecutorService(serverConfiguration);

        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        final InetAddress firstAddress = getInetAddress();
        final InetAddress secondAddress = InetAddress.getByName(hostname);
        when(dnsLookupWrapper.getInetAddressesByDnsLookUp(hostname)).thenReturn(new CopyOnWriteArraySet<>(Arrays.asList(firstAddress, secondAddress)));
        // When
        try (final HttpClientPool httpClientPool = new HttpClientPool(
                dnsLookupWrapper,
                scheduledExecutorService,
                serverConfiguration,
                inetAddress -> SingleHostHttpClientBuilder
                        .builder(hostname, inetAddress, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1L)))
                        .withTlsNameMatching((KeyStore) null)
                        .withSni()
                        .buildWithHostHeader())
        ) {
            // Then
            final HttpClient httpClient = httpClientPool.resilientClient();
            final HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();
            final CompletableFuture<HttpResponse<Void>> httpResponseAsync = httpClient.sendAsync(HttpRequest.newBuilder().GET().uri(URI.create("https://openjdk.org")).build(), bodyHandler, HttpResponse.PushPromiseHandler.of(request -> bodyHandler, new ConcurrentHashMap<>()));

            final HttpResponse<Void> httpResponse = httpResponseAsync.join();
            assertThat(httpResponse.statusCode(), allOf(Matchers.greaterThanOrEqualTo(200), Matchers.lessThanOrEqualTo(499)));
        }
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

    @Test
    void shouldNotStopListRefreshingInCaseOfRuntimeException() throws MalformedURLException, URISyntaxException {
        final ServerConfiguration serverConfigurationMock = mock(ServerConfiguration.class);
        final ServerConfiguration serverConfiguration = new ServerConfiguration("openjdk.org");
        when(serverConfigurationMock.getHostname()).thenReturn("fake.hostname.xxx", "openjdk.org");
        when(serverConfigurationMock.getDnsLookupRefreshPeriodInSeconds()).thenReturn(1L);
        when(serverConfigurationMock.getConnectionHealthCheckPeriodInSeconds()).thenReturn(serverConfiguration.getConnectionHealthCheckPeriodInSeconds());
        when(serverConfigurationMock.getHealthPath()).thenReturn(serverConfiguration.getHealthPath());
        when(serverConfigurationMock.getPort()).thenReturn(serverConfiguration.getPort());
        when(serverConfigurationMock.getReadTimeoutInMilliseconds()).thenReturn(serverConfiguration.getReadTimeoutInMilliseconds());

        try (HttpClientPool httpClientPool = HttpClientPool.newHttpClientPool(serverConfigurationMock)) {
            await().pollDelay(10, TimeUnit.SECONDS).atMost(1, TimeUnit.MINUTES).until(() -> httpClientPool.getNextHttpClient().isPresent());

            final Optional<SingleIpHttpClient> nextHttpClient = httpClientPool.getNextHttpClient();
            final SingleIpHttpClient singleIpHttpClient = nextHttpClient.orElseThrow();
            final HttpClient httpClient = singleIpHttpClient.getHttpClient();
            final int statusCode = httpClient.sendAsync(HttpRequest.newBuilder()
                            .uri(new URL("https", singleIpHttpClient.getInetAddress().getHostAddress(), serverConfigurationMock.getPort(), serverConfigurationMock.getHealthPath()).toURI())
                            .build(),
                    HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::statusCode)
                    .join();
            assertThat(statusCode, allOf(Matchers.greaterThanOrEqualTo(200), Matchers.lessThanOrEqualTo(499)));
        }
    }

    @Test
    void readme() {
        HttpClientPool singleInstanceByHost = HttpClientPool.newHttpClientPool(
                new ServerConfiguration("openjdk.org"));
        HttpClient resilientClient = singleInstanceByHost.resilientClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://openjdk.org/"))
                .build();
        final String response = resilientClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .join();

        assertNotNull(response);
    }

    @Test
    void shouldUpdateToFailedCountForHealthChecksFailed() {
        final List<String> hosts = List.of("openjdk.org", "en.wikipedia.org");
        for (String hostname : hosts) {
            final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
            try (HttpClientPool httpClientPool = HttpClientPool.builder(serverConfiguration).build()) {
                await().pollDelay(1, TimeUnit.SECONDS)
                        .atMost(1, TimeUnit.MINUTES)
                        .until(httpClientPool::check, checkResult -> NOT_ERROR.contains(checkResult.getStatus()));
                // ipv4 health checks have no failure, unlike ipv6
                for (final SingleIpHttpClient singleIpHttpClient : httpClientPool.getHttpClientsCache().get().getList()) {
                    final int failedResponseCount = singleIpHttpClient.getFailedResponseCount();

                    if (singleIpHttpClient.getInetAddress() instanceof Inet4Address) {
                        assertThat("failedResponseCount", failedResponseCount, equalTo(0));
                    } else {
                        assertThat("failedResponseCount", failedResponseCount, greaterThan(0));
                    }
                }
            }
        }
    }

    @Test
    void shouldDecommissionIfCouldNotFulfillFailedResponseCountThresholdRequirement() throws IOException, URISyntaxException, InterruptedException {
        // Given
        final String hostname = "postman-echo.com";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname, -1, "", 1, 1, -1, 0);

        // When
        try (HttpClientPool httpClientPool = HttpClientPool.builder(serverConfiguration).build()) {
            await().atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> !httpClientPool.getHttpClientsCache().get().getList().isEmpty());

            final Set<SingleIpHttpClient> initialSingleIpHttpClients = collectClients(httpClientPool);

            // send a request to have a client with failed response count of 1
            httpClientPool
                    .resilientClient()
                    .send(
                            HttpRequest.newBuilder()
                                    .uri(new URL("http", hostname, -1, "/status/500").toURI())
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.discarding()
                    );

            await().atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> collectClients(httpClientPool).stream().anyMatch(singleIpHttpClient -> !initialSingleIpHttpClients.contains(singleIpHttpClient)));
        }
    }

    private Set<SingleIpHttpClient> collectClients(final HttpClientPool httpClientPool) {
        return new HashSet<>(httpClientPool.getHttpClientsCache().get().getList());
    }
}