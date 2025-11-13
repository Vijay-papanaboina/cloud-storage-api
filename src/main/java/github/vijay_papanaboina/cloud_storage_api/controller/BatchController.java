package github.vijay_papanaboina.cloud_storage_api.controller;

import github.vijay_papanaboina.cloud_storage_api.dto.BatchJobResponse;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;
import github.vijay_papanaboina.cloud_storage_api.security.SecurityUtils;
import github.vijay_papanaboina.cloud_storage_api.service.BatchJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for batch job operations.
 * Handles batch job status queries and progress tracking.
 */
@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchJobService batchJobService;

    @Autowired
    public BatchController(BatchJobService batchJobService) {
        this.batchJobService = batchJobService;
    }

    /**
     * Get batch job status by ID.
     * Only returns batch jobs that belong to the authenticated user.
     * 
     * Authorization and not-found handling are enforced by the service layer:
     * - The service extracts the authenticated user ID from SecurityContext
     * - Verifies that the batch job belongs to the authenticated user
     * - Throws ResourceNotFoundException (handled by @ControllerAdvice) if the
     * batch job
     * is not found or does not belong to the authenticated user, resulting in a 404
     * response
     *
     * @param id Batch job ID
     * @return BatchJobResponse with batch job status and progress
     * @throws ResourceNotFoundException if batch job is not found or does not
     *                                   belong to the authenticated user
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<BatchJobResponse> getBatchJobStatus(@PathVariable UUID id) {
        SecurityUtils.requirePermission("ROLE_READ");
        BatchJobResponse response = batchJobService.getBatchJobStatus(id);
        return ResponseEntity.ok(response);
    }
}
