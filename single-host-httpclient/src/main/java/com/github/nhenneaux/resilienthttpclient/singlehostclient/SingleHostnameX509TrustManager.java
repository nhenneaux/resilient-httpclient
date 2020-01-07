package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.X509TrustManager;
import java.net.IDN;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class SingleHostnameX509TrustManager implements X509TrustManager {
    private static final Logger LOGGER = Logger.getLogger(SingleHostnameX509TrustManager.class.getSimpleName());
    // constants for subject alt names of type DNS and IP
    private static final int ALTNAME_DNS = 2;

    private final X509TrustManager trustManager;
    private final String hostname;

    protected SingleHostnameX509TrustManager(X509TrustManager trustManager, String hostname) {
        this.trustManager = trustManager;
        this.hostname = hostname;
    }

    /**
     * Check if the certificate allows use of the given DNS name.
     * <p>
     * From RFC2818:
     * If a subjectAltName extension of type dNSName is present, that MUST
     * be used as the identity. Otherwise, the (most specific) Common Name
     * field in the Subject field of the certificate MUST be used. Although
     * the use of the Common Name is existing practice, it is deprecated and
     * Certification Authorities are encouraged to use the dNSName instead.
     * <p>
     * Matching is performed using the matching rules specified by
     * [RFC5280].  If more than one identity of a given type is present in
     * the certificate (e.g., more than one dNSName name, a match in any one
     * of the set is considered acceptable.)
     * <p>
     * Inspired from sun.security.util.HostnameChecker#matchDNS(java.lang.String, java.security.cert.X509Certificate, boolean)
     */
    private static void matchDNS(String expectedName, X509Certificate cert)
            throws CertificateException {
        // Check that the expected name is a valid domain name.
        try {
            // Using the checking implemented in SNIHostName
            new SNIHostName(expectedName);
        } catch (IllegalArgumentException iae) {
            throw new CertificateException("Illegal given domain name: " + expectedName, iae);
        }

        final Collection<List<?>> subjAltNames = cert.getSubjectAlternativeNames();
        if (subjAltNames != null) {
            boolean foundDNS = false;
            for (List<?> next : subjAltNames) {
                if ((Integer) next.get(0) == ALTNAME_DNS) {
                    foundDNS = true;
                    String dnsName = (String) next.get(1);
                    if (isMatched(expectedName, dnsName)) {
                        return;
                    }
                }
            }
            if (foundDNS) {
                // if certificate contains any subject alt names of type DNS
                // but none match, reject
                throw new CertificateException("No subject alternative DNS "
                        + "name matching " + expectedName + " found.");
            }
        }
        final String subject = getSubject(cert);
        if (subject != null && isMatched(expectedName, subject)) {
            return;
        }
        String msg = "No name matching " + expectedName + " found";
        throw new CertificateException(msg);
    }

    private static String getSubject(X509Certificate leaf) {
        return Stream.of(leaf)
                .map(cert -> cert.getSubjectX500Principal().getName())
                .flatMap(name -> {
                    final LdapName ldapName;
                    try {
                        ldapName = new LdapName(name);
                    } catch (InvalidNameException e) {
                        LOGGER.log(Level.INFO, e, () -> "The name " + name + " is not valid and cannot be parsed as javax.naming.ldap.LdapName");
                        return Stream.empty();
                    }
                    return ldapName.getRdns().stream()
                            .filter(rdn -> rdn.getType().equalsIgnoreCase("cn"))
                            .map(rdn -> rdn.getValue().toString());

                })
                .collect(joining(", "));
    }

    /**
     * Returns true if name matches against template.<p>
     * <p>
     * The matching is performed as per RFC 2818 rules for TLS and
     * RFC 2830 rules for LDAP.<p>
     * <p>
     * The <code>name</code> parameter should represent a DNS name.  The
     * <code>template</code> parameter may contain the wildcard character '*'.
     * <p>
     * Inspired from sun.security.util.HostnameChecker#isMatched(java.lang.String, java.lang.String, boolean)
     */
    private static boolean isMatched(String name, String template) {

        // Normalize to Unicode, because PSL is in Unicode.
        try {
            name = IDN.toUnicode(IDN.toASCII(name));
            template = IDN.toUnicode(IDN.toASCII(template));
        } catch (RuntimeException re) {
            LOGGER.log(Level.FINE, "Failed to normalize to Unicode.", re);
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
     * Inspired from sun.security.util.HostnameChecker#hasIllegalWildcard(java.lang.String, boolean)
     */
    private static boolean hasIllegalWildcard(
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
     * Inspired from sun.security.util.HostnameChecker#matchAllWildcards(java.lang.String, java.lang.String)
     */
    private static boolean matchAllWildcards(String name, String template) {
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
     * contain wildcard char
     * <p>
     * Inspired from sun.security.util.HostnameChecker#matchWildCards(java.lang.String, java.lang.String)
     */
    private static boolean matchWildCards(String name, String template) {

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

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return trustManager.getAcceptedIssuers();
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
        trustManager.checkClientTrusted(certs, authType);
    }

    /**
     * Check the server is trusted using the instance {@link #trustManager}.
     * Then doing a DNS name validation based on {@link #hostname}
     */
    @Override
    public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
        trustManager.checkServerTrusted(certs, authType);

        final X509Certificate leaf = certs[0];
        matchDNS(hostname, leaf);
    }
}
