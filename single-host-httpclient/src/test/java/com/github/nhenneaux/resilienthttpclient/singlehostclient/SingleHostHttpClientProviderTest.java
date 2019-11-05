package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SingleHostHttpClientProviderTest {

    @Test
    void shouldBuildSingleIpHttpClientAndWorksWithPublicWebsite() {
        // Given
        final List<String> hosts = List.of("openjdk.java.net", "travis-ci.com", "github.com", "facebook.com");
        for (String hostname : hosts) {
            final String ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).get(0).getHostAddress();

            final HttpClient client = new SingleHostHttpClientProvider().buildSingleHostnameHttpClient(hostname);


            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + ip))
                    .build();


            // When
            final String response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .join();

            // Then
            assertNotNull(response);
        }


    }
}