package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class HttpClientDisposerJava21IT {
    @Test
    void shouldReleaseTheClient() {
        assertThat(HttpClientDisposer.dispose(HttpClient.newHttpClient()), equalTo(true));
    }
}
