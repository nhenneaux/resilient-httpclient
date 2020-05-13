package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import java.util.List;

public class HealthCheckResult {
    private final HealthStatus status;
    private final List<ConnectionDetail> details;

    public HealthCheckResult(HealthStatus status, List<ConnectionDetail> details) {
        this.status = status;
        this.details = List.copyOf(details);
    }

    public HealthStatus getStatus() {
        return status;
    }

    public List<ConnectionDetail> getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return "HealthCheckResult{" +
                "status=" + status +
                ", details=" + details +
                '}';
    }

    public enum HealthStatus {
        OK, WARNING, ERROR
    }
}
