package github.vijay_papanaboina.cloud_storage_api.model;

/**
 * Enum representing the type of JWT token.
 * Used to determine token expiration times and other token-specific behavior.
 */
public enum TokenType {
    /**
     * Access token.
     * Short-lived token used for API authentication.
     */
    ACCESS,

    /**
     * Refresh token.
     * Long-lived token used to obtain new access tokens.
     */
    REFRESH
}
