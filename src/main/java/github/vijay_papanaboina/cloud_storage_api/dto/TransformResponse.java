package github.vijay_papanaboina.cloud_storage_api.dto;

public class TransformResponse {
    private String transformedUrl;
    private String originalUrl;

    // Constructors
    public TransformResponse() {
    }

    public TransformResponse(String transformedUrl, String originalUrl) {
        this.transformedUrl = transformedUrl;
        this.originalUrl = originalUrl;
    }

    // Getters and Setters
    public String getTransformedUrl() {
        return transformedUrl;
    }

    public void setTransformedUrl(String transformedUrl) {
        this.transformedUrl = transformedUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }
}
