package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.BatchJobResponse;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;
import github.vijay_papanaboina.cloud_storage_api.model.BatchJob;
import github.vijay_papanaboina.cloud_storage_api.repository.BatchJobRepository;
import github.vijay_papanaboina.cloud_storage_api.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class BatchJobServiceImpl implements BatchJobService {
    private static final Logger log = LoggerFactory.getLogger(BatchJobServiceImpl.class);

    private final BatchJobRepository batchJobRepository;

    @Autowired
    public BatchJobServiceImpl(BatchJobRepository batchJobRepository) {
        this.batchJobRepository = batchJobRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BatchJobResponse getBatchJobStatus(UUID id) {
        log.info("Getting batch job status: id={}", id);

        // Ensure parameter is not null
        Objects.requireNonNull(id, "Batch job ID cannot be null");

        // Get authenticated user ID from SecurityContext
        UUID authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        log.debug("Authenticated user ID: {}", authenticatedUserId);

        // Get batch job by ID
        BatchJob batchJob = batchJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch job not found: " + id, id));

        // Verify ownership - only return if batch job belongs to authenticated user
        if (batchJob.getUser() == null || !batchJob.getUser().getId().equals(authenticatedUserId)) {
            log.warn(
                    "Attempt to access batch job belonging to another user: id={}, authenticatedUserId={}, batchJobUserId={}",
                    id, authenticatedUserId, batchJob.getUser() != null ? batchJob.getUser().getId() : null);
            throw new ResourceNotFoundException("Batch job not found: " + id, id);
        }

        // Map to response DTO
        return BatchJobResponse.from(batchJob);
    }
}
