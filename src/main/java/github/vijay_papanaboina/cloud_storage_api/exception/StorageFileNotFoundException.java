package github.vijay_papanaboina.cloud_storage_api.exception;

import java.util.UUID;

public class StorageFileNotFoundException extends CloudStorageApiException {

    public StorageFileNotFoundException(UUID fileId) {
        super("FILE_NOT_FOUND",
                String.format("File with ID %s not found", fileId),
                fileId);
    }
}
