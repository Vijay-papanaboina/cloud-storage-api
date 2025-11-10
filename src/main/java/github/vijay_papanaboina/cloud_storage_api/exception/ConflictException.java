package github.vijay_papanaboina.cloud_storage_api.exception;

public class ConflictException extends CloudStorageApiException {

    public ConflictException(String message) {
        super("CONFLICT", message);
    }

    public ConflictException(String message, Throwable cause) {
        super("CONFLICT", message, cause);
    }
}

