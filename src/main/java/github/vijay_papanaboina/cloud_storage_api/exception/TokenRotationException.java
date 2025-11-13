package github.vijay_papanaboina.cloud_storage_api.exception;

public class TokenRotationException extends CloudStorageApiException {

    public TokenRotationException(String message) {
        super("TOKEN_ROTATION_ERROR", message);
    }

    public TokenRotationException(String message, Throwable cause) {
        super("TOKEN_ROTATION_ERROR", message, cause);
    }
}

