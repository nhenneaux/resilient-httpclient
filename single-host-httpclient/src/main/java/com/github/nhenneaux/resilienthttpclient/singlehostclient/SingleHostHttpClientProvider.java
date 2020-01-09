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
import java.util.Collections;
import java.util.Optional;

import static com.github.nhenneaux.resilienthttpclient.singlehostclient.SingleHostHttpClientProvider.RethrowGeneralSecurityException.handleGeneralSecurityException;


/**
 * Create an {@link HttpClient} to target a single host.
 * It validates the certificate to authenticate the server in TLS communication with this single name.
 * It can be used to target a single host using its IP address(es) instead of its hostname while keeping a high protection against Man-in-the-middle attack.
 */
@SuppressWarnings("WeakerAccess") // To use outside the module
public class SingleHostHttpClientProvider {

    private static final String JDK_INTERNAL_HTTPCLIENT_DISABLE_HOSTNAME_VERIFICATION = "jdk.internal.httpclient.disableHostnameVerification";

    /*
     * Override host header in the HTTP request so that it can be used for routing on server side.
     */
    static {
        isJava13OrHigher().ifPresent(ignored -> System.setProperty("jdk.httpclient.allowRestrictedHeaders", HttpRequestWithHostHeader.HOST_HEADER));
    }

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

    public HttpClient buildSingleHostnameHttpClient(String hostname) {
        return buildSingleHostnameHttpClient(hostname, null);
    }

    public HttpClient buildSingleHostnameHttpClient(String hostname, KeyStore trustStore) {
        final HttpClient.Builder builder = HttpClient.newBuilder();
        return buildSingleHostnameHttpClient(hostname, trustStore, builder);
    }


    /**
     * Build a single hostname client. It overrides the following elements of the builder
     * <ul>
     *     <li><code>java.net.http.HttpClient.Builder#sslContext(javax.net.ssl.SSLContext)</code> with a custom SSLContext disabling default name validation and using the given hostname</li>
     *     <li><code>java.net.http.HttpClient.Builder#sslParameters(javax.net.ssl.SSLParameters)</code> to force the SNI server name expected</li>
     * </ul>
     * The returned java.net.http.HttpClient is wrapped to force the HTTP header <code>Host</code> with the given hostname.
     */
    public HttpClient buildSingleHostnameHttpClient(String hostname, KeyStore trustStore, HttpClient.Builder builder) {
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

        final SSLParameters sslParameters = new SSLParameters();
        sslParameters.setServerNames(Collections.singletonList(new SNIHostName(hostname)));
        builder.sslParameters(sslParameters);

        final HttpClient client = builder.build();
        return isJava13OrHigher()
                .map(ignored -> new HttpClientWrapper(client, hostname))
                .map(HttpClient.class::cast)
                .orElse(client);
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
