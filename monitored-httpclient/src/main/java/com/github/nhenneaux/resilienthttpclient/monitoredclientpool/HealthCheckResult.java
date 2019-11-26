package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

public class HealthCheckResult {
    private final HealthStatus status;
    private final Object details;

    public HealthCheckResult(HealthStatus status, Object details) {
        this.status = status;
        this.details = details;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public Object getDetails() {
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
