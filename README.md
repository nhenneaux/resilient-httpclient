# Resilient Java HTTP client based on `java.net.http.HttpClient`
Resilient Java HTTP client based on `java.net.http.HttpClient`. A pool of HTTP client targeting a single IP each is refreshed based on health check and DNS query.
* Client side load balancing between all the IP behind the hostname
* Monitoring of all the IP behind the hostname at HTTP level
* Monitoring of the DNS
* TCP failover
* HTTP/2 with seamless fallback to HTTP/1

![Schema](images/dns_update.png)

A presentation detailing the features of the client and comparing it with other Java HTTP clients (HttpUrlConnection, Apache, Jetty) [Presentation](https://docs.google.com/presentation/d/1ixrKR79pX5jDGRO46mA03r20n3sQGhu2TRYoe_uKFYI/edit?usp=sharing).
# Usage
```java
HttpClientPool singletonByHost = HttpClientPool.newHttpClientPool(new ServerConfiguration("openjdk.java.net"));
java.net.http.HttpClient resilientClient = singletonByHost.resilientClient();

HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://openjdk.java.net/"))
        .build();
resilientClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(HttpResponse::body)
        .thenAccept(System.out::println)
        .join();
```
[![Build Status](https://travis-ci.com/nhenneaux/resilient-httpclient.svg?branch=master)](https://travis-ci.com/nhenneaux/resilient-httpclient)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.nhenneaux.resilienthttpclient/monitored-httpclient/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.nhenneaux.resilienthttpclient/monitored-httpclient)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=nhenneaux_resilient-httpclient&metric=alert_status)](https://sonarcloud.io/dashboard?id=nhenneaux_resilient-httpclient)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=nhenneaux_resilient-httpclient&metric=coverage)](https://sonarcloud.io/dashboard?id=nhenneaux_resilient-httpclient)
