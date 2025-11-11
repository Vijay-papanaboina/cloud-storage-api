package github.vijay_papanaboina.cloud_storage_api.integration;

import github.vijay_papanaboina.cloud_storage_api.model.BatchJob;
import github.vijay_papanaboina.cloud_storage_api.model.BatchJobStatus;
import github.vijay_papanaboina.cloud_storage_api.model.BatchJobType;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BatchJobIntegrationTest extends BaseIntegrationTest {

    private BatchJob createTestBatchJob(User user, BatchJobType jobType, BatchJobStatus status) {
        BatchJob batchJob = new BatchJob();
        batchJob.setUser(user);
        batchJob.setJobType(jobType);
        batchJob.setStatus(status);
        batchJob.setTotalItems(10);
        batchJob.setProcessedItems(5);
        batchJob.setFailedItems(0);
        batchJob.setProgress(50);
        batchJob.setStartedAt(Instant.now().minusSeconds(60));
        return batchJobRepository.save(batchJob);
    }

    @Test
    void getBatchJobStatus_ShouldReturnCorrectStatus() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        String accessToken = generateAccessToken(user);
        BatchJob batchJob = createTestBatchJob(user, BatchJobType.UPLOAD, BatchJobStatus.PROCESSING);

        // When & Then
        mockMvc.perform(get("/api/batches/" + batchJob.getId() + "/status")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchJob.getId().toString()))
                .andExpect(jsonPath("$.jobType").value("UPLOAD"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.totalItems").value(10))
                .andExpect(jsonPath("$.processedItems").value(5))
                .andExpect(jsonPath("$.progress").value(50));
    }

    @Test
    void getBatchJobStatus_BelongsToUser_ShouldSucceed() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        String accessToken = generateAccessToken(user);
        BatchJob batchJob = createTestBatchJob(user, BatchJobType.DELETE, BatchJobStatus.COMPLETED);

        // When & Then
        mockMvc.perform(get("/api/batches/" + batchJob.getId() + "/status")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getBatchJobStatus_BelongsToAnotherUser_ShouldReturn404() throws Exception {
        // Given
        User user1 = createTestUser("user1", "user1@example.com");
        User user2 = createTestUser("user2", "user2@example.com");
        String accessToken2 = generateAccessToken(user2);
        BatchJob batchJob = createTestBatchJob(user1, BatchJobType.UPLOAD, BatchJobStatus.PROCESSING);

        // When & Then - user2 should not be able to access user1's batch job
        mockMvc.perform(get("/api/batches/" + batchJob.getId() + "/status")
                .header("Authorization", "Bearer " + accessToken2))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBatchJobStatus_WithProgress_ShouldCalculateEstimatedCompletion() throws Exception {
        // Given
        User user = createTestUser("testuser", "test@example.com");
        String accessToken = generateAccessToken(user);
        BatchJob batchJob = createTestBatchJob(user, BatchJobType.UPLOAD, BatchJobStatus.PROCESSING);
        batchJob.setStartedAt(Instant.now().minusSeconds(60)); // Started 60 seconds ago
        batchJob.setProcessedItems(5);
        batchJob.setTotalItems(10);
        batchJobRepository.save(batchJob);

        // When & Then
        mockMvc.perform(get("/api/batches/" + batchJob.getId() + "/status")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedCompletion").exists());
    }
}
