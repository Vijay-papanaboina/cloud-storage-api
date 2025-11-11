package github.vijay_papanaboina.cloud_storage_api.dto;

import java.time.Instant;

/**
 * Response DTO for health check endpoint.
 */
public class HealthResponse {
    private String status;
    private Instant timestamp;

    // Constructors
    public HealthResponse() {
    }

    public HealthResponse(String status, Instant timestamp) {
        this.status = status;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

