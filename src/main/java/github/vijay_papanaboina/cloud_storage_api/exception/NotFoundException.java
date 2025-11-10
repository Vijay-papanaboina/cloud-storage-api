package github.vijay_papanaboina.cloud_storage_api.exception;

import java.util.UUID;

public class NotFoundException extends CloudStorageApiException {

    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }

    public NotFoundException(String message, UUID id) {
        super("NOT_FOUND", message, id);
    }

    public NotFoundException(String message, Throwable cause) {
        super("NOT_FOUND", message, cause);
    }
}
