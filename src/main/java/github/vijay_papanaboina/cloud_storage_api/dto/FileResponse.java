package github.vijay_papanaboina.cloud_storage_api.dto;

import java.time.Instant;
import java.util.UUID;

public class FileResponse {
    private UUID id;
    private String filename;
    private String contentType;
    private Long fileSize;
    private String folderPath;
    private String cloudinaryUrl;
    private String cloudinarySecureUrl;
    private Instant createdAt;
    private Instant updatedAt;

    // Constructors
    public FileResponse() {
    }

    public FileResponse(UUID id, String filename, String contentType, Long fileSize, String folderPath,
            String cloudinaryUrl, String cloudinarySecureUrl, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.filename = filename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.folderPath = folderPath;
        this.cloudinaryUrl = cloudinaryUrl;
        this.cloudinarySecureUrl = cloudinarySecureUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getCloudinaryUrl() {
        return cloudinaryUrl;
    }

    public void setCloudinaryUrl(String cloudinaryUrl) {
        this.cloudinaryUrl = cloudinaryUrl;
    }

    public String getCloudinarySecureUrl() {
        return cloudinarySecureUrl;
    }

    public void setCloudinarySecureUrl(String cloudinarySecureUrl) {
        this.cloudinarySecureUrl = cloudinarySecureUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
