package github.vijay_papanaboina.cloud_storage_api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "idx_api_keys_key", columnList = "key"),
        @Index(name = "idx_api_keys_user_id", columnList = "user_id"),
        @Index(name = "idx_api_keys_active", columnList = "active")
})
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Size(max = 32)
    @Column(name = "key", nullable = false, length = 32, unique = true)
    private String key;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "permissions", nullable = false, length = 20)
    private ApiKeyPermission permissions = ApiKeyPermission.FULL_ACCESS;

    // Constructors
    public ApiKey() {
    }

    public ApiKey(String key, User user, ApiKeyPermission permissions) {
        this.key = key;
        this.user = user;
        this.permissions = permissions;
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public ApiKeyPermission getPermissions() {
        return permissions;
    }

    public void setPermissions(ApiKeyPermission permissions) {
        this.permissions = permissions;
    }

    // equals, hashCode, toString
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ApiKey apiKey = (ApiKey) o;
        return Objects.equals(id, apiKey.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ApiKey{" +
                "id=" + id +
                ", key='***REDACTED***'" + '\'' +
                ", name='" + name + '\'' +
                ", active=" + active +
                ", permissions=" + permissions +
                '}';
    }
}
