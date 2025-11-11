package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.ApiKeyRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.ApiKeyResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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

    // generateApiKey tests
    @Test
    void generateApiKey_Success() {
        // Given
        ApiKeyRequest request = createTestApiKeyRequest("Test API Key", Instant.now().plus(Duration.ofDays(30)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(apiKeyRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey apiKey = invocation.getArgument(0);
            apiKey.setId(UUID.randomUUID());
            return apiKey;
        });

        // When
        ApiKeyResponse response = apiKeyService.generateApiKey(request, userId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getKey()).isNotNull();
        assertThat(response.getKey()).hasSize(32);
        assertThat(response.getName()).isEqualTo("Test API Key");
        assertThat(response.getActive()).isTrue();
        assertThat(response.getExpiresAt()).isEqualTo(request.getExpiresAt());
        assertThat(response.getCreatedAt()).isNotNull();

        verify(userRepository, times(1)).findById(userId);
        verify(apiKeyRepository, atLeastOnce()).findByKey(anyString());
        verify(apiKeyRepository, times(1)).save(any(ApiKey.class));
    }

    @Test
    void generateApiKey_WithoutExpiration_Success() {
        // Given
        ApiKeyRequest request = createTestApiKeyRequest("Test API Key", null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(apiKeyRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey apiKey = invocation.getArgument(0);
            apiKey.setId(UUID.randomUUID());
            return apiKey;
        });

        // When
        ApiKeyResponse response = apiKeyService.generateApiKey(request, userId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getExpiresAt()).isNull();

        verify(userRepository, times(1)).findById(userId);
        verify(apiKeyRepository, times(1)).save(any(ApiKey.class));
    }

    @Test
    void generateApiKey_NullRequest_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.generateApiKey(null, userId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Request cannot be null");

        verify(userRepository, never()).findById(any());
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void generateApiKey_NullName_ThrowsException() {
        // Given
        ApiKeyRequest request = createTestApiKeyRequest(null, null);

        // When/Then
        assertThatThrownBy(() -> apiKeyService.generateApiKey(request, userId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("API key name cannot be null");

        verify(userRepository, never()).findById(any());
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void generateApiKey_NullUserId_ThrowsException() {
        // Given
        ApiKeyRequest request = createTestApiKeyRequest("Test API Key", null);

        // When/Then
        assertThatThrownBy(() -> apiKeyService.generateApiKey(request, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("User ID cannot be null");

        verify(userRepository, never()).findById(any());
        verify(apiKeyRepository, never()).save(any());
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

        verify(userRepository, times(1)).findById(userId);
        verify(apiKeyRepository, never()).save(any());
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

        verify(userRepository, times(1)).findById(userId);
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void generateApiKey_PastExpiration_ThrowsBadRequestException() {
        // Given
        ApiKeyRequest request = createTestApiKeyRequest("Test API Key", Instant.now().minus(Duration.ofDays(1)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When/Then
        assertThatThrownBy(() -> apiKeyService.generateApiKey(request, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Expiration date cannot be in the past");

        verify(userRepository, times(1)).findById(userId);
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void generateApiKey_KeyCollision_Retry_Success() {
        // Given
        ApiKeyRequest request = createTestApiKeyRequest("Test API Key", null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        // First call returns existing key (collision), second call returns empty (success)
        when(apiKeyRepository.findByKey(anyString()))
                .thenReturn(Optional.of(createTestApiKey(UUID.randomUUID(), testUser)))
                .thenReturn(Optional.empty());
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey apiKey = invocation.getArgument(0);
            apiKey.setId(UUID.randomUUID());
            return apiKey;
        });

        // When
        ApiKeyResponse response = apiKeyService.generateApiKey(request, userId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getKey()).isNotNull();
        // Verify retry happened (at least 2 calls to findByKey)
        verify(apiKeyRepository, atLeast(2)).findByKey(anyString());
        verify(apiKeyRepository, times(1)).save(any(ApiKey.class));
    }

    // listApiKeys tests
    @Test
    void listApiKeys_Success_ReturnsMultiple() {
        // Given
        List<ApiKey> apiKeys = new ArrayList<>();
        apiKeys.add(createTestApiKey(UUID.randomUUID(), testUser));
        apiKeys.add(createTestApiKey(UUID.randomUUID(), testUser));

        when(userRepository.existsById(userId)).thenReturn(true);
        when(apiKeyRepository.findByUserId(userId)).thenReturn(apiKeys);

        // When
        List<ApiKeyResponse> responses = apiKeyService.listApiKeys(userId);

        // Then
        assertThat(responses).isNotNull();
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getKey()).isNull(); // Key value should not be included
        assertThat(responses.get(1).getKey()).isNull();

        verify(userRepository, times(1)).existsById(userId);
        verify(apiKeyRepository, times(1)).findByUserId(userId);
    }

    @Test
    void listApiKeys_Success_ReturnsEmpty() {
        // Given
        when(userRepository.existsById(userId)).thenReturn(true);
        when(apiKeyRepository.findByUserId(userId)).thenReturn(new ArrayList<>());

        // When
        List<ApiKeyResponse> responses = apiKeyService.listApiKeys(userId);

        // Then
        assertThat(responses).isNotNull();
        assertThat(responses).isEmpty();

        verify(userRepository, times(1)).existsById(userId);
        verify(apiKeyRepository, times(1)).findByUserId(userId);
    }

    @Test
    void listApiKeys_NullUserId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.listApiKeys(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("User ID cannot be null");

        verify(userRepository, never()).existsById(any());
        verify(apiKeyRepository, never()).findByUserId(any());
    }

    @Test
    void listApiKeys_UserNotFound_ThrowsResourceNotFoundException() {
        // Given
        when(userRepository.existsById(userId)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> apiKeyService.listApiKeys(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, times(1)).existsById(userId);
        verify(apiKeyRepository, never()).findByUserId(any());
    }

    @Test
    void listApiKeys_KeyValuesNotIncluded() {
        // Given
        List<ApiKey> apiKeys = new ArrayList<>();
        ApiKey apiKey = createTestApiKey(UUID.randomUUID(), testUser);
        apiKey.setKey("test-key-value-1234567890123456");
        apiKeys.add(apiKey);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(apiKeyRepository.findByUserId(userId)).thenReturn(apiKeys);

        // When
        List<ApiKeyResponse> responses = apiKeyService.listApiKeys(userId);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getKey()).isNull(); // Key value must not be included

        verify(apiKeyRepository, times(1)).findByUserId(userId);
    }

    // getApiKey tests
    @Test
    void getApiKey_Success() {
        // Given
        when(apiKeyRepository.findByIdAndUserId(apiKeyId, userId)).thenReturn(Optional.of(testApiKey));

        // When
        ApiKeyResponse response = apiKeyService.getApiKey(apiKeyId, userId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(apiKeyId);
        assertThat(response.getName()).isEqualTo(testApiKey.getName());
        assertThat(response.getActive()).isEqualTo(testApiKey.getActive());
        assertThat(response.getCreatedAt()).isEqualTo(testApiKey.getCreatedAt());
        assertThat(response.getExpiresAt()).isEqualTo(testApiKey.getExpiresAt());

        verify(apiKeyRepository, times(1)).findByIdAndUserId(apiKeyId, userId);
    }

    @Test
    void getApiKey_NullId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.getApiKey(null, userId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("API key ID cannot be null");

        verify(apiKeyRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void getApiKey_NullUserId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.getApiKey(apiKeyId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("User ID cannot be null");

        verify(apiKeyRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void getApiKey_NotFound_ThrowsResourceNotFoundException() {
        // Given
        when(apiKeyRepository.findByIdAndUserId(apiKeyId, userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> apiKeyService.getApiKey(apiKeyId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("API key not found");

        verify(apiKeyRepository, times(1)).findByIdAndUserId(apiKeyId, userId);
    }

    @Test
    void getApiKey_KeyValueNotIncluded() {
        // Given
        testApiKey.setKey("test-key-value-1234567890123456");
        when(apiKeyRepository.findByIdAndUserId(apiKeyId, userId)).thenReturn(Optional.of(testApiKey));

        // When
        ApiKeyResponse response = apiKeyService.getApiKey(apiKeyId, userId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getKey()).isNull(); // Key value must not be included

        verify(apiKeyRepository, times(1)).findByIdAndUserId(apiKeyId, userId);
    }

    // revokeApiKey tests
    @Test
    void revokeApiKey_Success() {
        // Given
        testApiKey.setActive(true);
        when(apiKeyRepository.findByIdAndUserId(apiKeyId, userId)).thenReturn(Optional.of(testApiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

        // When
        apiKeyService.revokeApiKey(apiKeyId, userId);

        // Then
        ArgumentCaptor<ApiKey> apiKeyCaptor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository, times(1)).findByIdAndUserId(apiKeyId, userId);
        verify(apiKeyRepository, times(1)).save(apiKeyCaptor.capture());
        assertThat(apiKeyCaptor.getValue().getActive()).isFalse();
    }

    @Test
    void revokeApiKey_NullId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.revokeApiKey(null, userId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("API key ID cannot be null");

        verify(apiKeyRepository, never()).findByIdAndUserId(any(), any());
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void revokeApiKey_NullUserId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.revokeApiKey(apiKeyId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("User ID cannot be null");

        verify(apiKeyRepository, never()).findByIdAndUserId(any(), any());
        verify(apiKeyRepository, never()).save(any());
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

        verify(apiKeyRepository, times(1)).findByIdAndUserId(apiKeyId, userId);
        verify(apiKeyRepository, times(1)).findById(apiKeyId);
        verify(apiKeyRepository, never()).save(any());
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

        verify(apiKeyRepository, times(1)).findByIdAndUserId(apiKeyId, userId);
        verify(apiKeyRepository, times(1)).findById(apiKeyId);
        verify(apiKeyRepository, never()).save(any());
    }

    // updateLastUsedAt tests
    @Test
    void updateLastUsedAt_Success() {
        // Given
        when(apiKeyRepository.findById(apiKeyId)).thenReturn(Optional.of(testApiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

        // When
        apiKeyService.updateLastUsedAt(apiKeyId);

        // Then
        ArgumentCaptor<ApiKey> apiKeyCaptor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository, times(1)).findById(apiKeyId);
        verify(apiKeyRepository, times(1)).save(apiKeyCaptor.capture());
        assertThat(apiKeyCaptor.getValue().getLastUsedAt()).isNotNull();
    }

    @Test
    void updateLastUsedAt_NullId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> apiKeyService.updateLastUsedAt(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("API key ID cannot be null");

        verify(apiKeyRepository, never()).findById(any());
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void updateLastUsedAt_NotFound_HandlesGracefully() {
        // Given
        when(apiKeyRepository.findById(apiKeyId)).thenReturn(Optional.empty());

        // When
        apiKeyService.updateLastUsedAt(apiKeyId);

        // Then - should not throw exception, just log warning
        verify(apiKeyRepository, times(1)).findById(apiKeyId);
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void updateLastUsedAt_ExceptionDuringUpdate_HandlesGracefully() {
        // Given
        when(apiKeyRepository.findById(apiKeyId)).thenReturn(Optional.of(testApiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenThrow(new RuntimeException("Database error"));

        // When - should not throw exception, just log warning
        apiKeyService.updateLastUsedAt(apiKeyId);

        // Then
        verify(apiKeyRepository, times(1)).findById(apiKeyId);
        verify(apiKeyRepository, times(1)).save(any(ApiKey.class));
    }

    // Helper methods
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
        apiKey.setExpiresAt(Instant.now().plus(Duration.ofDays(30)));
        return apiKey;
    }

    private ApiKeyRequest createTestApiKeyRequest(String name, Instant expiresAt) {
        ApiKeyRequest request = new ApiKeyRequest();
        request.setName(name);
        request.setExpiresAt(expiresAt);
        return request;
    }
}

