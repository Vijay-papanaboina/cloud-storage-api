package github.vijay_papanaboina.cloud_storage_api.exception;

/**
 * Exception thrown when authorization fails due to system-level issues,
 * such as invalid/expired tokens, permission service unreachable, or missing
 * token scopes.
 * This is distinct from
 * {@link org.springframework.security.access.AccessDeniedException},
 * which is used for business-level ownership/permission denials.
 */
public class AuthorizationException extends CloudStorageApiException {
    private static final String ERROR_CODE = "AUTHORIZATION_FAILED";

    public AuthorizationException(String message) {
        super(ERROR_CODE, message);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
