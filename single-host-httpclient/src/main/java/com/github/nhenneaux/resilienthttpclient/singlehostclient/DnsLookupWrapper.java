package com.github.nhenneaux.resilienthttpclient.singlehostclient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class DnsLookupWrapper {

    /**
     * Looks up for the IP addresses for the given host name.
     *
     * @param hostName the name to resolve
     * @return the set of {@link InetAddress} resolved from the given name in the DNS order
     */
    public Set<InetAddress> getInetAddressesByDnsLookUp(final String hostName) {
        final InetAddress[] inetSocketAddresses;
        try {
            inetSocketAddresses = InetAddress.getAllByName(hostName);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Cannot perform a DNS lookup for the hostname: " + hostName + ".", e);
        }
        return Collections.unmodifiableSet(new CopyOnWriteArraySet<>(Arrays.asList(inetSocketAddresses)));
    }

}
