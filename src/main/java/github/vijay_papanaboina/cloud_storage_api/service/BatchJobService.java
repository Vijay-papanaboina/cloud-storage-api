package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.BatchJobResponse;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;

import java.util.UUID;

/**
 * Service interface for batch job operations.
 */
public interface BatchJobService {
    /**
     * Get batch job status by ID.
     * Only returns batch jobs that belong to the authenticated user.
     * The authenticated user ID is extracted from SecurityContext.
     *
     * @param id Batch job ID
     * @return BatchJobResponse with batch job status and progress
     * @throws ResourceNotFoundException if batch job is not found or does not
     *                                   belong to the authenticated user
     */
    BatchJobResponse getBatchJobStatus(UUID id);
}
