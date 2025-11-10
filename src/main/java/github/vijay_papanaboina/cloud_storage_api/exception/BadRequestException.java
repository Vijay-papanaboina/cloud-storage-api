package github.vijay_papanaboina.cloud_storage_api.exception;

public class BadRequestException extends CloudStorageApiException {

    public BadRequestException(String message) {
        super("BAD_REQUEST", message);
    }

    public BadRequestException(String message, Throwable cause) {
        super("BAD_REQUEST", message, cause);
    }
}
