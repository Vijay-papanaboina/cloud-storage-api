package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.*;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileService {
    /**
     * Upload a file to Cloudinary and save metadata to database
     *
     * @param file       The file to upload
     * @param folderPath Optional folder path. Empty Optional or empty string means
     *                   no folder.
     * @param filename   Optional custom filename. Empty Optional means use original filename.
     * @param userId     The authenticated user's ID
     * @return FileResponse with file metadata
     */
    FileResponse upload(MultipartFile file, Optional<String> folderPath, Optional<String> filename, UUID userId);

    /**
     * Download a file from Cloudinary
     *
     * @param id     File ID
     * @param userId The authenticated user's ID
     * @return Resource containing file bytes
     * @throws ResourceNotFoundException if the file with the given ID does not
     *                                   exist
     *                                   (HTTP 404 Not Found)
     * @throws RuntimeException          if an I/O error occurs while
     *                                   retrieving the file from
     *                                   Cloudinary or if the storage service
     *                                   is unavailable (HTTP 500 Internal Server
     *                                   Error or HTTP 503 Service Unavailable).
     *                                   I/O errors from the underlying storage
     *                                   service are caught and wrapped in
     *                                   RuntimeException
     */
    Resource download(UUID id, UUID userId);

    /**
     * Get signed download URL for a file (user-scoped)
     *
     * @param id                File ID
     * @param userId            The authenticated user's ID
     * @param expirationMinutes URL expiration time in minutes (default: 60)
     * @return FileUrlResponse with signed URL and metadata
     */
    FileUrlResponse getSignedDownloadUrl(UUID id, UUID userId, int expirationMinutes);

    /**
     * Get file by ID (user-scoped)
     *
     * @param id     File ID
     * @param userId The authenticated user's ID
     * @return FileResponse with file metadata
     * @throws ResourceNotFoundException if the file with the given ID does not
     *                                   exist
     *                                   (HTTP 404 Not Found)
     * @throws AccessDeniedException     if the userId does not own the file or is
     *                                   not
     *                                   authorized to access it (HTTP 403
     *                                   Forbidden)
     * @throws AuthorizationException    if authorization fails for the requested
     *                                   file
     *                                   (HTTP 403 Forbidden)
     */
    FileResponse getById(UUID id, UUID userId);

    /**
     * List files with pagination and optional filters (user-scoped)
     *
     * @param pageable    Pagination parameters
     * @param contentType Optional content type filter. Empty Optional means no
     *                    filter.
     * @param folderPath  Optional folder path filter. Empty Optional means no
     *                    filter.
     * @param userId      The authenticated user's ID
     * @return Page of FileResponse
     */
    Page<FileResponse> list(Pageable pageable, Optional<String> contentType, Optional<String> folderPath, UUID userId);

    /**
     * Update file metadata (user-scoped)
     *
     * @param id      File ID
     * @param request Update request with new metadata
     * @param userId  The authenticated user's ID
     * @return FileResponse with updated metadata
     */
    FileResponse update(UUID id, FileUpdateRequest request, UUID userId);

    /**
     * Delete file (soft delete, user-scoped)
     *
     * @param id     File ID
     * @param userId The authenticated user's ID
     */
    void delete(UUID id, UUID userId);

    /**
     * Search files by filename (user-scoped)
     *
     * @param query       Search query
     * @param contentType Optional content type filter. Empty Optional means no
     *                    filter.
     * @param folderPath  Optional folder path filter. Empty Optional means no
     *                    filter.
     * @param pageable    Pagination parameters
     * @param userId      The authenticated user's ID
     * @return Page of FileResponse
     */
    Page<FileResponse> search(String query, Optional<String> contentType, Optional<String> folderPath,
            Pageable pageable, UUID userId);

    /**
     * Get file statistics for a user
     *
     * @param userId The authenticated user's ID
     * @return FileStatisticsResponse containing statistics (totalFiles, totalSize,
     *         byContentType, byFolder, etc.)
     */
    FileStatisticsResponse getStatistics(UUID userId);

    /**
     * Batch delete files (soft delete, user-scoped)
     *
     * @param fileIds List of file IDs to delete
     * @param userId  The authenticated user's ID
     * @return Number of files deleted
     */
    int batchDelete(List<UUID> fileIds, UUID userId);

    /**
     * Get Cloudinary URL for a file (user-scoped)
     *
     * @param id     File ID
     * @param secure Use HTTPS URL
     * @param userId The authenticated user's ID
     * @return FileUrlResponse with URL and metadata
     */
    FileUrlResponse getFileUrl(UUID id, boolean secure, UUID userId);

    /**
     * Transform image/video (on-the-fly via Cloudinary, user-scoped)
     *
     * @param id      File ID
     * @param request Transformation request
     * @param userId  The authenticated user's ID
     * @return TransformResponse with transformed URL
     */
    TransformResponse transform(UUID id, TransformRequest request, UUID userId);

    /**
     * Get transformation URL for image/video (on-the-fly via Cloudinary,
     * user-scoped)
     *
     * @param id      File ID
     * @param width   Optional width
     * @param height  Optional height
     * @param crop    Optional crop mode. Empty Optional means no crop.
     * @param quality Optional quality setting. Empty Optional means no quality
     *                override.
     * @param format  Optional output format. Empty Optional means no format
     *                override.
     * @param userId  The authenticated user's ID
     * @return TransformResponse with transformed URL
     */
    TransformResponse getTransformUrl(UUID id, Integer width, Integer height, Optional<String> crop,
            Optional<String> quality,
            Optional<String> format, UUID userId);

    /**
     * Bulk upload files (user-scoped)
     * <p>
     * This method returns immediately with a batch job ID. The actual file uploads
     * are processed asynchronously. Use {@link #getBulkUploadStatus(UUID, UUID)} to
     * poll for progress and completion status.
     *
     * @param files      Array of files to upload (max 100)
     * @param folderPath Optional folder path for all files. Empty Optional or empty
     *                   string means no folder.
     * @param userId     The authenticated user's ID
     * @return BulkUploadResponse with batch job ID and initial status
     */
    BulkUploadResponse bulkUpload(MultipartFile[] files, Optional<String> folderPath, UUID userId);

    /**
     * Get bulk upload status (user-scoped)
     * <p>
     * Retrieves the current status of a bulk upload job, including progress,
     * processed items, and failed items. Use this method to poll for job
     * completion.
     *
     * @param jobId  The batch job ID returned from
     *               {@link #bulkUpload(MultipartFile[], Optional, UUID)}
     * @param userId The authenticated user's ID
     * @return BulkUploadResponse with current job status and progress
     * @throws ResourceNotFoundException if the job with the given ID does not
     *                                   exist (HTTP 404 Not Found)
     * @throws AccessDeniedException     if the job exists but does not belong to
     *                                   the user (HTTP 403 Forbidden)
     * @throws IllegalArgumentException  if the userId is null
     */
    BulkUploadResponse getBulkUploadStatus(UUID jobId, UUID userId);
}
