package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.ApiKeyRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.ApiKeyResponse;
import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import github.vijay_papanaboina.cloud_storage_api.exception.ForbiddenException;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for API key management operations.
 */
public interface ApiKeyService {
    /**
     * Generate a new API key for the authenticated user.
     *
     * @param request API key request with name and optional expiration date
     * @param userId  User ID of the authenticated user
     * @return ApiKeyResponse with the generated API key (key value included)
     * @throws ResourceNotFoundException if user is not found
     * @throws BadRequestException       if request validation fails or user is inactive
     */
    ApiKeyResponse generateApiKey(ApiKeyRequest request, UUID userId);

    /**
     * List all API keys for the authenticated user.
     * API key values are not included in the response for security.
     *
     * @param userId User ID of the authenticated user
     * @return List of ApiKeyResponse (without key values)
     * @throws ResourceNotFoundException if user is not found
     */
    List<ApiKeyResponse> listApiKeys(UUID userId);

    /**
     * Get API key details by ID.
     * Only returns API keys that belong to the authenticated user.
     * API key value is not included in the response for security.
     *
     * @param id     API key ID
     * @param userId User ID of the authenticated user
     * @return ApiKeyResponse (without key value)
     * @throws ResourceNotFoundException if API key is not found
     * @throws ForbiddenException       if API key belongs to another user
     */
    ApiKeyResponse getApiKey(UUID id, UUID userId);

    /**
     * Revoke (deactivate) an API key.
     * Only allows revoking API keys that belong to the authenticated user.
     *
     * @param id     API key ID
     * @param userId User ID of the authenticated user
     * @throws ResourceNotFoundException if API key is not found
     * @throws ForbiddenException       if API key belongs to another user
     */
    void revokeApiKey(UUID id, UUID userId);
}

