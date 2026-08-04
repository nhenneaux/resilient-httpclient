package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import java.net.http.HttpClient;
import java.util.Objects;

/**
 * Releases an {@link HttpClient} the pool no longer uses.
 *
 * <p>Before Java 21 {@link HttpClient} has no lifecycle method, so only the garbage collector can
 * reclaim it, and the client keeps its {@code SelectorManager} thread until then.
 */
final class HttpClientDisposer {
    private HttpClientDisposer() {
    }

    /**
     * @return whether the client was released, {@code false} below Java 21
     */
    public static boolean dispose(HttpClient httpClient) {
        Objects.requireNonNull(httpClient);
        // Nothing to call before Java 21.
        return false;
    }
}
