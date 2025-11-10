package github.vijay_papanaboina.cloud_storage_api.dto;

public class FolderPathValidationResult {
    private String path;
    private boolean isValid;
    private boolean exists;
    private String message;
    private Long fileCount;

    // Constructors
    public FolderPathValidationResult() {
    }

    public FolderPathValidationResult(String path, boolean isValid, boolean exists, String message, Long fileCount) {
        this.path = path;
        this.isValid = isValid;
        this.exists = exists;
        this.message = message;
        this.fileCount = fileCount;
    }

    // Getters and Setters
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public boolean isExists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getFileCount() {
        return fileCount;
    }

    public void setFileCount(Long fileCount) {
        this.fileCount = fileCount;
    }
}
