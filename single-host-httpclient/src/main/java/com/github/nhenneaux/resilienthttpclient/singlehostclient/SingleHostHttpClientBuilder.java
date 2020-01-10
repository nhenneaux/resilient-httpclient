package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

import static com.github.nhenneaux.resilienthttpclient.singlehostclient.SingleHostHttpClientBuilder.RethrowGeneralSecurityException.handleGeneralSecurityException;


/**
 * Create an {@link HttpClient} to target a single host.
 * It validates the certificate to authenticate the server in TLS communication with this single name.
 * It can be used to target a single host using its IP address(es) instead of its hostname while keeping a high protection against Man-in-the-middle attack.
 * <p>
 * <code>-Djdk.internal.httpclient.disableHostnameVerification</code> is needed to use a custom TLS name matching based on the requested host instead of the one from the URL.
 * <p>
 * <code>-Djdk.httpclient.allowRestrictedHeaders=Host</code> is needed to customize the HTTP Host header.
 */
@SuppressWarnings({"unused"}) // To use outside the module
public class SingleHostHttpClientBuilder {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);

    private String hostname;
    private HttpClient.Builder httpClientBuilder;
    private KeyStore trustStore;
    private SSLContext initialSslContext;
    private Duration connectTimeout;
    private boolean withoutHostHeader;
    private boolean withoutSni;

    private SingleHostHttpClientBuilder(String hostname) {
        this.hostname = hostname;
    }

    public static SingleHostHttpClientBuilder builder(String hostname) {
        return new SingleHostHttpClientBuilder(hostname);
    }

    public SingleHostHttpClientBuilder withConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public SingleHostHttpClientBuilder withHttpClientBuilder(HttpClient.Builder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
        return this;
    }

    /**
     * It overrides the following elements of the builder
     * <ul>
     *     <li><code>java.net.http.HttpClient.Builder#sslContext(javax.net.ssl.SSLContext)</code> with a custom SSLContext using the given truststore disabling default name validation and using the given hostname</li>
     *     <li><code>java.net.http.HttpClient.Builder#sslParameters(javax.net.ssl.SSLParameters)</code> to force the SNI server name expected</li>
     * </ul>
     */
    public SingleHostHttpClientBuilder withTrustStore(KeyStore trustStore) {
        this.trustStore = trustStore;
        return this;
    }

    public SingleHostHttpClientBuilder withInitialSslContext(SSLContext initialSslContext) {
        this.initialSslContext = initialSslContext;
        return this;
    }

    public SingleHostHttpClientBuilder withoutSni() {
        this.withoutSni = true;
        return this;
    }

    /**
     * Build a client with HTTP header host overridden in Java 13+
     */
    public SingleHostHttpClientBuilder withoutHostHeader() {
        this.withoutHostHeader = true;
        return this;
    }

    private void setupSni() {
        final SSLParameters sslParameters = new SSLParameters();
        sslParameters.setServerNames(Collections.singletonList(new SNIHostName(hostname)));
        httpClientBuilder.sslParameters(sslParameters);
    }

    private void setupTlsNameMatching(KeyStore trustStore, SSLContext initialSslContext) {
        final SSLContext sslContextForSingleHostname = buildSslContextForSingleHostname(hostname, trustStore, initialSslContext);
        httpClientBuilder.sslContext(sslContextForSingleHostname);
    }

    private static SSLContext buildSslContextForSingleHostname(String hostname, KeyStore truststore, SSLContext initialSslContext) {
        final TrustManager[] trustOnlyGivenHostname = singleHostTrustManager(hostname, truststore);

        handleGeneralSecurityException(() -> initialSslContext.init(null, trustOnlyGivenHostname, new SecureRandom()));
        return initialSslContext;
    }

    private static TrustManager[] singleHostTrustManager(String hostname, KeyStore truststore) {
        final TrustManagerFactory instance = handleGeneralSecurityException(() -> TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()));

        handleGeneralSecurityException(() -> instance.init(truststore));

        var trustManagers = instance.getTrustManagers();
        var trustManager = (X509TrustManager) trustManagers[0];
        return new TrustManager[]{
                new SingleHostnameX509TrustManager(trustManager, hostname)
        };
    }

    /**
     * By defauilt it builds a single hostname client.
     * It uses TLS matching based on the given hostname.
     * It also provides the given hostname in SNI extension if this feature is not explicitly switched off.
     * The returned java.net.http.HttpClient is wrapped to force the HTTP header <code>Host</code> with the given hostname if this feature is not explicitly switched off.
     * The default connect timeout is set to 2 seconds.
     */
    public HttpClient build() {
        if (httpClientBuilder == null) {
            if (connectTimeout == null) {
                connectTimeout = DEFAULT_CONNECT_TIMEOUT;
            }
            httpClientBuilder = HttpClient.newBuilder().connectTimeout(connectTimeout);
        }

        if (initialSslContext == null) {
            initialSslContext = handleGeneralSecurityException(() -> SSLContext.getInstance("TLSv1.3"));
        }

        setupTlsNameMatching(trustStore, initialSslContext);

        if (!withoutSni) {
            setupSni();
        }

        final HttpClient httpClient = httpClientBuilder.build();

        if (!withoutHostHeader) {
            return isJava13orHigher()
                    .map(ignored -> new HttpClientWrapper(httpClient, hostname))
                    .map(HttpClient.class::cast)
                    .orElse(httpClient);
        }
        return httpClient;
    }

    private static Optional<Runtime.Version> isJava13orHigher() {
        return Optional.of(Runtime.version()).filter(version -> version.feature() >= 13);
    }

    interface RethrowGeneralSecurityException<T> {
        static <T> T handleGeneralSecurityException(RethrowGeneralSecurityException<T> operation) {
            try {
                return operation.run();
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }

        static void handleGeneralSecurityException(RethrowVoidGeneralSecurityException operation) {
            handleGeneralSecurityException(() -> {
                operation.run();
                return null;
            });
        }

        T run() throws GeneralSecurityException;
    }

    interface RethrowVoidGeneralSecurityException {

        void run() throws GeneralSecurityException;
    }


}
