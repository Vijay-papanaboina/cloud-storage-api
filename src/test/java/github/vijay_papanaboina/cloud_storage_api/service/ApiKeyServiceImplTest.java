package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.ApiKeyRequest;
import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import github.vijay_papanaboina.cloud_storage_api.exception.ForbiddenException;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;
import github.vijay_papanaboina.cloud_storage_api.model.ApiKey;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import github.vijay_papanaboina.cloud_storage_api.repository.ApiKeyRepository;
import github.vijay_papanaboina.cloud_storage_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApiKeyServiceImpl focusing on error contracts and edge cases.
 * Success scenarios are covered by integration tests.
 * These tests verify what exceptions are thrown, not how the code works
 * internally.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceImplTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ApiKeyServiceImpl apiKeyService;

    private UUID userId;
    private UUID otherUserId;
    private UUID apiKeyId;
    private User testUser;
    private ApiKey testApiKey;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        apiKeyId = UUID.randomUUID();

        testUser = createTestUser(userId);
        testApiKey = createTestApiKey(apiKeyId, testUser);
    }

    // ==================== GenerateApiKey Error Contract Tests ====================

    @Test
    void generateApiKey_NullRequest_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.generateApiKey(null, userId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Request cannot be null");
    }

    @Test
    void generateApiKey_NullName_ThrowsException() {
        // Given
        ApiKeyRequest request = createTestApiKeyRequest(null, null);

        // When/Then
        assertThatThrownBy(() -> apiKeyService.generateApiKey(request, userId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("API key name cannot be null");
    }

    @Test
    void generateApiKey_NullUserId_ThrowsException() {
        // Given
        ApiKeyRequest request = createTestApiKeyRequest("Test API Key", null);

        // When/Then
        assertThatThrownBy(() -> apiKeyService.generateApiKey(request, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    @Test
    void generateApiKey_UserNotFound_ThrowsResourceNotFoundException() {
        // Given
        ApiKeyRequest request = createTestApiKeyRequest("Test API Key", null);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> apiKeyService.generateApiKey(request, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void generateApiKey_InactiveUser_ThrowsBadRequestException() {
        // Given
        ApiKeyRequest request = createTestApiKeyRequest("Test API Key", null);
        testUser.setActive(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When/Then
        assertThatThrownBy(() -> apiKeyService.generateApiKey(request, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("User account is inactive");
    }

    @Test
    void generateApiKey_InvalidExpirationDays_ThrowsBadRequestException() {
        // Given - invalid expiresInDays value (not 30, 60, or 90)
        // Use reflection to bypass setter validation and set invalid value directly
        ApiKeyRequest request = new ApiKeyRequest();
        request.setName("Test API Key");
        try {
            java.lang.reflect.Field field = ApiKeyRequest.class.getDeclaredField("expiresInDays");
            field.setAccessible(true);
            field.set(request, 45); // Invalid value
        } catch (Exception e) {
            org.junit.jupiter.api.Assertions
                    .fail("Failed to set invalid expiresInDays via reflection: " + e.getMessage());
        }

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When/Then
        assertThatThrownBy(() -> apiKeyService.generateApiKey(request, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expiresInDays must be one of: 30, 60, or 90 days");
    }

    // ==================== ListApiKeys Error Contract Tests ====================

    @Test
    void listApiKeys_NullUserId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.listApiKeys(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    @Test
    void listApiKeys_UserNotFound_ThrowsResourceNotFoundException() {
        // Given
        when(userRepository.existsById(userId)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> apiKeyService.listApiKeys(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ==================== GetApiKey Error Contract Tests ====================

    @Test
    void getApiKey_NullId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.getApiKey(null, userId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("API key ID cannot be null");
    }

    @Test
    void getApiKey_NullUserId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.getApiKey(apiKeyId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    @Test
    void getApiKey_NotFound_ThrowsResourceNotFoundException() {
        // Given
        when(apiKeyRepository.findByIdAndUserId(apiKeyId, userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> apiKeyService.getApiKey(apiKeyId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("API key not found");
    }

    // ==================== RevokeApiKey Error Contract Tests ====================

    @Test
    void revokeApiKey_NullId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.revokeApiKey(null, userId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("API key ID cannot be null");
    }

    @Test
    void revokeApiKey_NullUserId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.revokeApiKey(apiKeyId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    @Test
    void revokeApiKey_NotFound_ThrowsResourceNotFoundException() {
        // Given
        when(apiKeyRepository.findByIdAndUserId(apiKeyId, userId)).thenReturn(Optional.empty());
        when(apiKeyRepository.findById(apiKeyId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> apiKeyService.revokeApiKey(apiKeyId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("API key not found");
    }

    @Test
    void revokeApiKey_DifferentUser_ThrowsForbiddenException() {
        // Given
        ApiKey otherUserApiKey = createTestApiKey(apiKeyId, createTestUser(otherUserId));
        when(apiKeyRepository.findByIdAndUserId(apiKeyId, userId)).thenReturn(Optional.empty());
        when(apiKeyRepository.findById(apiKeyId)).thenReturn(Optional.of(otherUserApiKey));

        // When/Then
        assertThatThrownBy(() -> apiKeyService.revokeApiKey(apiKeyId, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("API key belongs to another user");
    }

    // ==================== UpdateLastUsedAt Error Contract Tests
    // ====================

    @Test
    void updateLastUsedAt_NullId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.updateLastUsedAt(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("API key ID cannot be null");
    }

    // ==================== Helper Methods ====================

    private User createTestUser(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPasswordHash("hashedPassword");
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        return user;
    }

    private ApiKey createTestApiKey(UUID apiKeyId, User user) {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(apiKeyId);
        apiKey.setKey("test-api-key-1234567890123456");
        apiKey.setUser(user);
        apiKey.setName("Test API Key");
        apiKey.setActive(true);
        apiKey.setCreatedAt(Instant.now());
        apiKey.setExpiresAt(Instant.now().plusSeconds(30 * 24 * 60 * 60));
        return apiKey;
    }

    private ApiKeyRequest createTestApiKeyRequest(String name, Instant expiresAt) {
        ApiKeyRequest request = new ApiKeyRequest();
        request.setName(name);
        // Convert Instant to days if provided
        if (expiresAt != null) {
            long days = (expiresAt.getEpochSecond() - Instant.now().getEpochSecond()) / (24 * 60 * 60);
            // Round to nearest allowed value (30, 60, or 90)
            if (days <= 30) {
                request.setExpiresInDays(30);
            } else if (days <= 60) {
                request.setExpiresInDays(60);
            } else {
                request.setExpiresInDays(90);
            }
        }
        return request;
    }
}
