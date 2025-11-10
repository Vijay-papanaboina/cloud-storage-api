package github.vijay_papanaboina.cloud_storage_api.dto;

import java.util.UUID;

public class BulkUploadResponse {
    private UUID batchId;
    private String jobType;
    private String status;
    private Integer totalItems;
    private String message;
    private String statusUrl;

    // Constructors
    public BulkUploadResponse() {
    }

    public BulkUploadResponse(UUID batchId, String jobType, String status, Integer totalItems, String message,
            String statusUrl) {
        this.batchId = batchId;
        this.jobType = jobType;
        this.status = status;
        this.totalItems = totalItems;
        this.message = message;
        this.statusUrl = statusUrl;
    }

    // Getters and Setters
    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatusUrl() {
        return statusUrl;
    }

    public void setStatusUrl(String statusUrl) {
        this.statusUrl = statusUrl;
    }
}
