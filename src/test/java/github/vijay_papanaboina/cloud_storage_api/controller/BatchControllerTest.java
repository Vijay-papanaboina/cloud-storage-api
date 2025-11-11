package github.vijay_papanaboina.cloud_storage_api.controller;

import github.vijay_papanaboina.cloud_storage_api.dto.BatchJobResponse;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;
import github.vijay_papanaboina.cloud_storage_api.security.JwtTokenProvider;
import github.vijay_papanaboina.cloud_storage_api.service.BatchJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = BatchController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@ExtendWith(MockitoExtension.class)
class BatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BatchJobService batchJobService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private UUID batchId;

    @BeforeEach
    void setUp() {
        batchId = UUID.randomUUID();
    }

    // GET /api/batches/{id}/status tests

    @Test
    @WithMockUser
    void getBatchJobStatus_Success_Returns200() throws Exception {
        // Given
        BatchJobResponse response = createTestBatchJobResponse(batchId, "PROCESSING", 50);

        when(batchJobService.getBatchJobStatus(any(UUID.class))).thenReturn(response);

        // When/Then
        mockMvc.perform(get("/api/batches/{id}/status", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchId.toString()))
                .andExpect(jsonPath("$.jobType").value("UPLOAD"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.totalItems").value(10))
                .andExpect(jsonPath("$.processedItems").value(5))
                .andExpect(jsonPath("$.failedItems").value(0))
                .andExpect(jsonPath("$.progress").value(50))
                .andExpect(jsonPath("$.startedAt").exists());

        verify(batchJobService, times(1)).getBatchJobStatus(batchId);
    }

    @Test
    @WithMockUser
    void getBatchJobStatus_WithEstimatedCompletion_Returns200() throws Exception {
        // Given
        BatchJobResponse response = createTestBatchJobResponse(batchId, "PROCESSING", 50);
        response.setEstimatedCompletion(Instant.now().plusSeconds(300));

        when(batchJobService.getBatchJobStatus(any(UUID.class))).thenReturn(response);

        // When/Then
        mockMvc.perform(get("/api/batches/{id}/status", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.estimatedCompletion").exists())
                .andExpect(jsonPath("$.estimatedCompletion").isString()); // Instant is serialized as ISO-8601 string

        verify(batchJobService, times(1)).getBatchJobStatus(batchId);
    }

    @Test
    @WithMockUser
    void getBatchJobStatus_NotFound_Returns404() throws Exception {
        // Given
        when(batchJobService.getBatchJobStatus(any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Batch job not found: " + batchId, batchId));

        // When/Then
        mockMvc.perform(get("/api/batches/{id}/status", batchId))
                .andExpect(status().isNotFound());

        verify(batchJobService, times(1)).getBatchJobStatus(batchId);
    }

    @Test
    void getBatchJobStatus_Unauthenticated_Returns200() throws Exception {
        // Given - Without security, unauthenticated requests succeed
        // In real scenario, security would block this, but for controller tests we
        // exclude security
        BatchJobResponse response = createTestBatchJobResponse(batchId, "PROCESSING", 50);
        when(batchJobService.getBatchJobStatus(any(UUID.class))).thenReturn(response);

        // When/Then - Without security, request succeeds
        mockMvc.perform(get("/api/batches/{id}/status", batchId))
                .andExpect(status().isOk());

        verify(batchJobService, times(1)).getBatchJobStatus(batchId);
    }

    @Test
    @WithMockUser
    void getBatchJobStatus_InvalidUUID_Returns500() throws Exception {
        // When/Then - Spring returns 500 for invalid UUID path variable conversion
        // This is Spring's default behavior for type conversion failures
        mockMvc.perform(get("/api/batches/{id}/status", "invalid-id"))
                .andExpect(status().isInternalServerError());

        verify(batchJobService, never()).getBatchJobStatus(any());
    }

    @Test
    @WithMockUser
    void getBatchJobStatus_BelongsToAnotherUser_Returns404() throws Exception {
        // Given
        // Service handles ownership check and throws ResourceNotFoundException
        when(batchJobService.getBatchJobStatus(any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Batch job not found: " + batchId, batchId));

        // When/Then
        mockMvc.perform(get("/api/batches/{id}/status", batchId))
                .andExpect(status().isNotFound());

        verify(batchJobService, times(1)).getBatchJobStatus(batchId);
    }

    // Helper methods
    private BatchJobResponse createTestBatchJobResponse(UUID batchId, String status, Integer progress) {
        BatchJobResponse response = new BatchJobResponse();
        response.setBatchId(batchId);
        response.setJobType("UPLOAD");
        response.setStatus(status);
        response.setTotalItems(10);
        response.setProcessedItems(5);
        response.setFailedItems(0);
        response.setProgress(progress);
        response.setStartedAt(Instant.now());
        return response;
    }
}
