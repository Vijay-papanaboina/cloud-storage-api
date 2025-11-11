package github.vijay_papanaboina.cloud_storage_api.controller;

import github.vijay_papanaboina.cloud_storage_api.dto.*;
import github.vijay_papanaboina.cloud_storage_api.security.SecurityUtils;
import github.vijay_papanaboina.cloud_storage_api.service.ApiKeyService;
import github.vijay_papanaboina.cloud_storage_api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for authentication and API key management.
 * Handles user authentication (login, register, refresh, logout) and API key operations.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ApiKeyService apiKeyService;

    @Autowired
    public AuthController(AuthService authService, ApiKeyService apiKeyService) {
        this.authService = authService;
        this.apiKeyService = apiKeyService;
    }

    /**
     * Login endpoint.
     * Authenticate user and receive JWT tokens.
     *
     * @param request Login request with username, password, and optional clientType
     * @return AuthResponse with access token, refresh token, and user information
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Register endpoint.
     * Register a new user.
     *
     * @param request Registration request with username, email, and password
     * @return UserResponse with created user information
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Refresh token endpoint.
     * Refresh access token using refresh token.
     *
     * @param request Refresh token request
     * @return RefreshTokenResponse with new access token
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint.
     * Logout user and invalidate refresh token.
     *
     * @param request Refresh token request containing the refresh token to invalidate
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Get current user endpoint.
     * Get current authenticated user information.
     *
     * @return UserResponse with user information
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        UserResponse response = authService.getCurrentUser(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Generate API key endpoint.
     * Generate a new API key for the authenticated user.
     *
     * @param request API key request with name and optional expiration date
     * @return ApiKeyResponse with the generated API key (key value included)
     */
    @PostMapping("/api-keys")
    public ResponseEntity<ApiKeyResponse> generateApiKey(@Valid @RequestBody ApiKeyRequest request) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        ApiKeyResponse response = apiKeyService.generateApiKey(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List API keys endpoint.
     * List all API keys for the authenticated user.
     *
     * @return List of ApiKeyResponse (without key values)
     */
    @GetMapping("/api-keys")
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys() {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        List<ApiKeyResponse> response = apiKeyService.listApiKeys(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get API key details endpoint.
     * Get API key details by ID.
     *
     * @param id API key ID
     * @return ApiKeyResponse (without key value)
     */
    @GetMapping("/api-keys/{id}")
    public ResponseEntity<ApiKeyResponse> getApiKey(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        ApiKeyResponse response = apiKeyService.getApiKey(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Revoke API key endpoint.
     * Revoke (deactivate) an API key.
     *
     * @param id API key ID
     * @return 204 No Content
     */
    @DeleteMapping("/api-keys/{id}")
    public ResponseEntity<Void> revokeApiKey(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        apiKeyService.revokeApiKey(id, userId);
        return ResponseEntity.noContent().build();
    }
}

