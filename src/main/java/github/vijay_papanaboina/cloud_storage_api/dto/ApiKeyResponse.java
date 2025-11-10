package github.vijay_papanaboina.cloud_storage_api.dto;

import github.vijay_papanaboina.cloud_storage_api.model.ApiKey;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for API key information.
 * The key field is only included when includeKey is true (typically only on
 * creation).
 */
public class ApiKeyResponse {
    private UUID id;
    private String key; // Only returned on creation, null otherwise
    private String name;
    private Boolean active;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant lastUsedAt;

    // Constructors
    public ApiKeyResponse() {
    }

    public ApiKeyResponse(UUID id, String key, String name, Boolean active, Instant createdAt,
            Instant expiresAt, Instant lastUsedAt) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.active = active;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastUsedAt = lastUsedAt;
    }

    /**
     * Factory method to create ApiKeyResponse from ApiKey entity.
     *
     * @param apiKey     The API key entity
     * @param includeKey Whether to include the key value (only true on creation)
     * @return ApiKeyResponse DTO
     */
    public static ApiKeyResponse from(ApiKey apiKey, boolean includeKey) {
        if (apiKey == null) {
            throw new IllegalArgumentException("apiKey cannot be null");
        }
        ApiKeyResponse response = new ApiKeyResponse();
        response.setId(apiKey.getId());
        response.setKey(includeKey ? apiKey.getKey() : null);
        response.setName(apiKey.getName());
        response.setActive(apiKey.getActive());
        response.setCreatedAt(apiKey.getCreatedAt());
        response.setExpiresAt(apiKey.getExpiresAt());
        response.setLastUsedAt(apiKey.getLastUsedAt());
        return response;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    @Override
    public String toString() {
        return "ApiKeyResponse{" +
                "id=" + id +
                ", key='" + (key != null ? "[REDACTED]" : null) + '\'' +
                ", name='" + name + '\'' +
                ", active=" + active +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                ", lastUsedAt=" + lastUsedAt +
                '}';
    }
}
