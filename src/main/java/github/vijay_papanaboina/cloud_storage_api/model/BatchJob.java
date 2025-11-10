package github.vijay_papanaboina.cloud_storage_api.model;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.Type;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "batch_jobs", indexes = {
        @Index(name = "idx_batch_job_type", columnList = "job_type"),
        @Index(name = "idx_batch_job_status", columnList = "status"),
        @Index(name = "idx_batch_job_created_at", columnList = "created_at")
})
public class BatchJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50)
    private BatchJobType jobType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private BatchJobStatus status = BatchJobStatus.QUEUED;

    @NotNull
    @Min(1)
    @Column(name = "total_items", nullable = false)
    private Integer totalItems;

    @NotNull
    @Min(0)
    @Column(name = "processed_items", nullable = false)
    private Integer processedItems = 0;

    @NotNull
    @Min(0)
    @Column(name = "failed_items", nullable = false)
    private Integer failedItems = 0;

    @Min(0)
    @Max(100)
    @Column(name = "progress")
    private Integer progress = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata_json", columnDefinition = "JSONB")
    private Map<String, Object> metadataJson = new HashMap<>();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // Constructors
    public BatchJob() {
    }

    public BatchJob(BatchJobType jobType, Integer totalItems) {
        this.jobType = jobType;
        this.totalItems = totalItems;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public BatchJobType getJobType() {
        return jobType;
    }

    public void setJobType(BatchJobType jobType) {
        this.jobType = jobType;
    }

    public BatchJobStatus getStatus() {
        return status;
    }

    public void setStatus(BatchJobStatus status) {
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Map<String, Object> getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(Map<String, Object> metadataJson) {
        this.metadataJson = metadataJson;
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

    // equals, hashCode, toString
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BatchJob batchJob = (BatchJob) o;
        return Objects.equals(id, batchJob.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "BatchJob{" +
                "id=" + id +
                ", jobType=" + jobType +
                ", status=" + status +
                ", totalItems=" + totalItems +
                ", processedItems=" + processedItems +
                ", failedItems=" + failedItems +
                ", progress=" + progress +
                '}';
    }
}
