# Resilient Java HTTP client based on `java.net.http.HttpClient`
Java HTTP client with auto refresh of underlying IP addresses.
* A Java HTTP client is build to use a single IP with a specific TLS hostname validation
* A pool of HTTP client targeting a single IP each is refreshed based on health check and DNS query.

[![Build Status](https://travis-ci.org/nhenneaux/resilient-java-httpclient.svg?branch=master)](https://travis-ci.org/nhenneaux/resilient-java-httpclient)
