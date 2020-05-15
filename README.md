# Resilient Java HTTP client based on `java.net.http.HttpClient`
Java HTTP client with pooling and auto refresh of underlying IP addresses.
* A Java HTTP client is build to use a single IP with a specific TLS hostname validation
* A pool of HTTP client targeting a single IP each is refreshed based on health check and DNS query.

# Usage
```java
HttpClientPool singletonByHost = HttpClientPool.newHttpClientPool(
                                                        new ServerConfiguration("openjdk.java.net"));
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
