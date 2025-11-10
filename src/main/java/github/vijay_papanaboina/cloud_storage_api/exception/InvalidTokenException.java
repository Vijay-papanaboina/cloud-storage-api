package github.vijay_papanaboina.cloud_storage_api.exception;

/**
 * Exception thrown when a JWT token is invalid, expired, or malformed.
 * This is a checked exception to ensure callers explicitly handle token
 * validation errors.
 */
public class InvalidTokenException extends Exception {

    private static final String DEFAULT_ERROR_CODE = "INVALID_TOKEN";

    private final String errorCode;

    public InvalidTokenException(String message) {
        super(message);
        this.errorCode = DEFAULT_ERROR_CODE;
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = DEFAULT_ERROR_CODE;
    }

    public InvalidTokenException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public InvalidTokenException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
