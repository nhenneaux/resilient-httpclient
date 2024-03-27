package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.DnsLookupWrapper;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.SingleHostHttpClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.nhenneaux.resilienthttpclient.monitoredclientpool.HttpClientPoolTest.PUBLIC_HOST_TO_TEST;
import static com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration.DEFAULT_REQUEST_TRANSFORMER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SingleIpHttpClientTest {
    @SuppressWarnings("unchecked")
    private static final Class<HttpResponse.BodyHandler<Void>> DISCARDING_BODY_HANDLER_CLASS = (Class<HttpResponse.BodyHandler<Void>>) HttpResponse.BodyHandlers.discarding().getClass();

    static {
        // Force init of the client without hostname check, otherwise it is cached
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");

        // Force init of the client without hostname check, otherwise it is cached
        SingleHostHttpClientBuilder.newHttpClient("test", InetAddress.getLoopbackAddress());

    }

    @Test
    void shouldBeHealthyWithOneRefresh() {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
        final InetAddress ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next();
        final HttpClient httpClient = SingleHostHttpClientBuilder.newHttpClient(hostname, ip);
        // When
        try (final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, ip, new ServerConfiguration(hostname))) {
            // Then
            assertSame(httpClient, singleIpHttpClient.getHttpClient());
            assertTrue(singleIpHttpClient.isHealthy());
            assertThat("failedResponseCount", singleIpHttpClient.getFailedResponseCount(), equalTo(0));
        }
    }

    @Test
    void shouldBeUnHealthyWith500Status() {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
        final HttpClient httpClient = mock(HttpClient.class);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        @SuppressWarnings("unchecked") final HttpResponse<Void> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpClient.sendAsync(captor.capture(), any(DISCARDING_BODY_HANDLER_CLASS))).thenReturn(CompletableFuture.completedFuture(httpResponse));
        // When
        try (final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next(), new ServerConfiguration(hostname))) {
            // Then
            assertSame(httpClient, singleIpHttpClient.getHttpClient());
            assertFalse(singleIpHttpClient.isHealthy());
            assertThat("failedResponseCount", singleIpHttpClient.getFailedResponseCount(), equalTo(2));
        }
    }

    @Test
    void shouldBeRefreshed() {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
        final HttpClient httpClient = mock(HttpClient.class);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        @SuppressWarnings("unchecked") final HttpResponse<Void> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpClient.sendAsync(captor.capture(), any(DISCARDING_BODY_HANDLER_CLASS))).thenReturn(CompletableFuture.completedFuture(httpResponse));

        ServerConfiguration serverConfiguration = new ServerConfiguration(hostname, 443, "/", 1, 1, -1, 1, DEFAULT_REQUEST_TRANSFORMER);

        // When
        try (final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next(), serverConfiguration)) {
            // Then
            assertFalse(singleIpHttpClient.isHealthy());
            assertTrue(singleIpHttpClient.shouldBeRefreshed());
            assertThat("failedResponseCount", singleIpHttpClient.getFailedResponseCount(), equalTo(2));
        }
    }

    @Test
    void shouldBeUnHealthyWith100Status() {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
        final HttpClient httpClient = mock(HttpClient.class);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        @SuppressWarnings("unchecked") final HttpResponse<Void> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(100);
        when(httpClient.sendAsync(captor.capture(), any(DISCARDING_BODY_HANDLER_CLASS))).thenReturn(CompletableFuture.completedFuture(httpResponse));
        // When
        try (final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next(), new ServerConfiguration(hostname))) {
            // Then
            assertSame(httpClient, singleIpHttpClient.getHttpClient());
            assertFalse(singleIpHttpClient.isHealthy());
            assertThat("failedResponseCount", singleIpHttpClient.getFailedResponseCount(), equalTo(2));
        }
    }


    @Test
    void shouldBeUnhealthyWithInvalidAddress() throws UnknownHostException {
        // Given
        // When
        final HttpClient httpClient = HttpClient.newHttpClient();
        try (final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, InetAddress.getLocalHost(), new ServerConfiguration("com.github.nhenneaux.resilienthttpclient.monitoredclientpool.SingleIpHttpClientTest.shouldCreateClientWithoutRefresh"))) {

            // Then
            assertSame(httpClient, singleIpHttpClient.getHttpClient());
            assertFalse(singleIpHttpClient.isHealthy());
            assertThat("failedResponseCount", singleIpHttpClient.getFailedResponseCount(), equalTo(2));
        }

    }

    @Test
    void shouldFailOnMalformedUrl() throws UnknownHostException {
        // Given
        final HttpClient httpClient = HttpClient.newHttpClient();
        // When - Then
        ServerConfiguration serverConfiguration = new ServerConfiguration("com.github.nhenneaux.resilienthttpclient.monitoredclientpool.SingleIpHttpClientTest.shouldCreateClientWithoutRefresh", -234, "&dfsfsd", 1, 1, -1, 0, DEFAULT_REQUEST_TRANSFORMER);
        InetAddress localHost = InetAddress.getLocalHost();
        final IllegalArgumentException illegalStateException = assertThrows(IllegalArgumentException.class, () -> new SingleIpHttpClient(httpClient, localHost, serverConfiguration));
        assertEquals("Cannot build health URI from ServerConfiguration{hostname='com.github.nhenneaux.resilienthttpclient.monitoredclientpool.SingleIpHttpClientTest.shouldCreateClientWithoutRefresh', port=-234, healthPath='&dfsfsd', healthCheckRequestBody='null', connectionHealthCheckPeriodInSeconds=1, dnsLookupRefreshPeriodInSeconds=1, healthReadTimeoutInMilliseconds=-1, failureResponseCountThreshold= 0}", illegalStateException.getMessage());
    }

    @Test
    void shouldCallCheckHealthStatusIfHealthyIsFalse() {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
        final HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked") final HttpResponse<Void> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpClient.sendAsync(any(HttpRequest.class), any(DISCARDING_BODY_HANDLER_CLASS))).thenReturn(CompletableFuture.completedFuture(httpResponse));
        // When
        try (final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next(), new ServerConfiguration(hostname))) {
            // Then
            assertSame(httpClient, singleIpHttpClient.getHttpClient());
            assertFalse(singleIpHttpClient.isHealthy());
            verify(httpClient, times(2)).sendAsync(any(), any());
        }
    }

    @Test
    void shouldntCallCheckHealthStatusIfHealthyIsTrue() {
        // Given
        final String hostname = PUBLIC_HOST_TO_TEST.get(0);
        final HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked") final HttpResponse<Void> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpClient.sendAsync(any(HttpRequest.class), any(DISCARDING_BODY_HANDLER_CLASS))).thenReturn(CompletableFuture.completedFuture(httpResponse));
        // When
        try (final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).iterator().next(), new ServerConfiguration(hostname))) {
            // Then
            assertSame(httpClient, singleIpHttpClient.getHttpClient());
            assertTrue(singleIpHttpClient.isHealthy());
            verify(httpClient, times(1)).sendAsync(any(), any());
            assertThat("failedResponseCount", singleIpHttpClient.getFailedResponseCount(), equalTo(0));
        }
    }

    @ParameterizedTest
    @MethodSource("getArgumentsForHealthCheckPostRequest")
    void shouldBeHealthyWithPostRequest(final Consumer<HttpRequest.Builder> givenRequestTransformer) {
        // Given
        final String hostname = "csp.credomatic.com";
        final InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(hostname);
            final HttpClient httpClient = SingleHostHttpClientBuilder.newHttpClient(hostname, inetAddress);
            // When
            final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, inetAddress, new ServerConfiguration(hostname, 443, "/", 1, 1, -1, 1, givenRequestTransformer));
            // Then
            assertSame(httpClient, singleIpHttpClient.getHttpClient());
            assertEquals(Boolean.TRUE, singleIpHttpClient.isHealthy());
            assertThat("failedResponseCount", singleIpHttpClient.getFailedResponseCount(), equalTo(0));
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<Arguments> getArgumentsForHealthCheckPostRequest() {
        return Stream.of(
                Arguments.of(getRequestTransformer()),
                Arguments.of((Object) null)
        );
    }

    static Consumer<HttpRequest.Builder> getRequestTransformer() {
        return builder -> builder.POST(HttpRequest.BodyPublishers.ofString("{\"request\":{\"transactionType\":\"ECHO_TEST\"}}"));
    }
}