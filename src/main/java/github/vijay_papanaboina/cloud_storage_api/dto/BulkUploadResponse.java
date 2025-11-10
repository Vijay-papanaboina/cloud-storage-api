package github.vijay_papanaboina.cloud_storage_api.dto;

import java.util.UUID;

public class BulkUploadResponse {
    private UUID batchId;
    private String jobType;
    private String status;
    private Integer totalItems;
    private Integer processedItems;
    private Integer failedItems;
    private Integer progress;
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

    public BulkUploadResponse(UUID batchId, String jobType, String status, Integer totalItems, Integer processedItems,
            Integer failedItems, Integer progress, String message, String statusUrl) {
        this.batchId = batchId;
        this.jobType = jobType;
        this.status = status;
        this.totalItems = totalItems;
        this.processedItems = processedItems;
        this.failedItems = failedItems;
        this.progress = progress;
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

    public Integer getProcessedItems() {
        return processedItems;
    }

    public void setProcessedItems(Integer processedItems) {
        this.processedItems = processedItems;
    }

    public Integer getFailedItems() {
        return failedItems;
    }

    public void setFailedItems(Integer failedItems) {
        this.failedItems = failedItems;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }
}
