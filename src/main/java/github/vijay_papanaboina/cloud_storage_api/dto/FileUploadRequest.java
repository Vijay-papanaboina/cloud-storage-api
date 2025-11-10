package github.vijay_papanaboina.cloud_storage_api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class FileUploadRequest {
    @Size(max = 500, message = "Folder path must not exceed 500 characters")
    @Pattern(regexp = "^/.*|^$", message = "Folder path must start with '/' if provided")
    private String folderPath;

    // Constructors
    public FileUploadRequest() {
    }

    public FileUploadRequest(String folderPath) {
        this.folderPath = folderPath;
    }

    // Getters and Setters
    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }
}
