package github.vijay_papanaboina.cloud_storage_api.dto;

import github.vijay_papanaboina.cloud_storage_api.validation.SafeFolderPath;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a folder.
 */
public class FolderCreateRequest {

    @NotBlank(message = "Folder path is required")
    @Size(max = 500, message = "Folder path must not exceed 500 characters")
    @SafeFolderPath
    private String path;

    @Size(max = 1000, message = "Folder description must not exceed 1000 characters")
    private String description;

    // Constructors
    public FolderCreateRequest() {
    }

    public FolderCreateRequest(String path, String description) {
        this.path = path;
        this.description = description;
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

    @Override
    public String toString() {
        return "FolderCreateRequest{" +
                "path='" + path + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
