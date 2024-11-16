package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.DnsLookupWrapper;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.SingleHostHttpClientBuilder;
import org.awaitility.core.ConditionFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
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

import static com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration.DEFAULT_REQUEST_TRANSFORMER;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
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

@SuppressWarnings("resource") // HttpClient and ExecutorService not closeable on Java 17
class HttpClientPoolTest {

    private static final Set<HealthCheckResult.HealthStatus> NOT_ERROR = Set.of(HealthCheckResult.HealthStatus.OK, HealthCheckResult.HealthStatus.WARNING);
    public static final List<String> PUBLIC_HOST_TO_TEST = List.of("nicolas.henneaux.io", "openjdk.org", "github.com", "twitter.com", "cloudflare.com", "facebook.com", "amazon.com", "en.wikipedia.org"
            //,"travis-ci.com","google.com" failing on Java22
    );

    static {
        // Force properties
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Host");
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        var testClass = testInfo.getTestClass().orElseThrow();
        var testMethod = testInfo.getTestMethod().orElseThrow();
        System.out.println(testClass.getSimpleName() + "::" + testMethod.getName() + " test has started.");
    }

    @AfterEach
    void tearDown(TestInfo testInfo) {
        var testClass = testInfo.getTestClass().orElseThrow();
        var testMethod = testInfo.getTestMethod().orElseThrow();
        System.out.println(testClass.getSimpleName() + "::" + testMethod.getName() + " test has finished.");
    }

    @Test @Timeout(61)
    void getNextHttpClient() throws MalformedURLException, URISyntaxException {
        for (String hostname : PUBLIC_HOST_TO_TEST) {
            final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
            try (HttpClientPool httpClientPool = HttpClientPool.newHttpClientPool(serverConfiguration)) {
                waitOneMinute(hostname)
                        .until(httpClientPool::getNextHttpClient, Optional::isPresent);

                final Optional<SingleIpHttpClient> nextHttpClient = httpClientPool.getNextHttpClient();
                final HttpClient httpClient;
                try (SingleIpHttpClient singleIpHttpClient = nextHttpClient.orElseThrow()) {
                    httpClient = singleIpHttpClient.getHttpClient();
                }
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

    @Test @Timeout(61)
    void resilientClient() throws MalformedURLException, URISyntaxException {
        for (String hostname : PUBLIC_HOST_TO_TEST) {
            final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname, 443);
            try (HttpClientPool httpClientPool = HttpClientPool.newHttpClientPool(serverConfiguration)) {
                waitOneMinute(hostname).until(httpClientPool::getNextHttpClient, Optional::isPresent);

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

    @Test @Timeout(61)
    void shouldUseCustomSingleHostHttpClientBuilder() throws MalformedURLException, URISyntaxException {
        String hostname = PUBLIC_HOST_TO_TEST.get(0);
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

    @Test @Timeout(61)
    void shouldUseNullTruststore() throws MalformedURLException, URISyntaxException {
        String hostname = PUBLIC_HOST_TO_TEST.get(0);
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

    @Test @Timeout(61)
    void shouldReturnToString() {
        var hostname = "nicolas.henneaux.io";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        try (HttpClientPool httpClientPool = HttpClientPool.builder(serverConfiguration)
                .withScheduledExecutorService(Executors.newScheduledThreadPool(4))
                .build()
        ) {
            waitOneMinute(hostname).until(() -> httpClientPool.getNextHttpClient().isPresent());
            assertFalse(httpClientPool.getNextHttpClient().isEmpty());
            assertThat(httpClientPool.check().getDetails().toString(), stringContainsInOrder("[ConnectionDetail{hostname='nicolas.henneaux.io', hostAddress=", ", healthUri=https://", ", healthy=true}"));

            assertThat(httpClientPool.toString(),
                    allOf(containsString("SingleIpHttpClient{inetAddress=nicolas.henneaux.io"),
                            containsString("HttpClientPool{httpClientsCache=GenericRoundRobinListWithHealthCheck{list=["),
                            containsString("serverConfiguration=ServerConfiguration{hostname='nicolas.henneaux.io', port=-1, healthPath=''"),
                            containsString("connectionHealthCheckPeriodInSeconds=30, dnsLookupRefreshPeriodInSeconds=300, healthReadTimeoutInMilliseconds=5000, failureResponseCountThreshold= -1}}")));
        }
    }

    @Test @Timeout(61)
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
        assertEquals("HttpClientPool{httpClientsCache=null, serverConfiguration=ServerConfiguration{hostname='not.found.host', port=-1, healthPath='', connectionHealthCheckPeriodInSeconds=30, dnsLookupRefreshPeriodInSeconds=300, healthReadTimeoutInMilliseconds=5000, failureResponseCountThreshold= -1}}", httpClientPool.toString());

    }

    @Test @Timeout(61)
    void check() {
        for (String hostname : PUBLIC_HOST_TO_TEST) {
            final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
            try (HttpClientPool httpClientPool = HttpClientPool.builder(serverConfiguration).build()) {
                waitOneMinute(hostname)
                        .until(
                                httpClientPool::check,
                                checkResult -> NOT_ERROR.contains(checkResult.getStatus())

                        );
            }
        }
    }

    private static ConditionFactory waitOneMinute(String hostname) {
        return await("waiting " + hostname + " being available")
                .atMost(1, TimeUnit.MINUTES);
    }

    @Test @Timeout(61)
    void checkInJson() throws JsonProcessingException {
        final ServerConfiguration serverConfiguration = new ServerConfiguration(PUBLIC_HOST_TO_TEST.get(0));
        try (HttpClientPool httpClientPool = HttpClientPool.builder(serverConfiguration).build()) {
            final HealthCheckResult result = await()
                    .pollDelay(1, TimeUnit.SECONDS)
                    .atMost(1, TimeUnit.MINUTES).until(
                            httpClientPool::check,
                            checkResult -> NOT_ERROR.contains(checkResult.getStatus())

                    );
            assertThat(objectMapper().writeValueAsString(result),
                    stringContainsInOrder("{\"status\":\"",
                            "\",\"details\":[{\"hostname\":\"nicolas.henneaux.io\",\"hostAddress\":\"",
                            "\",\"healthUri\":\"https://nicolas.henneaux.io\",\"healthy\":true}",
                            "]}"));
        }
    }

    private ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, false)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
                .serializationInclusion(JsonInclude.Include.NON_NULL).build();
    }


    @Test @Timeout(61)
    void scheduleRefresh() {
        // Given
        final ServerConfiguration serverConfiguration = new ServerConfiguration(PUBLIC_HOST_TO_TEST.get(0));
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


    @Test @Timeout(61)
    void keepPreviousListWhenNewLookupEmpty() throws UnknownHostException {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
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
        when(dnsLookupWrapper.getInetAddressesByDnsLookUp(PUBLIC_HOST_TO_TEST.get(0))).thenReturn(Set.of(byName), of);
    }

    @Test @Timeout(61)
    void updatePreviousListWhenNewLookupResult() throws UnknownHostException {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
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
        final InetAddress secondAddress = localInetAddress();
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

    private static InetAddress localInetAddress() throws UnknownHostException {
        return InetAddress.getByAddress(new byte[]{10, 0, 0, 127});
    }

    @Test @Timeout(61)
    void updatePreviousListWhenNewLookupInvalid() throws UnknownHostException {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
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

    @Test @Timeout(61)
    void warningHealthWhenOneHostDown() throws UnknownHostException {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
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
        final InetAddress secondAddress = localInetAddress();
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

    @Test @Timeout(61)
    void shouldUseDnsFailsafe() throws IOException, InterruptedException {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        final ScheduledExecutorService scheduledExecutorService = mockScheduledExecutorService(serverConfiguration);

        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        final InetAddress firstAddress = localInetAddress();
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
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        final ScheduledExecutorService scheduledExecutorService = mockScheduledExecutorService(serverConfiguration);

        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        final InetAddress firstAddress = localInetAddress();
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
    void shouldConnectTimeout() throws UnknownHostException {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        mockScheduledExecutorService(serverConfiguration);

        // When
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Set<InetAddress> addresses = mockForConnectTimeout(hostname, serverConfiguration, roundRobinPool);

        // Then
        final HttpClient httpClient = new ResilientClient(() -> roundRobinPool);
        final CompletableFuture<HttpResponse<Void>> httpResponseAsync = httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create("https://" + hostname)).build(), HttpResponse.BodyHandlers.discarding());

        final CompletionException executionException = assertThrows(CompletionException.class, httpResponseAsync::join);
        assertEquals("Cannot connect to the server, the following address were tried without success " + addresses + ".", executionException.getCause().getMessage());

    }

    @Test
    @Timeout(20)
    void shouldConnectTimeoutSync() throws UnknownHostException {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        mockScheduledExecutorService(serverConfiguration);

        // When
        final RoundRobinPool roundRobinPool = mock(RoundRobinPool.class);
        final Set<InetAddress> addresses = mockForConnectTimeout(hostname, serverConfiguration, roundRobinPool);
        HttpClient httpClient = new ResilientClient(() -> roundRobinPool);

        // Then
        final HttpConnectTimeoutException httpConnectTimeoutException = assertThrows(HttpConnectTimeoutException.class, () -> httpClient.send(HttpRequest.newBuilder().uri(URI.create("https://" + hostname)).build(), HttpResponse.BodyHandlers.discarding()));

        assertEquals("Cannot connect to the HTTP server, tried to connect to the following IP " + addresses + " to send the HTTP request https://" + hostname + " GET", httpConnectTimeoutException.getMessage());

    }

    private Set<InetAddress> mockForConnectTimeout(String hostname, ServerConfiguration serverConfiguration, RoundRobinPool roundRobinPool) throws UnknownHostException {
        final InetAddress nonRoutableAddress = InetAddress.getByName("10.255.255.1");

        final Optional<SingleIpHttpClient> firstSingleClient = createSingleClient(hostname, serverConfiguration, nonRoutableAddress);
        when(roundRobinPool.next()).thenReturn(firstSingleClient);

        when(roundRobinPool.getList()).thenReturn(List.of(firstSingleClient.orElseThrow()));
        return Set.of(nonRoutableAddress);
    }

    @Test
    @Timeout(20)
    void shouldConnectTimeoutDuplicateAddressList() {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(1);
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
        when(roundRobinPool.getList()).thenReturn(clients.stream().filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList()));

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
        final String hostname = PUBLIC_HOST_TO_TEST.get(1);
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
        final List<Optional<SingleIpHttpClient>> clientsWithEmpty = new ArrayList<>(optionalList);
        clientsWithEmpty.add(Optional.empty());
        @SuppressWarnings("unchecked") final Optional<SingleIpHttpClient>[] clientsArrayWithEmpty = clientsWithEmpty.toArray(Optional[]::new);
        when(roundRobinPool.next()).thenReturn(firstSingleClient, clientsArrayWithEmpty);
        final List<Optional<SingleIpHttpClient>> clients = new ArrayList<>(optionalList);
        clients.add(firstSingleClient);
        when(roundRobinPool.getList()).thenReturn(clients.stream().filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList()));

        // Then
        final HttpClient httpClient = new ResilientClient(() -> roundRobinPool);

        final HttpConnectTimeoutException httpConnectTimeoutException = assertThrows(HttpConnectTimeoutException.class,
                () -> httpClient.send(HttpRequest.newBuilder().uri(URI.create("https://" + hostname)).build(), HttpResponse.BodyHandlers.discarding()),
                () -> "Not throwing for addresses " + addresses);
        assertEquals("Cannot connect to the HTTP server, tried to connect to the following IP " + addresses + " to send the HTTP request https://" + hostname + " GET", httpConnectTimeoutException.getMessage());

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
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
        final ScheduledExecutorService scheduledExecutorService = mockScheduledExecutorService(serverConfiguration);

        final DnsLookupWrapper dnsLookupWrapper = mock(DnsLookupWrapper.class);
        final InetAddress firstAddress = localInetAddress();
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

    @Test @Timeout(61)
    void validatePropertyMinus1() {
        final String key = "validatePropertyMinus1";
        Security.setProperty(key, "-1");
        assertFalse(HttpClientPool.validateProperty(key, 10));
    }

    @Test @Timeout(61)
    void validatePropertyLowerThanBound() {
        final String key = "validatePropertyLowerThanBound";
        Security.setProperty(key, "5");
        assertTrue(HttpClientPool.validateProperty(key, 10));
    }

    @Test @Timeout(61)
    void validatePropertyEmpty() {
        final String key = "validatePropertyEmpty";
        Security.setProperty(key, "");
        assertTrue(HttpClientPool.validateProperty(key, 10));
    }

    @Test @Timeout(61)
    void validatePropertyHigherThanBound() {
        final String key = "validatePropertyHigherThanBound";
        Security.setProperty(key, "11");
        assertFalse(HttpClientPool.validateProperty(key, 10));
    }

    @Test
    @Timeout(120)
    void shouldNotStopListRefreshingInCaseOfRuntimeException() throws MalformedURLException, URISyntaxException {
        final ServerConfiguration serverConfigurationMock = mock(ServerConfiguration.class);
        final ServerConfiguration serverConfiguration = new ServerConfiguration(PUBLIC_HOST_TO_TEST.get(0));
        when(serverConfigurationMock.getHostname()).thenReturn("fake.hostname.xxx", PUBLIC_HOST_TO_TEST.get(0));
        when(serverConfigurationMock.getDnsLookupRefreshPeriodInSeconds()).thenReturn(1L);
        when(serverConfigurationMock.getConnectionHealthCheckPeriodInSeconds()).thenReturn(serverConfiguration.getConnectionHealthCheckPeriodInSeconds());
        when(serverConfigurationMock.getHealthPath()).thenReturn(serverConfiguration.getHealthPath());
        when(serverConfigurationMock.getPort()).thenReturn(serverConfiguration.getPort());
        when(serverConfigurationMock.getHealthReadTimeoutInMilliseconds()).thenReturn(serverConfiguration.getHealthReadTimeoutInMilliseconds());
        when(serverConfigurationMock.getRequestTransformer()).thenReturn(serverConfiguration.getRequestTransformer());

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

    @Test @Timeout(61)
    void readme() {
        HttpClientPool singleInstanceByHost = HttpClientPool.newHttpClientPool(
                new ServerConfiguration(PUBLIC_HOST_TO_TEST.get(0)));
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
    @Timeout(65)
    void shouldUpdateToFailedCountForHealthChecksFailed() {
        final List<String> hosts = List.of(PUBLIC_HOST_TO_TEST.get(1), "en.wikipedia.org");
        for (String hostname : hosts) {
            final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
            try (HttpClientPool httpClientPool = HttpClientPool.builder(serverConfiguration).build()) {
                await().pollDelay(1, TimeUnit.SECONDS)
                        .atMost(1, TimeUnit.MINUTES)
                        .until(httpClientPool::check, checkResult -> NOT_ERROR.contains(checkResult.getStatus()));
                for (final SingleIpHttpClient singleIpHttpClient : httpClientPool.getHttpClientsCache().get().getList()) {
                    final int failedResponseCount = singleIpHttpClient.getFailedResponseCount();

                    if (singleIpHttpClient.getInetAddress() instanceof Inet4Address) {
                        assertThat("failedResponseCount", failedResponseCount, equalTo(0));
                    } else {
                        assertThat("failedResponseCount", failedResponseCount, greaterThanOrEqualTo(0));
                    }
                }
            }
        }
    }

    @Test @Timeout(61)
    void shouldDecommissionIfCouldNotFulfillFailedResponseCountThresholdRequirement() throws IOException, URISyntaxException, InterruptedException {
        // Given
        final String hostname = "postman-echo.com";
        final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname, -1, "", 1, 1, -1, 0, DEFAULT_REQUEST_TRANSFORMER);

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