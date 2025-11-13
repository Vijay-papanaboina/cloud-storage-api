package github.vijay_papanaboina.cloud_storage_api.security;

import github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException;
import github.vijay_papanaboina.cloud_storage_api.model.TokenType;

import java.util.UUID;

public interface JwtTokenProvider {
    /**
     * Generate access token for a user
     *
     * @param userId   User ID
     * @param username Username
     * @return JWT access token
     */
    String generateAccessToken(UUID userId, String username);

    /**
     * Generate access token for a user with authorities
     *
     * @param userId      User ID
     * @param username    Username
     * @param authorities List of authority strings (e.g., "ROLE_READ",
     *                    "ROLE_WRITE")
     * @return JWT access token
     */
    String generateAccessToken(UUID userId, String username, java.util.List<String> authorities);

    /**
     * Generate refresh token for a user
     *
     * @param userId   User ID
     * @param username Username
     * @return JWT refresh token
     */
    String generateRefreshToken(UUID userId, String username);

    /**
     * Validate a JWT token
     *
     * @param token JWT token to validate
     * @return true if token is valid, false otherwise
     */
    boolean validateToken(String token);

    /**
     * Extract user ID from JWT token
     *
     * @param token JWT token
     * @return User ID
     * @throws InvalidTokenException if the token is invalid, expired, or malformed
     */
    UUID getUserIdFromToken(String token) throws InvalidTokenException;

    /**
     * Extract username from JWT token
     *
     * @param token JWT token
     * @return Username
     * @throws InvalidTokenException if the token is invalid, expired, or malformed
     */
    String getUsernameFromToken(String token) throws InvalidTokenException;

    /**
     * Extract authorities from JWT token
     *
     * @param token JWT token
     * @return List of authority strings
     * @throws InvalidTokenException if the token is invalid, expired, or malformed
     */
    java.util.List<String> getAuthoritiesFromToken(String token) throws InvalidTokenException;

    /**
     * Get token expiration time in milliseconds based on token type
     *
     * @param tokenType Token type (ACCESS or REFRESH), must not be null
     * @return Expiration time in milliseconds
     * @throws IllegalArgumentException if {@code tokenType} is null
     */
    long getTokenExpiration(TokenType tokenType);
}
