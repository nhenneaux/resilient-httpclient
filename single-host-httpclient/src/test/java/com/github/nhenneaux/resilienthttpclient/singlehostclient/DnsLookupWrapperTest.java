package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class DnsLookupWrapperTest {

    @Test @Timeout(61)
    void shouldGetIntAddressInOrder() throws UnknownHostException {
        // Given
        final String hostName = "stackoverflow.com";
        final InetAddress[] names = InetAddress.getAllByName(hostName);
        // When
        final Set<InetAddress> inetAddressesByDnsLookUp = new DnsLookupWrapper().getInetAddressesByDnsLookUp(hostName);
        // Then
        assertArrayEquals(names, inetAddressesByDnsLookUp.toArray());
    }
}