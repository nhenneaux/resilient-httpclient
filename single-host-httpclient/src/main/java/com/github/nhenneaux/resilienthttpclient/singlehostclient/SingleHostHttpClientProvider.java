package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Optional;

import static com.github.nhenneaux.resilienthttpclient.singlehostclient.SingleHostHttpClientProvider.RethrowGeneralSecurityException.handleGeneralSecurityException;


/**
 * Create an {@link HttpClient} to target a single host.
 * It validates the certificate to authenticate the server in TLS communication with this single name.
 * It can be used to target a single host using its IP address(es) instead of its hostname while keeping a high protection against Man-in-the-middle attack.
 */
@SuppressWarnings("WeakerAccess") // To use outside the module
public class SingleHostHttpClientProvider {

    /*
     * Override host header in the HTTP request so that it can be used for routing on server side.
     */
    static {
        isJava13OrHigher().ifPresent(ignored -> System.setProperty("jdk.httpclient.allowRestrictedHeaders", HttpRequestWithHostHeader.HOST_HEADER));
    }

    static Optional<Runtime.Version> isJava13OrHigher() {
        return Optional.of(Runtime.version())
                .filter(version -> version.feature() >= 13);
    }

    private static final String JDK_INTERNAL_HTTPCLIENT_DISABLE_HOSTNAME_VERIFICATION = "jdk.internal.httpclient.disableHostnameVerification";

    public HttpClient buildSingleHostnameHttpClient(String hostname) {
        return buildSingleHostnameHttpClient(hostname, null);
    }

    public HttpClient buildSingleHostnameHttpClient(String hostname, KeyStore trustStore) {
        final HttpClient.Builder builder = HttpClient.newBuilder();
        return buildSingleHostnameHttpClient(hostname, trustStore, builder);
    }

    public HttpClient buildSingleHostnameHttpClient(String hostname, KeyStore trustStore, HttpClient.Builder builder) {
        final SSLContext sslContextForSingleHostname = buildSslContextForSingleHostname(hostname, trustStore);
        final HttpClient client;
        final String previousDisable = System.setProperty(JDK_INTERNAL_HTTPCLIENT_DISABLE_HOSTNAME_VERIFICATION, Boolean.TRUE.toString());
        try {
            client = builder
                    .sslContext(sslContextForSingleHostname)
                    .build();
        } finally {
            if (previousDisable == null) {
                System.clearProperty(JDK_INTERNAL_HTTPCLIENT_DISABLE_HOSTNAME_VERIFICATION);
            } else {
                System.setProperty(JDK_INTERNAL_HTTPCLIENT_DISABLE_HOSTNAME_VERIFICATION, previousDisable);
            }
        }
        return isJava13OrHigher().
                map(ignored -> new HttpClientWrapper(client, hostname))
                .map(HttpClient.class::cast)
                .orElse(client);
    }

    private SSLContext buildSslContextForSingleHostname(String hostname, KeyStore truststore) {
        final TrustManager[] trustOnlyGivenHostname = singleHostTrustManager(hostname, truststore);

        final SSLContext sslContextForSingleHostname = handleGeneralSecurityException(() -> SSLContext.getInstance("TLS"));

        handleGeneralSecurityException(() -> sslContextForSingleHostname.init(null, trustOnlyGivenHostname, new SecureRandom()));
        return sslContextForSingleHostname;
    }

    private TrustManager[] singleHostTrustManager(String hostname, KeyStore truststore) {
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
