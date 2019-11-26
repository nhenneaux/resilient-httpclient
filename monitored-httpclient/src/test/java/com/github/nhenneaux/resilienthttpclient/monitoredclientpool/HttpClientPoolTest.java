package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import com.github.nhenneaux.resilienthttpclient.singlehostclient.DnsLookupWrapper;
import com.github.nhenneaux.resilienthttpclient.singlehostclient.ServerConfiguration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpClientPoolTest {

    @Test
    void getNextHttpClient() throws MalformedURLException, URISyntaxException {
        final List<String> hosts = List.of("openjdk.java.net", "en.wikipedia.org", "cloudflare.com", "facebook.com");
        for (String hostname : hosts) {
            final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
            final HttpClientPool httpClientPool = new HttpClientPool(new DnsLookupWrapper(), Executors.newSingleThreadScheduledExecutor(), serverConfiguration);
            final Optional<SingleIpHttpClient> nextHttpClient = httpClientPool.getNextHttpClient();

            assertTrue(nextHttpClient.isPresent(), httpClientPool::toString);

            final SingleIpHttpClient singleIpHttpClient = nextHttpClient.orElseThrow();
            final HttpClient httpClient = singleIpHttpClient.getHttpClient();
            final int statusCode = httpClient.sendAsync(HttpRequest.newBuilder()
                            .uri(new URL("https", singleIpHttpClient.getInetAddress().getHostAddress(), serverConfiguration.getPort(), serverConfiguration.getHealthPath()).toURI())
                            .build(),
                    HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::statusCode)
                    .join();
            assertThat(statusCode, Matchers.allOf(Matchers.greaterThanOrEqualTo(200), Matchers.lessThanOrEqualTo(499)));
        }
    }


    @Test
    void check() {
        final List<String> hosts = List.of("openjdk.java.net", "en.wikipedia.org", "cloudflare.com", "facebook.com");
        for (String hostname : hosts) {
            final ServerConfiguration serverConfiguration = new ServerConfiguration(hostname);
            final HttpClientPool httpClientPool = new HttpClientPool(new DnsLookupWrapper(), Executors.newSingleThreadScheduledExecutor(), serverConfiguration);
            final HealthCheckResult checkResult = httpClientPool.check();
            assertThat(httpClientPool.toString(), checkResult.getStatus(), Matchers.oneOf(HealthCheckResult.HealthStatus.OK, HealthCheckResult.HealthStatus.WARNING));
        }
    }
}