package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.*;
import github.vijay_papanaboina.cloud_storage_api.exception.AuthorizationException;
import github.vijay_papanaboina.cloud_storage_api.exception.StorageException;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
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
         * @param filename   Optional custom filename. Empty Optional means use original
         *                   filename.
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
         * @throws StorageException          if an I/O error occurs while
         *                                   retrieving the file from
         *                                   Cloudinary or if the storage service
         *                                   is unavailable (HTTP 500 Internal Server
         *                                   Error or HTTP 503 Service Unavailable).
         *                                   I/O errors from the underlying storage
         *                                   service are caught and wrapped in
         *                                   StorageException
         */
        Resource download(UUID id, UUID userId);

        /**
         * Download a file by filepath (folder path + filename)
         *
         * @param filepath The full filepath (e.g., "/photos/2024/image.jpg" or
         *                 "document.pdf" for root)
         * @param userId   The authenticated user's ID
         * @return Resource containing file bytes
         * @throws ResourceNotFoundException if the file is not found
         * @throws BadRequestException       if filepath is invalid
         */
        Resource downloadByFilepath(String filepath, UUID userId);

        /**
         * Get signed download URL for a file (user-scoped)
         *
         * @param id                File ID
         * @param userId            The authenticated user's ID
         * @param expirationMinutes URL expiration time in minutes. Callers must pass
         *                          the
         *                          expiration time explicitly (e.g., pass 60 for 60
         *                          minutes).
         * @return FileUrlResponse with signed URL and metadata
         * @throws ResourceNotFoundException if the file with the given ID does not
         *                                   exist
         *                                   (HTTP 404 Not Found)
         * @throws BadRequestException       if the file ID is invalid
         * @throws AccessDeniedException     if the userId does not own the file or
         *                                   lacks
         *                                   file-level permissions (business-level
         *                                   denial).
         *                                   The user is authenticated but does not have
         *                                   permission to access this specific resource
         *                                   (HTTP 403 Forbidden)
         * @throws AuthorizationException    if authorization fails due to system-level
         *                                   issues, such as invalid/expired token,
         *                                   permission service unreachable, or missing
         *                                   token scope (HTTP 403 Forbidden)
         */
        FileUrlResponse getSignedDownloadUrl(UUID id, UUID userId, int expirationMinutes);

        /**
         * Get signed download URL for a file by filepath (user-scoped)
         *
         * @param filepath          The full filepath (e.g., "/photos/2024/image.jpg" or
         *                          "document.pdf" for root)
         * @param userId            The authenticated user's ID
         * @param expirationMinutes URL expiration time in minutes (default: 60)
         * @return FileUrlResponse with signed URL and metadata
         * @throws ResourceNotFoundException if the file is not found
         * @throws BadRequestException       if filepath is invalid
         */
        FileUrlResponse getSignedDownloadUrlByFilepath(String filepath, UUID userId, int expirationMinutes);

        /**
         * Get file by ID (user-scoped)
         *
         * @param id     File ID
         * @param userId The authenticated user's ID
         * @return FileResponse with file metadata
         * @throws ResourceNotFoundException if the file with the given ID does not
         *                                   exist
         *                                   (HTTP 404 Not Found)
         * @throws AccessDeniedException     if the userId does not own the file or
         *                                   lacks
         *                                   file-level permissions (business-level
         *                                   denial).
         *                                   The user is authenticated but does not have
         *                                   permission to access this specific resource
         *                                   (HTTP 403 Forbidden)
         * @throws AuthorizationException    if authorization fails due to system-level
         *                                   issues, such as invalid/expired token,
         *                                   permission service unreachable, or missing
         *                                   token scope (HTTP 403 Forbidden)
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
        Page<FileResponse> list(Pageable pageable, Optional<String> contentType, Optional<String> folderPath,
                        UUID userId);

        /**
         * Update file metadata (user-scoped)
         *
         * @param id      File ID
         * @param request Update request with new metadata
         * @param userId  The authenticated user's ID
         * @return FileResponse with updated metadata
         * @throws ResourceNotFoundException if the file with the given ID does not
         *                                   exist
         *                                   (HTTP 404 Not Found)
         * @throws AccessDeniedException     if the userId does not own the file or is
         *                                   not
         *                                   authorized to update it (HTTP 403
         *                                   Forbidden)
         */
        FileResponse update(UUID id, FileUpdateRequest request, UUID userId);

        /**
         * Delete file (soft delete, user-scoped).
         * <p>
         * This method performs a <strong>soft delete</strong> operation, which means
         * the file
         * record is not physically removed from the database. Instead, the file's
         * {@code deleted}
         * flag is set to {@code true} and the {@code deletedAt} timestamp is set to the
         * current
         * time. The file data is retained in the database for recovery purposes, but
         * the file
         * will no longer be returned by normal queries (e.g.,
         * {@link #list(Pageable, Optional, Optional, UUID)},
         * {@link #getById(UUID, UUID)},
         * {@link #search(String, Optional, Optional, Pageable, UUID)}).
         * </p>
         * <p>
         * <strong>Idempotency:</strong> This operation is <strong>not
         * idempotent</strong>. If the
         * file does not exist, does not belong to the specified user, or has already
         * been deleted,
         * this method will throw {@link CloudFileNotFoundException}. Calling this
         * method multiple
         * times with the same parameters will only succeed on the first call;
         * subsequent calls
         * will throw an exception.
         * </p>
         * <p>
         * <strong>Post-conditions:</strong> After successful execution:
         * <ul>
         * <li>The file's {@code deleted} flag is set to {@code true}</li>
         * <li>The file's {@code deletedAt} timestamp is set to the current time</li>
         * <li>The file is no longer returned by normal query methods (list, getById,
         * search)</li>
         * <li>The file record remains in the database for potential recovery</li>
         * </ul>
         * </p>
         * <p>
         * <strong>HTTP Mapping:</strong> When invoked via the REST API endpoint
         * ({@code DELETE /api/files/{id}}), successful deletion returns HTTP 204 No
         * Content.
         * Exceptions are mapped as follows:
         * <ul>
         * <li>{@link CloudFileNotFoundException} → HTTP 404 Not Found</li>
         * <li>{@link IllegalArgumentException} → HTTP 400 Bad Request (if parameters
         * are invalid)</li>
         * <li>Authorization failures are handled at the controller level and return
         * HTTP 403 Forbidden</li>
         * </ul>
         * </p>
         *
         * @param id     File ID
         * @param userId The authenticated user's ID
         * @throws CloudFileNotFoundException if the file with the given ID does not
         *                                    exist, does not
         *                                    belong to the specified user, or has
         *                                    already been deleted
         *                                    (HTTP 404 Not Found)
         * @throws IllegalArgumentException   if {@code id} or {@code userId} is null
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
         * Batch delete files (soft delete, user-scoped).
         * <p>
         * This operation is <strong>best-effort</strong> and not atomic. Files are
         * processed
         * individually, and partial failures do not cause the entire operation to fail.
         * Non-existent file IDs or file IDs that do not belong to the specified user
         * are
         * silently skipped and do not affect the operation.
         * </p>
         * <p>
         * If the {@code fileIds} list is empty, this method returns 0 and performs no
         * action.
         * </p>
         *
         * @param fileIds List of file IDs to delete
         * @param userId  The authenticated user's ID
         * @return The count of files successfully soft-deleted (not the total number
         *         requested)
         */
        int batchDelete(List<UUID> fileIds, UUID userId);

        /**
         * Get Cloudinary URL for a file (user-scoped)
         *
         * @param id     File ID
         * @param secure Use HTTPS URL
         * @param userId The authenticated user's ID
         * @return FileUrlResponse with URL and metadata
         * @throws ResourceNotFoundException if the file with the given ID does not
         *                                   exist
         *                                   (HTTP 404 Not Found)
         * @throws AccessDeniedException     if the userId does not own the file or
         *                                   lacks
         *                                   file-level permissions (business-level
         *                                   denial).
         *                                   The user is authenticated but does not have
         *                                   permission to access this specific resource
         *                                   (HTTP 403 Forbidden)
         * @throws AuthorizationException    if authorization fails due to system-level
         *                                   issues, such as invalid/expired token,
         *                                   permission service unreachable, or missing
         *                                   token scope (HTTP 403 Forbidden)
         */
        FileUrlResponse getFileUrl(UUID id, boolean secure, UUID userId);

        /**
         * Transform image/video (on-the-fly via Cloudinary, user-scoped)
         * <p>
         * <strong>Supported MIME Types:</strong>
         * <ul>
         * <li>{@code image/*} - All image types (e.g., image/jpeg, image/png,
         * image/gif, image/webp)</li>
         * <li>{@code video/*} - All video types (e.g., video/mp4, video/webm,
         * video/mov)</li>
         * </ul>
         * Other content types (e.g., application/pdf, text/*, audio/*) are not
         * supported for transformations.
         * </p>
         * <p>
         * <strong>Unsupported Content Type Behavior:</strong>
         * If the file's content type is not {@code image/*} or {@code video/*}, this
         * method throws
         * {@link BadRequestException} with HTTP 400 Bad Request status. The error
         * message indicates
         * that the file type does not support transformations.
         * </p>
         *
         * @param id      File ID
         * @param request Transformation request
         * @param userId  The authenticated user's ID
         * @return TransformResponse with transformed URL
         * @throws CloudFileNotFoundException if the file with the given ID does not
         *                                    exist or does not
         *                                    belong to the specified user (HTTP 404 Not
         *                                    Found)
         * @throws BadRequestException        if the file's content type does not
         *                                    support transformations
         *                                    (e.g., application/pdf, text/*, audio/*)
         *                                    or if transformation
         *                                    parameters are invalid (HTTP 400 Bad
         *                                    Request)
         * @throws StorageException           if an error occurs while communicating
         *                                    with Cloudinary or
         *                                    generating the transformation URL. This
         *                                    includes network errors,
         *                                    Cloudinary API errors, and remote service
         *                                    unavailability
         *                                    (HTTP 503 Service Unavailable)
         */
        TransformResponse transform(UUID id, TransformRequest request, UUID userId);

        /**
         * Get transformation URL for image/video (on-the-fly via Cloudinary,
         * user-scoped).
         * <p>
         * <strong>Supported MIME Types:</strong>
         * <ul>
         * <li>{@code image/*} - All image types (e.g., image/jpeg, image/png,
         * image/gif, image/webp)</li>
         * <li>{@code video/*} - All video types (e.g., video/mp4, video/webm,
         * video/mov)</li>
         * </ul>
         * Other content types (e.g., application/pdf, text/*, audio/*) are not
         * supported for transformations.
         * </p>
         * <p>
         * <strong>Parameter Handling:</strong>
         * <ul>
         * <li><b>Width/Height:</b> Use {@code Integer} (nullable) for numeric
         * dimensions. When {@code null}, the dimension is not applied. Values must be
         * positive integers (at least 1) if provided.</li>
         * <li><b>Crop/Quality/Format:</b> Use {@code Optional<String>} for string
         * parameters. Empty Optional means the parameter is not applied. When present,
         * values must match the valid formats documented below.</li>
         * </ul>
         * </p>
         * <p>
         * <strong>Valid Parameter Values:</strong>
         * <ul>
         * <li><b>Width/Height:</b> Positive integers (minimum 1). {@code null} means
         * no dimension constraint.</li>
         * <li><b>Crop:</b> One of: {@code fill}, {@code fit}, {@code scale},
         * {@code thumb}, {@code crop}, {@code limit}, {@code pad}, {@code lfill},
         * {@code limit_pad}, {@code fit_pad}, {@code auto}, {@code imagga_scale},
         * {@code imagga_crop}. Empty Optional means no crop mode applied.</li>
         * <li><b>Quality:</b> One of: {@code auto}, {@code best}, {@code good},
         * {@code eco}, {@code low}, or a numeric value between 1-100 (inclusive).
         * Empty Optional means no quality override.</li>
         * <li><b>Format:</b> One of: {@code webp}, {@code jpg}, {@code jpeg},
         * {@code png}, {@code gif}, {@code bmp}, {@code tiff}, {@code ico},
         * {@code pdf}, {@code svg}, {@code mp4}, {@code webm}, {@code ogv},
         * {@code flv}, {@code mov}, {@code wmv}. Empty Optional means no format
         * 
         * conversion.</li>
         * </ul>
         * </p>
         * <p>
         * <strong>Unsupported Content Type Behavior:</strong>
         * If the file's content type is not {@code image/*} or {@code video/*}, this
         * method throws {@link BadRequestException} with HTTP 400 Bad Request status.
         * The error message indicates that the file type does not support
         * transformations.
         * </p>
         *
         * @param id      File ID
         * @param width   Optional width in pixels. {@code null} means no width
         *                constraint. Must be at least 1 if provided.
         * @param height  Optional height in pixels. {@code null} means no height
         *                constraint. Must be at least 1 if provided.
         * @param crop    Optional crop mode. Empty Optional means no crop mode
         *                applied. See valid values above.
         * @param quality Optional quality setting. Empty Optional means no quality
         *                override. See valid values above.
         * @param format  Optional output format. Empty Optional means no format
         *                conversion. See valid values above.
         * @param userId  The authenticated user's ID
         * @return TransformResponse with transformed URL and original URL
         * @throws CloudFileNotFoundException if the file with the given ID does not
         *                                    exist or does not
         *                                    belong to the specified user (HTTP 404 Not
         *                                    Found)
         * @throws BadRequestException        if the file's content type does not
         *                                    support transformations
         *                                    (e.g., application/pdf, text/*, audio/*)
         *                                    or if transformation
         *                                    parameters are invalid (HTTP 400 Bad
         *                                    Request)
         * @throws StorageException           if an error occurs while communicating
         *                                    with Cloudinary or
         *                                    generating the transformation URL. This
         *                                    includes network errors,
         *                                    Cloudinary API errors, and remote service
         *                                    unavailability
         *                                    (HTTP 503 Service Unavailable)
         * @throws AccessDeniedException      if the userId does not own the file or
         *                                    lacks
         *                                    file-level permissions (business-level
         *                                    denial).
         *                                    The user is authenticated but does not
         *                                    have
         *                                    permission to access this specific
         *                                    resource
         *                                    (HTTP 403 Forbidden)
         * @throws AuthorizationException     if authorization fails due to system-level
         *                                    issues, such as invalid/expired token,
         *                                    permission service unreachable, or missing
         *                                    token scope (HTTP 403 Forbidden)
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
         * <p>
         * <b>Validation Rules:</b>
         * <ul>
         * <li><b>Array Size:</b> The files array must contain between 1 and 100 files
         * (inclusive). Providing more than 100 files will result in a
         * {@link BadRequestException} with a 400 Bad Request status code.</li>
         * <li><b>Empty Arrays:</b> An empty array or null array is invalid and will
         * result in a {@link BadRequestException} with a 400 Bad Request status
         * code.</li>
         * <li><b>Duplicate Files:</b> Duplicate filenames within the same batch are
         * allowed and will be treated as separate uploads. Each file will be
         * automatically renamed with a unique suffix (e.g., "file-1.pdf", "file-2.pdf")
         * if a filename already exists in the target folder to ensure uniqueness.</li>
         * <li><b>File Size Limits:</b>
         * <p>
         * The per-file limit and total payload limit are <b>independent constraints</b>
         * that must both be satisfied:
         * <ul>
         * <li><b>Per-file limit:</b> Each individual file must not exceed 100MB. This
         * limit is checked independently for each file. Any file exceeding this limit
         * will result in a {@link BadRequestException} with a 400 Bad Request status
         * code.</li>
         * <li><b>Total payload limit:</b> The combined size of all files in the request
         * must not exceed 100MB. This limit applies to the sum of all file sizes,
         * regardless of the per-file limit. Exceeding this limit will result in a
         * {@link BadRequestException} with a 400 Bad Request status code.</li>
         * </ul>
         * <p>
         * <b>Valid Examples:</b>
         * <ul>
         * <li>1 file of 100MB (100MB per file ✓, 100MB total ✓)</li>
         * <li>10 files of 10MB each (10MB per file ✓, 100MB total ✓)</li>
         * <li>100 files of 1MB each (1MB per file ✓, 100MB total ✓)</li>
         * <li>50 files of 2MB each (2MB per file ✓, 100MB total ✓)</li>
         * </ul>
         * <p>
         * <b>Invalid Examples:</b>
         * <ul>
         * <li>1 file of 101MB (101MB per file ✗, exceeds per-file limit)</li>
         * <li>2 files of 60MB each (60MB per file ✓, but 120MB total ✗, exceeds total
         * limit)</li>
         * <li>100 files of 2MB each (2MB per file ✓, but 200MB total ✗, exceeds total
         * limit)</li>
         * <li>5 files of 25MB each (25MB per file ✓, but 125MB total ✗, exceeds total
         * limit)</li>
         * </ul>
         * <p>
         * <b>Note:</b> When uploading the maximum of 100 files, each file can be at
         * most
         * 1MB on average to stay within the 100MB total payload limit.
         * </li>
         * </ul>
         *
         * @param files      Array of files to upload (max 100)
         * @param folderPath Optional folder path for all files. Empty Optional or empty
         *                   string means no folder.
         * @param userId     The authenticated user's ID
         * @return BulkUploadResponse with batch job ID and initial status
         * @throws BadRequestException      if the files array is null or empty, more
         *                                  than
         *                                  100 files are provided, any individual file
         *                                  exceeds 100MB (per-file limit), or the total
         *                                  combined size of all files exceeds 100MB
         *                                  (total payload limit). Both limits are
         *                                  independent and must be satisfied (HTTP 400
         *                                  Bad
         *                                  Request)
         * @throws IllegalArgumentException if the userId is null
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
