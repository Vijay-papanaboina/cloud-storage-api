package github.vijay_papanaboina.cloud_storage_api.repository;

import github.vijay_papanaboina.cloud_storage_api.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Find API key by key value (for API key authentication).
     */
    Optional<ApiKey> findByKey(String key);

    /**
     * Find all API keys for a user (GET /api/auth/api-keys).
     */
    List<ApiKey> findByUserId(UUID userId);

    /**
     * Find all active API keys for a user.
     */
    List<ApiKey> findByUserIdAndActiveTrue(UUID userId);

    /**
     * Get API key by ID and user ID (GET /api/auth/api-keys/{id}).
     * Returns API key only if it belongs to the user.
     */
    @Query("SELECT a FROM ApiKey a WHERE a.id = :id AND a.user.id = :userId")
    Optional<ApiKey> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
}
