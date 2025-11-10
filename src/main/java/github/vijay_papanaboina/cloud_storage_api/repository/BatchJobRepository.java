package github.vijay_papanaboina.cloud_storage_api.repository;

import github.vijay_papanaboina.cloud_storage_api.model.BatchJob;
import github.vijay_papanaboina.cloud_storage_api.model.BatchJobStatus;
import github.vijay_papanaboina.cloud_storage_api.model.BatchJobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchJobRepository extends JpaRepository<BatchJob, UUID> {

    /**
     * Find all batch jobs by status.
     */
    List<BatchJob> findByStatus(BatchJobStatus status);

    /**
     * Find all batch jobs by job type.
     */
    List<BatchJob> findByJobType(BatchJobType jobType);

    /**
     * Get batch job by ID and user ID (GET /api/batches/{id}/status).
     * Returns batch job only if it belongs to the user.
     */
    @Query("SELECT bj FROM BatchJob bj WHERE bj.id = :id AND bj.user.id = :userId")
    Optional<BatchJob> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
}
