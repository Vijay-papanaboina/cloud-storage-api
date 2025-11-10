package github.vijay_papanaboina.cloud_storage_api.dto;

public class FileUrlResponse {
    private String url;
    private String publicId;
    private String format;
    private String resourceType;

    // Constructors
    public FileUrlResponse() {
    }

    public FileUrlResponse(String url, String publicId, String format, String resourceType) {
        this.url = url;
        this.publicId = publicId;
        this.format = format;
        this.resourceType = resourceType;
    }

    // Getters and Setters
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
}
