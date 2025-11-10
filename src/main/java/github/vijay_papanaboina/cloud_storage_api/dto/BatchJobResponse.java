package github.vijay_papanaboina.cloud_storage_api.dto;

import github.vijay_papanaboina.cloud_storage_api.model.BatchJob;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for batch job status information.
 */
public class BatchJobResponse {
    private UUID batchId;
    private String jobType; // UPLOAD, DELETE, TRANSFORM
    private String status; // QUEUED, PROCESSING, COMPLETED, FAILED, CANCELLED
    private Integer totalItems;
    private Integer processedItems;
    private Integer failedItems;
    private Integer progress; // 0-100
    private Instant startedAt;
    private Instant completedAt;
    private Instant estimatedCompletion;
    private String errorMessage;

    // Constructors
    public BatchJobResponse() {
    }

    public BatchJobResponse(UUID batchId, String jobType, String status, Integer totalItems,
            Integer processedItems, Integer failedItems, Integer progress, Instant startedAt,
            Instant completedAt, Instant estimatedCompletion, String errorMessage) {
        this.batchId = batchId;
        this.jobType = jobType;
        this.status = status;
        this.totalItems = totalItems;
        this.processedItems = processedItems;
        this.failedItems = failedItems;
        this.progress = progress;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.estimatedCompletion = estimatedCompletion;
        this.errorMessage = errorMessage;
    }

    /**
     * Factory method to create BatchJobResponse from BatchJob entity.
     *
     * @param batchJob The batch job entity
     * @return BatchJobResponse DTO
     */
    public static BatchJobResponse from(BatchJob batchJob) {
        if (batchJob == null) {
            throw new IllegalArgumentException("batchJob cannot be null");
        }
        BatchJobResponse response = new BatchJobResponse();
        response.setBatchId(batchJob.getId());
        response.setJobType(batchJob.getJobType() != null ? batchJob.getJobType().name() : null);
        response.setStatus(batchJob.getStatus() != null ? batchJob.getStatus().name() : null);
        response.setTotalItems(batchJob.getTotalItems());
        response.setProcessedItems(batchJob.getProcessedItems());
        response.setFailedItems(batchJob.getFailedItems());
        response.setProgress(batchJob.getProgress());
        response.setStartedAt(batchJob.getStartedAt());
        response.setCompletedAt(batchJob.getCompletedAt());
        response.setErrorMessage(batchJob.getErrorMessage());

        // Calculate estimated completion if processing
        if (batchJob.getStatus() != null
                && batchJob.getStatus().name().equals("PROCESSING")
                && batchJob.getStartedAt() != null
                && batchJob.getProcessedItems() != null
                && batchJob.getTotalItems() != null
                && batchJob.getProcessedItems() > 0
                && batchJob.getProcessedItems() < batchJob.getTotalItems()) {
            // Capture current time once to ensure consistency and avoid race conditions
            Instant now = Instant.now();
            long elapsed = now.toEpochMilli() - batchJob.getStartedAt().toEpochMilli();
            int remaining = batchJob.getTotalItems() - batchJob.getProcessedItems();

            // Calculate average time per item first to avoid overflow
            // Use double for precision when dividing
            double averageTimePerItem = (double) elapsed / batchJob.getProcessedItems();

            // Multiply average by remaining to get estimated milliseconds
            long estimatedMillis = Math.round(averageTimePerItem * remaining);

            // Use the captured 'now' for consistency
            response.setEstimatedCompletion(now.plusMillis(estimatedMillis));
        }

        return response;
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

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getEstimatedCompletion() {
        return estimatedCompletion;
    }

    public void setEstimatedCompletion(Instant estimatedCompletion) {
        this.estimatedCompletion = estimatedCompletion;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "BatchJobResponse{" +
                "batchId=" + batchId +
                ", jobType='" + jobType + '\'' +
                ", status='" + status + '\'' +
                ", totalItems=" + totalItems +
                ", processedItems=" + processedItems +
                ", failedItems=" + failedItems +
                ", progress=" + progress +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                ", estimatedCompletion=" + estimatedCompletion +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
