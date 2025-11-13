package github.vijay_papanaboina.cloud_storage_api.dto;

import github.vijay_papanaboina.cloud_storage_api.model.ApiKeyPermission;
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

    private ApiKeyPermission permissions = ApiKeyPermission.READ_ONLY;

    // Constructors
    public ApiKeyRequest() {
    }

    public ApiKeyRequest(String name, Instant expiresAt) {
        this.name = name;
        this.expiresAt = expiresAt;
        this.permissions = ApiKeyPermission.READ_ONLY;
    }

    public ApiKeyRequest(String name, Instant expiresAt, ApiKeyPermission permissions) {
        this.name = name;
        this.expiresAt = expiresAt;
        this.permissions = permissions != null ? permissions : ApiKeyPermission.READ_ONLY;
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

    public ApiKeyPermission getPermissions() {
        return permissions;
    }

    public void setPermissions(ApiKeyPermission permissions) {
        this.permissions = permissions != null ? permissions : ApiKeyPermission.READ_ONLY;
    }

    @Override
    public String toString() {
        return "ApiKeyRequest{" +
                "name='" + name + '\'' +
                ", expiresAt=" + expiresAt +
                ", permissions=" + permissions +
                '}';
    }
}
