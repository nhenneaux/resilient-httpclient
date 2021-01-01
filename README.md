# Resilient HTTP with `java.net.http.HttpClient`
Client using a pool of HTTP clients targeting each a single IP. Each of them is refreshed based on HTTP health check and DNS query. It has the following features.
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

[![Build Status](https://github.com/nhenneaux/resilient-httpclient/workflows/Java%20CI/badge.svg)](https://github.com/nhenneaux/resilient-httpclient/actions?query=workflow%3A%22Java+CI%22)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.nhenneaux.resilienthttpclient/monitored-httpclient/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.nhenneaux.resilienthttpclient/monitored-httpclient)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=nhenneaux_resilient-httpclient&metric=alert_status)](https://sonarcloud.io/dashboard?id=nhenneaux_resilient-httpclient)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=nhenneaux_resilient-httpclient&metric=coverage)](https://sonarcloud.io/dashboard?id=nhenneaux_resilient-httpclient)
