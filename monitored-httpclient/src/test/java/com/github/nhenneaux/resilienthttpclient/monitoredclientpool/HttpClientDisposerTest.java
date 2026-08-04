package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Surefire's classpath is a directory, so {@code META-INF/versions/21} is never consulted and the
 * baseline variant is always the one loaded. The Java 21+ variant is a single {@code shutdown()}
 * call, compiled by the CI matrix on JDK 21, 25 and 26.
 */
class HttpClientDisposerTest {

    @Test
    void shouldRejectNullClient() {
        assertThrows(NullPointerException.class, () -> HttpClientDisposer.dispose(null));
    }

    @Test
    void baselineShouldReportTheClientWasNotReleased() {
        assertFalse(HttpClientDisposer.dispose(mock(HttpClient.class)));
    }

    @Test
    void closeShouldDisposeTheUnderlyingClient() throws UnknownHostException {
        // Given
        final HttpClient httpClient = healthyClient();
        final SingleIpHttpClient singleIpHttpClient = new SingleIpHttpClient(httpClient, InetAddress.getByName("127.0.0.1"), new ServerConfiguration("localhost"));

        try (MockedStatic<HttpClientDisposer> disposer = mockStatic(HttpClientDisposer.class)) {
            // When
            singleIpHttpClient.close();

            // Then
            disposer.verify(() -> HttpClientDisposer.dispose(httpClient));
        }
    }

    private static HttpClient healthyClient() {
        final HttpClient httpClient = mock(HttpClient.class);
        final HttpResponse<Void> httpResponse = mock();
        doReturn(200).when(httpResponse).statusCode();
        doReturn(CompletableFuture.completedFuture(httpResponse)).when(httpClient).sendAsync(any(), any());
        return httpClient;
    }
}
