package github.vijay_papanaboina.cloud_storage_api.exception;

public class UnauthorizedException extends CloudStorageApiException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super("UNAUTHORIZED", message, cause);
    }
}
