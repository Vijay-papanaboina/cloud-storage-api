package github.vijay_papanaboina.cloud_storage_api.dto;

import java.time.Instant;
import java.util.UUID;

public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private Boolean active;
    private Instant createdAt;
    private Instant lastLoginAt;

    // Constructors
    public UserResponse() {
    }

    public UserResponse(UUID id, String username, String email, Boolean active, Instant createdAt,
            Instant lastLoginAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.active = active;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
