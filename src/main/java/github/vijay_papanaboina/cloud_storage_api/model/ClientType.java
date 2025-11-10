package github.vijay_papanaboina.cloud_storage_api.model;

import java.time.Duration;

/**
 * Enum representing the type of client making the request.
 * Used to determine token expiration times and other client-specific behavior.
 */
public enum ClientType {
    /**
     * Command-line interface client.
     * Uses longer token expiration times (1 day for access, 90 days for refresh).
     */
    CLI(Duration.ofDays(1), Duration.ofDays(90)),
    /**
     * Web application client.
     * Uses shorter token expiration times (15 minutes for access, 7 days for
     * refresh).
     */
    WEB(Duration.ofMinutes(15), Duration.ofDays(7));

    private final Duration accessTokenExpiration;
    private final Duration refreshTokenExpiration;

    ClientType(Duration accessTokenExpiration, Duration refreshTokenExpiration) {
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public Duration getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public Duration getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}
