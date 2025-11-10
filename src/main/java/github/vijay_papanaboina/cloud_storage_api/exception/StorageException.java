package github.vijay_papanaboina.cloud_storage_api.exception;

public class StorageException extends CloudStorageApiException {

    public StorageException(String message) {
        super("STORAGE_ERROR", message);
    }

    public StorageException(String message, Throwable cause) {
        super("STORAGE_ERROR", message, cause);
    }
}
