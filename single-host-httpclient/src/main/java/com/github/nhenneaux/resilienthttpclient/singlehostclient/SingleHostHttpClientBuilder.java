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
@SuppressWarnings({"WeakerAccess", "unused"}) // To use outside the module
public class SingleHostHttpClientBuilder {

    private final String hostname;
    private final HttpClient.Builder builder;

    private SingleHostHttpClientBuilder(String hostname, HttpClient.Builder builder) {
        this.hostname = hostname;
        this.builder = builder;
    }

    /**
     * Build a single hostname client with default configuration.
     * It uses TLS matching based on the given hostname.
     * It also provides the given hostname in SNI extension.
     * The returned java.net.http.HttpClient is wrapped to force the HTTP header <code>Host</code> with the given hostname.
     */
    public static HttpClient newHttpClient(String hostname) {
        return builder(hostname, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2L)))
                .withTlsNameMatching()
                .withSni()
                .buildWithHostHeader();
    }

    /**
     * Build a single hostname client builder.
     * It could override the following elements of the builder.
     * <ul>
     *     <li><code>java.net.http.HttpClient.Builder#sslContext(javax.net.ssl.SSLContext)</code> with a custom SSLContext using the given truststore disabling default name validation and using the given hostname</li>
     *     <li><code>java.net.http.HttpClient.Builder#sslParameters(javax.net.ssl.SSLParameters)</code> to force the SNI server name expected</li>
     * </ul>
     */
    public static SingleHostHttpClientBuilder builder(String hostname, HttpClient.Builder builder) {
        return new SingleHostHttpClientBuilder(hostname, builder);
    }

    public SingleHostHttpClientBuilder withSni() {
        final SSLParameters sslParameters = new SSLParameters();
        sslParameters.setServerNames(Collections.singletonList(new SNIHostName(hostname)));
        builder.sslParameters(sslParameters);
        return this;
    }

    public SingleHostHttpClientBuilder withTlsNameMatching() {
        return withTlsNameMatching((KeyStore) null);
    }

    public SingleHostHttpClientBuilder withTlsNameMatching(KeyStore trustStore) {
        return withTlsNameMatching(trustStore, handleGeneralSecurityException(() -> SSLContext.getInstance("TLSv1.3")));
    }

    public SingleHostHttpClientBuilder withTlsNameMatching(SSLContext initialSslContext) {
        return withTlsNameMatching(null, initialSslContext);
    }

    public SingleHostHttpClientBuilder withTlsNameMatching(KeyStore trustStore, SSLContext initialSslContext) {
        final SSLContext sslContextForSingleHostname = buildSslContextForSingleHostname(hostname, trustStore, initialSslContext);
        builder.sslContext(sslContextForSingleHostname);
        return this;
    }


    /**
     * Build a client with HTTP header host overridden in Java 13+
     */
    public HttpClient buildWithHostHeader() {
        HttpClient client = build();
        return isJava13OrHigher()
                .map(ignored -> new HttpClientWrapper(client, hostname))
                .map(HttpClient.class::cast)
                .orElse(client);
    }

    public HttpClient build() {
        return builder.build();
    }


    private static SSLContext buildSslContextForSingleHostname(String hostname, KeyStore truststore, SSLContext initialSslContext) {
        final TrustManager[] trustOnlyGivenHostname = singleHostTrustManager(hostname, truststore);

        handleGeneralSecurityException(() -> initialSslContext.init(null, trustOnlyGivenHostname, new SecureRandom()));
        return initialSslContext;
    }

    private static Optional<Runtime.Version> isJava13OrHigher() {
        return Optional.of(Runtime.version()).filter(version -> version.feature() >= 13);
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
