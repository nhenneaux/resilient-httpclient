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
 */
@SuppressWarnings("WeakerAccess") // To use outside the module
public class SingleHostHttpClientBuilder {


    private final String hostname;
    private final HttpClient.Builder builder;

    private SingleHostHttpClientBuilder(String hostname, HttpClient.Builder builder) {
        this.hostname = hostname;
        this.builder = builder;
    }

    public static SingleHostHttpClientBuilder builder(String hostname) {
        return new SingleHostHttpClientBuilder(hostname, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)));
    }

    public static SingleHostHttpClientBuilder builder(String hostname, HttpClient.Builder builder) {
        return new SingleHostHttpClientBuilder(hostname, builder);
    }

    /**
     * Build a single hostname client. It overrides the following elements of the builder
     * <ul>
     *     <li><code>java.net.http.HttpClient.Builder#sslContext(javax.net.ssl.SSLContext)</code> with a custom SSLContext disabling default name validation and using the given hostname</li>
     *     <li><code>java.net.http.HttpClient.Builder#sslParameters(javax.net.ssl.SSLParameters)</code> to force the SNI server name expected</li>
     * </ul>
     * The returned java.net.http.HttpClient is wrapped to force the HTTP header <code>Host</code> with the given hostname.
     */
    public static HttpClient build(String hostname) {
        return builder(hostname)
                .withTlsNameMatching()
                .withSni()
                .buildWithHostHeader();
    }

    /**
     * Build a single hostname client. It overrides the following elements of the builder
     * <ul>
     *     <li><code>java.net.http.HttpClient.Builder#sslContext(javax.net.ssl.SSLContext)</code> with a custom SSLContext disabling default name validation and using the given hostname</li>
     *     <li><code>java.net.http.HttpClient.Builder#sslParameters(javax.net.ssl.SSLParameters)</code> to force the SNI server name expected</li>
     * </ul>
     * The returned java.net.http.HttpClient is wrapped to force the HTTP header <code>Host</code> with the given hostname.
     */
    public static HttpClient build(String hostname, HttpClient.Builder builder) {
        return builder(hostname, builder)
                .withTlsNameMatching(null)
                .withSni()
                .buildWithHostHeader();
    }


    public static HttpClient build(String hostname, KeyStore trustStore, HttpClient.Builder builder) {
        return builder(hostname, builder)
                .withTlsNameMatching(trustStore)
                .withSni()
                .buildWithHostHeader();
    }

    SingleHostHttpClientBuilder withSni() {
        final SSLParameters sslParameters = new SSLParameters();
        sslParameters.setServerNames(Collections.singletonList(new SNIHostName(hostname)));
        builder.sslParameters(sslParameters);
        return this;
    }

    SingleHostHttpClientBuilder withTlsNameMatching() {
        return withTlsNameMatching(null);
    }

    SingleHostHttpClientBuilder withTlsNameMatching(KeyStore trustStore) {
        final SSLContext sslContextForSingleHostname = buildSslContextForSingleHostname(hostname, trustStore);
        final String previousDisable = System.setProperty(JDK_INTERNAL_HTTPCLIENT_DISABLE_HOSTNAME_VERIFICATION, Boolean.TRUE.toString());
        try {
            builder.sslContext(sslContextForSingleHostname);
        } finally {
            if (previousDisable == null) {
                System.clearProperty(JDK_INTERNAL_HTTPCLIENT_DISABLE_HOSTNAME_VERIFICATION);
            } else {
                System.setProperty(JDK_INTERNAL_HTTPCLIENT_DISABLE_HOSTNAME_VERIFICATION, previousDisable);
            }
        }
        return this;
    }

    public HttpClient buildWithHostHeader() {
        /*
         * Override host header in the HTTP request so that it can be used for routing on server side.
         */
        isJava13OrHigher().ifPresent(ignored -> System.setProperty("jdk.httpclient.allowRestrictedHeaders", HttpRequestWithHostHeader.HOST_HEADER));
        HttpClient client = builder.build();
        return isJava13OrHigher()
                .map(ignored -> new HttpClientWrapper(client, hostname))
                .map(HttpClient.class::cast)
                .orElse(client);
    }

    public HttpClient build() {
        return builder.build();
    }


    private static final String JDK_INTERNAL_HTTPCLIENT_DISABLE_HOSTNAME_VERIFICATION = "jdk.internal.httpclient.disableHostnameVerification";


    static Optional<Runtime.Version> isJava13OrHigher() {
        return Optional.of(Runtime.version()).filter(version -> version.feature() >= 13);
    }


    public static SSLContext buildSslContextForSingleHostname(String hostname, KeyStore truststore) {
        final TrustManager[] trustOnlyGivenHostname = singleHostTrustManager(hostname, truststore);

        final SSLContext sslContextForSingleHostname = handleGeneralSecurityException(() -> SSLContext.getInstance("TLSv1.3"));

        handleGeneralSecurityException(() -> sslContextForSingleHostname.init(null, trustOnlyGivenHostname, new SecureRandom()));
        return sslContextForSingleHostname;
    }

    public static TrustManager[] singleHostTrustManager(String hostname, KeyStore truststore) {
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
