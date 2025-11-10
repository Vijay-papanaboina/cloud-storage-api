package github.vijay_papanaboina.cloud_storage_api.exception;

import java.util.UUID;

public class ResourceNotFoundException extends CloudStorageApiException {

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }

    public ResourceNotFoundException(String message, UUID resourceId) {
        super("RESOURCE_NOT_FOUND", message, resourceId);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super("RESOURCE_NOT_FOUND", message, cause);
    }
}
