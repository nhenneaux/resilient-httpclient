package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import java.net.http.HttpClient;

/**
 * Java 21 and above variant of {@code HttpClientDisposer}
 */
final class HttpClientDisposer {
    private HttpClientDisposer() {
    }

    /**
     * @return always {@code true}, the client is released
     */
    public static boolean dispose(HttpClient httpClient) {
        // Graceful and non-blocking. Not close(), which blocks until every exchange completes.
        httpClient.shutdown();
        return true;
    }
}
