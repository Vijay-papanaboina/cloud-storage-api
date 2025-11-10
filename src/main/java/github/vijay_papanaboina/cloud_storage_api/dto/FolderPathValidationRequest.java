package github.vijay_papanaboina.cloud_storage_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class FolderPathValidationRequest {
    @NotBlank(message = "Folder path is required")
    @Size(max = 500, message = "Folder path must not exceed 500 characters")
    @Pattern(regexp = "^/([a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*)?$", message = "Folder path must start with '/' and contain only valid characters (alphanumeric, underscore, hyphen)")
    private String path;

    @Size(max = 1000, message = "Folder description must not exceed 1000 characters")
    private String description;

    // Constructors
    public FolderPathValidationRequest() {
    }

    public FolderPathValidationRequest(String path, String description) {
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
}
