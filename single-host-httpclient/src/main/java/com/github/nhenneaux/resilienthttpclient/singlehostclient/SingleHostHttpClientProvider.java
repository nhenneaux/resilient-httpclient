package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import static com.github.nhenneaux.resilienthttpclient.singlehostclient.SingleHostHttpClientProvider.RethrowNoSuchAlgorithmException.handleNoSuchAlgorithmException;


/**
 * Create an {@link HttpClient} to target a single host.
 * It validates the certificate to authenticate the server in TLS communication with this single name.
 * It can be used to target a single host using its IP address(es) instead of its hostname while keeping a high protection against Man-in-the-middle attack.
 */
@SuppressWarnings("WeakerAccess") // To use outside the module
public class SingleHostHttpClientProvider {


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
        return client;
    }

    private SSLContext buildSslContextForSingleHostname(String hostname, KeyStore truststore) {
        final TrustManager[] trustOnlyGivenHostname = singleHostTrustManager(hostname, truststore);

        final SSLContext sslContextForSingleHostname = handleNoSuchAlgorithmException(() -> SSLContext.getInstance("TLS"));

        try {
            sslContextForSingleHostname.init(null, trustOnlyGivenHostname, new SecureRandom());
        } catch (KeyManagementException e) {
            throw new IllegalStateException(e);
        }
        return sslContextForSingleHostname;
    }

    private TrustManager[] singleHostTrustManager(String hostname, KeyStore truststore) {
        final TrustManagerFactory instance = handleNoSuchAlgorithmException(() -> TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()));

        try {
            instance.init(truststore);
        } catch (KeyStoreException e) {
            throw new IllegalStateException(e);
        }
        var trustManagers = instance.getTrustManagers();
        var trustManager = (X509TrustManager) trustManagers[0];
        return new TrustManager[]{
                new SingleHostnameX509TrustManager(trustManager, hostname)
        };
    }

    interface RethrowNoSuchAlgorithmException<T> {
        static <T> T handleNoSuchAlgorithmException(RethrowNoSuchAlgorithmException<T> operation) {
            try {
                return operation.run();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        T run() throws NoSuchAlgorithmException;
    }


}
