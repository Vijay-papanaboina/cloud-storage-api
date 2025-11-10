package github.vijay_papanaboina.cloud_storage_api.exception;

public class ForbiddenException extends CloudStorageApiException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}

