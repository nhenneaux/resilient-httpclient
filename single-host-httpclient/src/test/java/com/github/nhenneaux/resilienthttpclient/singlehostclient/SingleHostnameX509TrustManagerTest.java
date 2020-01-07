package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SingleHostnameX509TrustManagerTest {

    static final String HOSTNAME = "SingleHostnameX509TrustManager.nhenneaux.github.com";

    @Test
    void shouldMatchDnsWithoutSan() throws CertificateException, IOException {
        final X509Certificate cer = loadCertificateWithoutSan();
        SingleHostnameX509TrustManager.matchDNS(HOSTNAME, cer);
    }

    private X509Certificate loadCertificateWithoutSan() throws CertificateException, IOException {
        final CertificateFactory fact = CertificateFactory.getInstance("X.509");
        try (final InputStream resourceAsStream = SingleHostnameX509TrustManagerTest.class.getResourceAsStream("/" + HOSTNAME + ".cert.pem")) {
            return Objects.requireNonNull((X509Certificate) fact.generateCertificate(resourceAsStream));
        }
    }

    @Test
    void shouldNotMatchDnsWithoutSan() throws CertificateException, IOException {
        final X509Certificate cer = loadCertificateWithoutSan();
        final CertificateException certificateException = assertThrows(CertificateException.class, () -> SingleHostnameX509TrustManager.matchDNS("not.matching.github.com", cer));
        assertEquals("No name matching not.matching.github.com found", certificateException.getMessage());
    }

}