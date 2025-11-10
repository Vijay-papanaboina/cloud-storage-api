package github.vijay_papanaboina.cloud_storage_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request DTO for generating a new API key.
 */
public class ApiKeyRequest {
    @NotBlank(message = "API key name is required")
    @Size(max = 255, message = "API key name must not exceed 255 characters")
    private String name;

    private Instant expiresAt;

    // Constructors
    public ApiKeyRequest() {
    }

    public ApiKeyRequest(String name, Instant expiresAt) {
        this.name = name;
        this.expiresAt = expiresAt;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public String toString() {
        return "ApiKeyRequest{" +
                "name='" + name + '\'' +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
