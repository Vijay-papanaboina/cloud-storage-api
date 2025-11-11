package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.BatchJobResponse;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;
import github.vijay_papanaboina.cloud_storage_api.model.BatchJob;
import github.vijay_papanaboina.cloud_storage_api.model.BatchJobStatus;
import github.vijay_papanaboina.cloud_storage_api.model.BatchJobType;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import github.vijay_papanaboina.cloud_storage_api.repository.BatchJobRepository;
import github.vijay_papanaboina.cloud_storage_api.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchJobServiceImplTest {

    @Mock
    private BatchJobRepository batchJobRepository;

    @InjectMocks
    private BatchJobServiceImpl batchJobService;

    private UUID authenticatedUserId;
    private UUID otherUserId;
    private UUID batchJobId;
    private User testUser;
    private BatchJob testBatchJob;

    @BeforeEach
    void setUp() {
        authenticatedUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        batchJobId = UUID.randomUUID();

        testUser = createTestUser(authenticatedUserId);
        testBatchJob = createTestBatchJob(batchJobId, testUser);
    }

    @Test
    void getBatchJobStatus_Success() {
        // Given
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(authenticatedUserId);
            when(batchJobRepository.findById(batchJobId)).thenReturn(Optional.of(testBatchJob));

            // When
            BatchJobResponse response = batchJobService.getBatchJobStatus(batchJobId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getBatchId()).isEqualTo(batchJobId);
            assertThat(response.getJobType()).isEqualTo(testBatchJob.getJobType().name());
            assertThat(response.getStatus()).isEqualTo(testBatchJob.getStatus().name());
            assertThat(response.getTotalItems()).isEqualTo(testBatchJob.getTotalItems());
            assertThat(response.getProcessedItems()).isEqualTo(testBatchJob.getProcessedItems());
            assertThat(response.getFailedItems()).isEqualTo(testBatchJob.getFailedItems());
            assertThat(response.getProgress()).isEqualTo(testBatchJob.getProgress());
            assertThat(response.getStartedAt()).isEqualTo(testBatchJob.getStartedAt());
            assertThat(response.getCompletedAt()).isEqualTo(testBatchJob.getCompletedAt());
            assertThat(response.getErrorMessage()).isEqualTo(testBatchJob.getErrorMessage());

            verify(batchJobRepository, times(1)).findById(batchJobId);
            securityUtilsMock.verify(SecurityUtils::getAuthenticatedUserId, times(1));
        }
    }

    @Test
    void getBatchJobStatus_NullId_ThrowsException() {
        // Given
        UUID nullId = null;

        // When/Then
        assertThatThrownBy(() -> batchJobService.getBatchJobStatus(nullId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Batch job ID cannot be null");

        verify(batchJobRepository, never()).findById(any());
    }

    @Test
    void getBatchJobStatus_NotFound_ThrowsResourceNotFoundException() {
        // Given
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(authenticatedUserId);
            when(batchJobRepository.findById(batchJobId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> batchJobService.getBatchJobStatus(batchJobId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Batch job not found");

            verify(batchJobRepository, times(1)).findById(batchJobId);
            securityUtilsMock.verify(SecurityUtils::getAuthenticatedUserId, times(1));
        }
    }

    @Test
    void getBatchJobStatus_DifferentUser_ThrowsResourceNotFoundException() {
        // Given
        User otherUser = createTestUser(otherUserId);
        BatchJob otherUserBatchJob = createTestBatchJob(batchJobId, otherUser);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(authenticatedUserId);
            when(batchJobRepository.findById(batchJobId)).thenReturn(Optional.of(otherUserBatchJob));

            // When/Then
            assertThatThrownBy(() -> batchJobService.getBatchJobStatus(batchJobId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Batch job not found");

            verify(batchJobRepository, times(1)).findById(batchJobId);
            securityUtilsMock.verify(SecurityUtils::getAuthenticatedUserId, times(1));
        }
    }

    @Test
    void getBatchJobStatus_NullUser_ThrowsResourceNotFoundException() {
        // Given
        BatchJob batchJobWithNullUser = createTestBatchJob(batchJobId, null);

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getAuthenticatedUserId).thenReturn(authenticatedUserId);
            when(batchJobRepository.findById(batchJobId)).thenReturn(Optional.of(batchJobWithNullUser));

            // When/Then
            assertThatThrownBy(() -> batchJobService.getBatchJobStatus(batchJobId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Batch job not found");

            verify(batchJobRepository, times(1)).findById(batchJobId);
            securityUtilsMock.verify(SecurityUtils::getAuthenticatedUserId, times(1));
        }
    }

    // Helper methods
    private User createTestUser(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPasswordHash("hashedPassword");
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        return user;
    }

    private BatchJob createTestBatchJob(UUID batchJobId, User user) {
        BatchJob batchJob = new BatchJob();
        batchJob.setId(batchJobId);
        batchJob.setUser(user);
        batchJob.setJobType(BatchJobType.UPLOAD);
        batchJob.setStatus(BatchJobStatus.PROCESSING);
        batchJob.setTotalItems(100);
        batchJob.setProcessedItems(50);
        batchJob.setFailedItems(2);
        batchJob.setProgress(50);
        batchJob.setStartedAt(Instant.now().minusSeconds(60));
        batchJob.setCreatedAt(Instant.now().minusSeconds(120));
        batchJob.setUpdatedAt(Instant.now());
        return batchJob;
    }
}
