package github.vijay_papanaboina.cloud_storage_api.dto;

import github.vijay_papanaboina.cloud_storage_api.model.ApiKeyPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Request DTO for generating a new API key.
 */
public class ApiKeyRequest {
    private static final Set<Integer> ALLOWED_EXPIRY_DAYS = Set.of(30, 60, 90);

    @NotBlank(message = "API key name is required")
    @Size(max = 255, message = "API key name must not exceed 255 characters")
    private String name;

    @NotNull(message = "expiresInDays is required")
    private Integer expiresInDays;

    private ApiKeyPermission permissions = ApiKeyPermission.READ_ONLY;

    // Constructors
    public ApiKeyRequest() {
    }

    public ApiKeyRequest(String name, Integer expiresInDays) {
        this.name = name;
        this.expiresInDays = expiresInDays;
        this.permissions = ApiKeyPermission.READ_ONLY;
    }

    public ApiKeyRequest(String name, Integer expiresInDays, ApiKeyPermission permissions) {
        this.name = name;
        this.expiresInDays = expiresInDays;
        this.permissions = permissions != null ? permissions : ApiKeyPermission.READ_ONLY;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getExpiresInDays() {
        return expiresInDays;
    }

    public void setExpiresInDays(Integer expiresInDays) {
        if (expiresInDays == null) {
            throw new IllegalArgumentException("expiresInDays is required and cannot be null");
        }
        // Validate that expiresInDays is one of the allowed values (30, 60, or 90)
        if (!ALLOWED_EXPIRY_DAYS.contains(expiresInDays)) {
            throw new IllegalArgumentException(
                    "expiresInDays must be one of: 30, 60, or 90 days. Got: " + expiresInDays);
        }
        this.expiresInDays = expiresInDays;
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
                ", expiresInDays=" + expiresInDays +
                ", permissions=" + permissions +
                '}';
    }
}
