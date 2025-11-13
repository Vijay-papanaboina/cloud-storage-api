package github.vijay_papanaboina.cloud_storage_api.controller;

import github.vijay_papanaboina.cloud_storage_api.dto.*;
import github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException;
import github.vijay_papanaboina.cloud_storage_api.exception.TokenRotationException;
import github.vijay_papanaboina.cloud_storage_api.model.TokenType;
import github.vijay_papanaboina.cloud_storage_api.security.CookieUtils;
import github.vijay_papanaboina.cloud_storage_api.security.JwtTokenProvider;
import github.vijay_papanaboina.cloud_storage_api.security.SecurityUtils;
import github.vijay_papanaboina.cloud_storage_api.service.ApiKeyService;
import github.vijay_papanaboina.cloud_storage_api.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for authentication and API key management.
 * Handles user authentication (login, register, refresh, logout) and API key
 * operations.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final ApiKeyService apiKeyService;
    private final CookieUtils cookieUtils;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public AuthController(AuthService authService, ApiKeyService apiKeyService, CookieUtils cookieUtils,
            JwtTokenProvider jwtTokenProvider) {
        this.authService = authService;
        this.apiKeyService = apiKeyService;
        this.cookieUtils = cookieUtils;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Login endpoint.
     * Authenticate user and receive JWT tokens.
     * Refresh token is set as httpOnly cookie for security.
     *
     * @param request  Login request with username and password
     * @param response HTTP response for setting cookies
     * @return AuthResponse with access token and user information (refresh token in
     *         cookie)
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        if (authResponse.getRefreshToken() == null || authResponse.getRefreshToken().isBlank()) {
            log.error("Login succeeded but refresh token is missing from auth response");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        // Set refresh token as httpOnly cookie
        long refreshTokenExpiration = jwtTokenProvider.getTokenExpiration(TokenType.REFRESH);
        int maxAge = (int) (refreshTokenExpiration / 1000); // Convert milliseconds to seconds
        cookieUtils.setRefreshTokenCookie(response, authResponse.getRefreshToken(), maxAge);

        // Remove refresh token from response body for security (keep in cookie only)
        authResponse.setRefreshToken(null);

        return ResponseEntity.ok(authResponse);
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
     * Refresh access token using refresh token from cookie or request body.
     * New refresh token is set as httpOnly cookie.
     *
     * @param request  HTTP request (for reading cookie)
     * @param response HTTP response (for setting cookie)
     * @param body     Refresh token request (optional, for backward compatibility)
     * @return RefreshTokenResponse with new access token
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refreshToken(HttpServletRequest request,
            HttpServletResponse response, @RequestBody(required = false) RefreshTokenRequest body) {
        // Read refresh token from cookie first, fallback to request body
        String refreshToken = cookieUtils.getRefreshTokenFromCookie(request);
        if (refreshToken == null && body != null && body.getRefreshToken() != null) {
            refreshToken = body.getRefreshToken();
        }

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Extract user info from the old refresh token BEFORE calling
        // authService.refreshToken()
        // to avoid TOCTOU race condition if the service invalidates the token
        UUID userId;
        String username;
        try {
            userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
            username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        } catch (InvalidTokenException e) {
            // Log extraction failure and return UNAUTHORIZED
            log.warn("Failed to extract user info from refresh token before refresh. " +
                    "Token may be invalid, expired, or malformed. Exception type: {}, message: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            // Handle any other unexpected errors during extraction
            log.error("Unexpected error extracting user info from refresh token. " +
                    "Exception type: {}, message: {}",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Now call authService.refreshToken() - token may be invalidated here
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);
        RefreshTokenResponse authResponse = authService.refreshToken(refreshRequest);

        // Generate new refresh token for token rotation using previously extracted user
        // info
        try {
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, username);

            // Set new refresh token as cookie
            long refreshTokenExpiration = jwtTokenProvider.getTokenExpiration(TokenType.REFRESH);
            int maxAge = (int) (refreshTokenExpiration / 1000);
            cookieUtils.setRefreshTokenCookie(response, newRefreshToken, maxAge);
        } catch (Exception e) {
            // Log the error with context since token rotation failed
            // This should only happen during rotation/generation, not extraction
            log.error("Token rotation failed during refresh token endpoint. " +
                    "Failed to generate new refresh token or set cookie. " +
                    "Exception type: {}, message: {}",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            throw new TokenRotationException(
                    "Failed to rotate refresh token. Please re-authenticate.", e);
        }

        return ResponseEntity.ok(authResponse);
    }

    /**
     * Logout endpoint.
     * Logout user and invalidate refresh token.
     * Reads refresh token from cookie or request body.
     *
     * @param request  HTTP request (for reading cookie)
     * @param response HTTP response (for clearing cookie)
     * @param body     Refresh token request (optional, for backward compatibility)
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
            @RequestBody(required = false) RefreshTokenRequest body) {
        // Read refresh token from cookie first, fallback to request body
        String refreshToken = cookieUtils.getRefreshTokenFromCookie(request);
        if (refreshToken == null && body != null && body.getRefreshToken() != null) {
            refreshToken = body.getRefreshToken();
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }

        // Clear refresh token cookie
        cookieUtils.clearRefreshTokenCookie(response);

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
        SecurityUtils.requirePermission("ROLE_MANAGE_API_KEYS");
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
        SecurityUtils.requirePermission("ROLE_MANAGE_API_KEYS");
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
        SecurityUtils.requirePermission("ROLE_MANAGE_API_KEYS");
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
        SecurityUtils.requirePermission("ROLE_MANAGE_API_KEYS");
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        apiKeyService.revokeApiKey(id, userId);
        return ResponseEntity.noContent().build();
    }
}
