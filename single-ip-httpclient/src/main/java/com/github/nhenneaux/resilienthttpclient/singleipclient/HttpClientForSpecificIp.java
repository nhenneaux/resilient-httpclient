package com.github.nhenneaux.resilienthttpclient.singleipclient;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;


public class HttpClientForSpecificIp {

    private static final String JDK_INTERNAL_HTTPCLIENT_DISABLE_HOSTNAME_VERIFICATION = "jdk.internal.httpclient.disableHostnameVerification";


    public static void main(String[] args) throws InterruptedException {
        //var hostname = "api.bambora.com";
        //var hostname = "google.com";
        var hostname = "openjdk.java.net";
        //var hostname = "sis.redsys.es";
        final String ip = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostname).get(0).getHostAddress();
        // TODO review truststore loading
        final HttpClient client = new HttpClientForSpecificIp().buildSingleHostnameHttpClient(hostname);


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + ip
                        //    + "/health"
                ))
                .build();
        for (int i = 0; i < 10_000; i++) {
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(System.out::println)
                    .join();
            Thread.sleep(100);
        }

    }

    public HttpClient buildSingleHostnameHttpClient(String hostname) {
        final TrustManagerFactory instance;
        try {
            instance = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try {
            instance.init((KeyStore) null);
        } catch (KeyStoreException e) {
            throw new IllegalStateException(e);
        }
        final TrustManager[] trustManagers = instance.getTrustManagers();
        final X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
        TrustManager[] trustOnlyGivenHostname = new TrustManager[]{
                new SingleHostnameX509TrustManager(trustManager, hostname)
        };

        Properties props = System.getProperties();

        final SSLContext aDefault;
        try {
            aDefault = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try {
            aDefault.init(null, trustOnlyGivenHostname, new SecureRandom());
        } catch (KeyManagementException e) {
            throw new IllegalStateException(e);
        }

        final HttpClient client;
        final String previousDisable = (String) props.setProperty(JDK_INTERNAL_HTTPCLIENT_DISABLE_HOSTNAME_VERIFICATION, Boolean.TRUE.toString());
        try {
            client = HttpClient.newBuilder()
                    .sslContext(aDefault)
                    .build();
        } finally {
            if (previousDisable == null) {
                props.remove(JDK_INTERNAL_HTTPCLIENT_DISABLE_HOSTNAME_VERIFICATION);
            } else {
                props.setProperty(JDK_INTERNAL_HTTPCLIENT_DISABLE_HOSTNAME_VERIFICATION, previousDisable);
            }
        }
        return client;
    }


    static class DnsLookupWrapper {

        /**
         * Looks up for the IP addresses for the given host name.
         */
        public List<InetAddress> getInetAddressesByDnsLookUp(final String hostName) {
            final InetAddress[] inetSocketAddresses;
            try {
                inetSocketAddresses = InetAddress.getAllByName(hostName);
            } catch (UnknownHostException e) {
                throw new IllegalStateException("Cannot perform a DNS lookup for the hostname: " + hostName + ".", e);
            }

            return Arrays.stream(inetSocketAddresses)
                    .collect(Collectors.toList());
        }

    }


    private static class SingleHostnameX509TrustManager implements X509TrustManager {
        // constants for subject alt names of type DNS and IP
        private static final int ALTNAME_DNS = 2;

        private final X509TrustManager trustManager;
        private final String hostname;

        private SingleHostnameX509TrustManager(X509TrustManager trustManager, String hostname) {
            this.trustManager = trustManager;
            this.hostname = hostname;
        }

        public X509Certificate[] getAcceptedIssuers() {
            return trustManager.getAcceptedIssuers();
        }

        public void checkClientTrusted(
                X509Certificate[] certs, String authType) throws CertificateException {
            trustManager.checkClientTrusted(certs, authType);
        }

        public void checkServerTrusted(
                X509Certificate[] certs, String authType) throws CertificateException {
            trustManager.checkServerTrusted(certs, authType);
            final X509Certificate leaf = certs[0];

            Collection<List<?>> subjAltNames = leaf.getSubjectAlternativeNames();
            if (subjAltNames != null) {
                boolean foundDNS = false;
                for (List<?> next : subjAltNames) {
                    if ((Integer) next.get(0) == ALTNAME_DNS) {
                        foundDNS = true;
                        String dnsName = (String) next.get(1);
                        if (isMatched(hostname, dnsName)) {
                            return;
                        }
                    }
                }
                if (foundDNS) {
                    // if certificate contains any subject alt names of type DNS
                    // but none match, reject
                    throw new CertificateException("No subject alternative DNS "
                            + "name matching " + hostname + " found.");
                }
            }

            final String cn = Stream.of(leaf)
                    .map(cert -> cert.getSubjectX500Principal().getName())
                    .flatMap(name -> {
                        try {
                            return new LdapName(name).getRdns().stream()
                                    .filter(rdn -> rdn.getType().equalsIgnoreCase("cn"))
                                    .map(rdn -> rdn.getValue().toString());
                        } catch (InvalidNameException e) {
                            // TODO log correctly
                            e.printStackTrace();
                            return Stream.empty();
                        }
                    })
                    .collect(joining(", "));

            if (!matchAllWildcards(hostname, cn)) {
                throw new CertificateException("CN `" + cn + "` is not matching the expected hostname `" + hostname + "`.");
            }
        }

        /**
         * Returns true if name matches against template.<p>
         * <p>
         * The matching is performed as per RFC 2818 rules for TLS and
         * RFC 2830 rules for LDAP.<p>
         * <p>
         * The <code>name</code> parameter should represent a DNS name.  The
         * <code>template</code> parameter may contain the wildcard character '*'.
         */
        private boolean isMatched(String name, String template) {

            // Normalize to Unicode, because PSL is in Unicode.
            try {
                name = IDN.toUnicode(IDN.toASCII(name));
                template = IDN.toUnicode(IDN.toASCII(template));
            } catch (RuntimeException re) {
                // TODO log correctly
                re.printStackTrace();
                return false;
            }

            if (hasIllegalWildcard(template)) {
                return false;
            }

            // check the validity of the domain name template.
            try {
                // Replacing wildcard character '*' with 'z' so as to check
                // the domain name template validity.
                //
                // Using the checking implemented in SNIHostName
                new SNIHostName(template.replace('*', 'z'));
            } catch (IllegalArgumentException iae) {
                // It would be nice to add debug log if not matching.
                return false;
            }

            return matchAllWildcards(name, template);
        }

        /**
         * Returns true if the template contains an illegal wildcard character.
         */
        private boolean hasIllegalWildcard(
                String template) {
            // not ok if it is a single wildcard character or "*."
            if (template.equals("*") || template.equals("*.")) {
                return true;
            }

            int lastWildcardIndex = template.lastIndexOf('*');

            // ok if it has no wildcard character
            if (lastWildcardIndex == -1) {
                return false;
            }

            String afterWildcard = template.substring(lastWildcardIndex);
            int firstDotIndex = afterWildcard.indexOf('.');

            // not ok if there is no dot after wildcard (ex: "*com")
            return firstDotIndex == -1;
        }

        /**
         * Returns true if name matches against template.<p>
         * <p>
         * According to RFC 2818, section 3.1 -
         * Names may contain the wildcard character * which is
         * considered to match any single domain name component
         * or component fragment.
         * E.g., *.a.com matches foo.a.com but not
         * bar.foo.a.com. f*.com matches foo.com but not bar.com.
         */
        private boolean matchAllWildcards(String name,
                                          String template) {
            name = name.toLowerCase(Locale.ENGLISH);
            template = template.toLowerCase(Locale.ENGLISH);
            StringTokenizer nameSt = new StringTokenizer(name, ".");
            StringTokenizer templateSt = new StringTokenizer(template, ".");

            if (nameSt.countTokens() != templateSt.countTokens()) {
                return false;
            }

            while (nameSt.hasMoreTokens()) {
                if (!matchWildCards(nameSt.nextToken(),
                        templateSt.nextToken())) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Returns true if the name matches against the template that may
         * contain wildcard char * <p>
         */
        private boolean matchWildCards(String name, String template) {

            int wildcardIdx = template.indexOf('*');
            if (wildcardIdx == -1)
                return name.equals(template);

            boolean isBeginning = true;
            String beforeWildcard;
            String afterWildcard = template;

            while (wildcardIdx != -1) {

                // match in sequence the non-wildcard chars in the template.
                beforeWildcard = afterWildcard.substring(0, wildcardIdx);
                afterWildcard = afterWildcard.substring(wildcardIdx + 1);

                int beforeStartIdx = name.indexOf(beforeWildcard);
                if ((beforeStartIdx == -1) ||
                        (isBeginning && beforeStartIdx != 0)) {
                    return false;
                }
                isBeginning = false;

                // update the match scope
                name = name.substring(beforeStartIdx + beforeWildcard.length());
                wildcardIdx = afterWildcard.indexOf('*');
            }
            return name.endsWith(afterWildcard);
        }
    }
}
