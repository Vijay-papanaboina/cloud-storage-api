package github.vijay_papanaboina.cloud_storage_api.dto;

import java.time.Instant;

public class FolderResponse {
    private String path;
    private String description;
    private Integer fileCount;
    private Instant createdAt;

    // Constructors
    public FolderResponse() {
    }

    public FolderResponse(String path, String description, Integer fileCount, Instant createdAt) {
        this.path = path;
        this.description = description;
        this.fileCount = fileCount;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getFileCount() {
        return fileCount;
    }

    public void setFileCount(Integer fileCount) {
        this.fileCount = fileCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
