package github.vijay_papanaboina.cloud_storage_api.dto;

import java.time.Instant;

public class FileUrlResponse {
    private String url;
    private String publicId;
    private String format;
    private String resourceType;
    private Instant expiresAt; // Expiration timestamp

    // Constructors
    public FileUrlResponse() {
    }

    public FileUrlResponse(String url, String publicId, String format, String resourceType) {
        this.url = url;
        this.publicId = publicId;
        this.format = format;
        this.resourceType = resourceType;
    }

    public FileUrlResponse(String url, String publicId, String format, String resourceType,
            Instant expiresAt) {
        this.url = url;
        this.publicId = publicId;
        this.format = format;
        this.resourceType = resourceType;
        this.expiresAt = expiresAt;
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

    /**
     * Computes the expiration time in minutes from now until expiresAt.
     * Returns null if expiresAt is null or has already passed.
     * 
     * @return the number of minutes until expiration, or null if not set or expired
     */
    public Integer getExpiresIn() {
        if (expiresAt == null) {
            return null;
        }
        long secondsUntilExpiration = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        if (secondsUntilExpiration <= 0) {
            return null; // Already expired
        }
        return (int) Math.ceil(secondsUntilExpiration / 60.0);
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
