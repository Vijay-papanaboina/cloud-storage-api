package github.vijay_papanaboina.cloud_storage_api.security;

import github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException;
import github.vijay_papanaboina.cloud_storage_api.model.ClientType;
import github.vijay_papanaboina.cloud_storage_api.model.TokenType;

import java.util.UUID;

public interface JwtTokenProvider {
    /**
     * Generate access token for a user
     *
     * @param userId     User ID
     * @param username   Username
     * @param clientType Client type (CLI or WEB)
     * @return JWT access token
     */
    String generateAccessToken(UUID userId, String username, ClientType clientType);

    /**
     * Generate refresh token for a user
     *
     * @param userId     User ID
     * @param username   Username
     * @param clientType Client type (CLI or WEB)
     * @return JWT refresh token
     */
    String generateRefreshToken(UUID userId, String username, ClientType clientType);

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
     * Extract client type from JWT token
     *
     * @param token JWT token
     * @return Client type (CLI or WEB), defaults to WEB if not present
     * @throws InvalidTokenException if the token is invalid, expired, or malformed
     */
    ClientType getClientTypeFromToken(String token) throws InvalidTokenException;

    /**
     * Get token expiration time in milliseconds based on client type and token type
     * <p>
     * Implementations must support all enum values for both {@code clientType} and
     * {@code tokenType}. Implementations must throw
     * {@link IllegalArgumentException}
     * for null parameters or unsupported combinations.
     *
     * @param clientType Client type (CLI or WEB), must not be null
     * @param tokenType  Token type (ACCESS or REFRESH), must not be null
     * @return Expiration time in milliseconds
     * @throws IllegalArgumentException if {@code clientType} or {@code tokenType}
     *                                  is null,
     *                                  or if the combination is not supported
     */
    long getTokenExpiration(ClientType clientType, TokenType tokenType);
}
