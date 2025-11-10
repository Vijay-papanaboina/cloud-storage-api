package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.*;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FileService {
    /**
     * Upload a file to Cloudinary and save metadata to database
     *
     * @param file       The file to upload
     * @param folderPath Optional folder path
     * @param userId     The authenticated user's ID
     * @return FileResponse with file metadata
     */
    FileResponse upload(MultipartFile file, String folderPath, UUID userId);

    /**
     * Download a file from Cloudinary
     *
     * @param id     File ID
     * @param userId The authenticated user's ID
     * @return Resource containing file bytes
     */
    Resource download(UUID id, UUID userId);

    /**
     * Get file by ID (user-scoped)
     *
     * @param id     File ID
     * @param userId The authenticated user's ID
     * @return FileResponse with file metadata
     */
    FileResponse getById(UUID id, UUID userId);

    /**
     * List files with pagination and optional filters (user-scoped)
     *
     * @param pageable    Pagination parameters
     * @param contentType Optional content type filter
     * @param folderPath  Optional folder path filter
     * @param userId      The authenticated user's ID
     * @return Page of FileResponse
     */
    Page<FileResponse> list(Pageable pageable, String contentType, String folderPath, UUID userId);

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
     * @param contentType Optional content type filter
     * @param folderPath  Optional folder path filter
     * @param pageable    Pagination parameters
     * @param userId      The authenticated user's ID
     * @return Page of FileResponse
     */
    Page<FileResponse> search(String query, String contentType, String folderPath, Pageable pageable, UUID userId);

    /**
     * Get file statistics for a user
     *
     * @param userId The authenticated user's ID
     * @return Map containing statistics (totalFiles, totalSize, byContentType,
     *         byFolder, etc.)
     */
    Map<String, Object> getStatistics(UUID userId);

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
     * @param crop    Optional crop mode
     * @param quality Optional quality setting
     * @param format  Optional output format
     * @param userId  The authenticated user's ID
     * @return TransformResponse with transformed URL
     */
    TransformResponse getTransformUrl(UUID id, Integer width, Integer height, String crop, String quality,
            String format, UUID userId);

    /**
     * Bulk upload files (asynchronous, user-scoped)
     *
     * @param files      Array of files to upload (max 100)
     * @param folderPath Optional folder path for all files
     * @param userId     The authenticated user's ID
     * @return BulkUploadResponse with batch job ID
     */
    BulkUploadResponse bulkUpload(MultipartFile[] files, String folderPath, UUID userId);
}
