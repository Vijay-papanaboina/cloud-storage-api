package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.ApiKeyRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.ApiKeyResponse;
import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import github.vijay_papanaboina.cloud_storage_api.exception.ForbiddenException;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;
import github.vijay_papanaboina.cloud_storage_api.model.ApiKey;
import github.vijay_papanaboina.cloud_storage_api.model.ApiKeyPermission;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import github.vijay_papanaboina.cloud_storage_api.repository.ApiKeyRepository;
import github.vijay_papanaboina.cloud_storage_api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ApiKeyServiceImpl implements ApiKeyService {
    private static final Logger log = LoggerFactory.getLogger(ApiKeyServiceImpl.class);
    private static final int API_KEY_LENGTH = 32;
    private static final SecureRandom secureRandom = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;

    @Autowired
    public ApiKeyServiceImpl(ApiKeyRepository apiKeyRepository, UserRepository userRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public ApiKeyResponse generateApiKey(ApiKeyRequest request, UUID userId) {
        // Validate request parameter
        Objects.requireNonNull(request, "Request cannot be null");
        Objects.requireNonNull(request.getName(), "API key name cannot be null");

        log.info("Generating API key: userId={}, name={}", userId, request.getName());

        // Ensure userId is not null
        Objects.requireNonNull(userId, "User ID cannot be null");
        // Validate user exists and is active
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId, userId));

        if (!user.getActive()) {
            log.warn("Attempt to generate API key for inactive user: userId={}", userId);
            throw new BadRequestException("User account is inactive");
        }

        // Validate expiration date if provided
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Attempt to create API key with past expiration date: userId={}", userId);
            throw new BadRequestException("Expiration date cannot be in the past");
        }

        // Generate unique API key
        String apiKeyValue = generateUniqueApiKey();

        // Create API key entity
        ApiKey apiKey = new ApiKey();
        apiKey.setKey(apiKeyValue);
        apiKey.setUser(user);
        apiKey.setName(request.getName());
        apiKey.setActive(true);
        apiKey.setCreatedAt(Instant.now());
        apiKey.setExpiresAt(request.getExpiresAt());
        apiKey.setPermissions(
                request.getPermissions() != null ? request.getPermissions() : ApiKeyPermission.READ_ONLY);

        // Save API key
        ApiKey savedApiKey = apiKeyRepository.save(apiKey);
        log.info("API key generated successfully: apiKeyId={}, userId={}", savedApiKey.getId(), userId);

        // Return response with key value (only time it's returned)
        return ApiKeyResponse.from(savedApiKey, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(UUID userId) {
        log.info("Listing API keys: userId={}", userId);

        // Ensure userId is not null
        Objects.requireNonNull(userId, "User ID cannot be null");

        // Validate user exists
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found: " + userId, userId);
        }

        // Get all API keys for user
        List<ApiKey> apiKeys = apiKeyRepository.findByUserId(userId);

        // Map to response DTOs (without key values)
        return apiKeys.stream()
                .map(apiKey -> ApiKeyResponse.from(apiKey, false))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ApiKeyResponse getApiKey(UUID id, UUID userId) {
        log.info("Getting API key: id={}, userId={}", id, userId);

        // Ensure parameters are not null
        Objects.requireNonNull(id, "API key ID cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");

        // Get API key (user-scoped)
        ApiKey apiKey = apiKeyRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + id, id));

        // Return response without key value
        return ApiKeyResponse.from(apiKey, false);
    }

    @Override
    @Transactional
    public void revokeApiKey(UUID id, UUID userId) {
        log.info("Revoking API key: id={}, userId={}", id, userId);

        // Ensure parameters are not null
        Objects.requireNonNull(id, "API key ID cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");

        // Get API key (user-scoped)
        ApiKey apiKey = apiKeyRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> {
                    // Check if API key exists but belongs to another user
                    if (apiKeyRepository.findById(id).isPresent()) {
                        log.warn("Attempt to revoke API key belonging to another user: id={}, userId={}", id, userId);
                        throw new ForbiddenException("API key belongs to another user");
                    }
                    return new ResourceNotFoundException("API key not found: " + id, id);
                });

        // Deactivate API key
        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);

        log.info("API key revoked successfully: apiKeyId={}, userId={}", id, userId);
    }

    @Override
    @Transactional
    public void updateLastUsedAt(UUID apiKeyId) {
        Objects.requireNonNull(apiKeyId, "API key ID cannot be null");
        try {
            // Find the API key by ID to get a managed entity
            ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                    .orElse(null);

            if (apiKey != null) {
                // Update lastUsedAt on the managed entity
                apiKey.setLastUsedAt(Instant.now());
                // Save the managed entity
                apiKeyRepository.save(apiKey);
                log.debug("Updated lastUsedAt for API key: {}", apiKeyId);
            } else {
                log.warn("API key not found when updating lastUsedAt: {}", apiKeyId);
            }
        } catch (Exception e) {
            // Log error but don't fail authentication
            log.warn("Failed to update lastUsedAt for API key: {}", apiKeyId, e);
        }
    }

    /**
     * Generate a unique 32-character alphanumeric API key.
     * Uses SecureRandom with Base64 URL encoding for cryptographically secure
     * generation.
     *
     * @return Unique API key string
     */
    private String generateUniqueApiKey() {
        int maxAttempts = 10;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Generate 24 random bytes (will produce 32 Base64 URL characters)
            byte[] randomBytes = new byte[24];
            secureRandom.nextBytes(randomBytes);

            // Encode to Base64 URL-safe string and take first 32 characters
            String apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
                    .substring(0, API_KEY_LENGTH);

            // Check if key already exists
            if (!apiKeyRepository.findByKey(apiKey).isPresent()) {
                return apiKey;
            }

            log.warn("Generated API key already exists, retrying: attempt={}", attempt + 1);
        }

        throw new RuntimeException("Failed to generate unique API key after " + maxAttempts + " attempts");
    }
}
