package github.vijay_papanaboina.cloud_storage_api.controller;

import github.vijay_papanaboina.cloud_storage_api.dto.*;
import github.vijay_papanaboina.cloud_storage_api.security.SecurityUtils;
import github.vijay_papanaboina.cloud_storage_api.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import github.vijay_papanaboina.cloud_storage_api.model.File;
import github.vijay_papanaboina.cloud_storage_api.repository.FileRepository;

/**
 * REST controller for file management operations.
 * Handles file upload, download, listing, metadata management, transformations,
 * search, and statistics.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final FileRepository fileRepository;

    @Autowired
    public FileController(FileService fileService, FileRepository fileRepository) {
        this.fileService = fileService;
        this.fileRepository = fileRepository;
    }

    /**
     * Upload file endpoint.
     * Upload a new file to Cloudinary and save metadata to database.
     *
     * @param file       The file to upload
     * @param folderPath Optional folder path
     * @param filename   Optional custom filename (defaults to original filename)
     * @return FileResponse with file metadata
     */
    @Operation(summary = "Upload a file", description = "Upload a new file to cloud storage. The file will be associated with the authenticated user. "
            +
            "Use Unix-style paths (forward slashes) for folderPath, e.g., /photos/2024")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "File uploaded successfully", content = @Content(schema = @Schema(implementation = FileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file or request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponse> uploadFile(
            @Parameter(description = "File to upload", required = true, content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)) @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional folder path (Unix-style, e.g., /photos/2024). Must start with '/' if provided.", required = false) @RequestParam(value = "folderPath", required = false) String folderPath,
            @Parameter(description = "Optional custom filename (defaults to original filename if not provided).", required = false) @RequestParam(value = "filename", required = false) String filename) {
        SecurityUtils.requirePermission("ROLE_WRITE");
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        Optional<String> folderPathOpt = Optional.ofNullable(folderPath);
        Optional<String> filenameOpt = Optional.ofNullable(filename);
        FileResponse response = fileService.upload(file, folderPathOpt, filenameOpt, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List files endpoint.
     * List files with pagination and optional filters.
     *
     * @param page        Page number (0-indexed, default: 0)
     * @param size        Page size (default: 20, max: 100)
     * @param sort        Sort field and direction (e.g., "createdAt,desc")
     * @param contentType Optional content type filter
     * @param folderPath  Optional folder path filter
     * @return Page of FileResponse
     */
    @GetMapping
    public ResponseEntity<Page<FileResponse>> listFiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String folderPath) {
        SecurityUtils.requirePermission("ROLE_READ");
        UUID userId = SecurityUtils.getAuthenticatedUserId();

        // Validate pagination parameters
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be >= 0");
        }
        if (size <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size must be between 1 and 100");
        }
        if (size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size must be between 1 and 100");
        }

        // Parse sort parameter
        Sort sortObj = parseSort(sort);
        Objects.requireNonNull(sortObj, "sortObj cannot be null");
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Optional<String> contentTypeOpt = Optional.ofNullable(contentType);
        Optional<String> folderPathOpt = Optional.ofNullable(folderPath);

        Page<FileResponse> response = fileService.list(pageable, contentTypeOpt, folderPathOpt, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get file metadata endpoint.
     * Get file metadata by ID.
     *
     * @param id File ID
     * @return FileResponse with file metadata
     */
    @GetMapping("/{id}")
    public ResponseEntity<FileResponse> getFile(@PathVariable UUID id) {
        SecurityUtils.requirePermission("ROLE_READ");
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        FileResponse response = fileService.getById(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get file URL endpoint.
     * Get signed download URL for a file.
     *
     * @param id                File ID
     * @param expirationMinutes URL expiration time in minutes (default: 60)
     * @return FileUrlResponse with signed URL and metadata
     */
    @GetMapping("/{id}/url")
    public ResponseEntity<FileUrlResponse> getFileUrl(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "60") int expirationMinutes) {
        SecurityUtils.requirePermission("ROLE_READ");
        UUID userId = SecurityUtils.getAuthenticatedUserId();

        // Validate expirationMinutes: must be positive and non-zero
        if (expirationMinutes <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "expirationMinutes must be a positive non-zero value, but got: " + expirationMinutes);
        }

        // Cap expirationMinutes to a sensible maximum (24 hours = 1440 minutes)
        final int MAX_EXPIRATION_MINUTES = 24 * 60;
        if (expirationMinutes > MAX_EXPIRATION_MINUTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "expirationMinutes cannot exceed " + MAX_EXPIRATION_MINUTES + " minutes (24 hours), but got: "
                            + expirationMinutes);
        }

        FileUrlResponse response = fileService.getSignedDownloadUrl(id, userId, expirationMinutes);
        return ResponseEntity.ok(response);
    }

    /**
     * Get file URL by filepath endpoint.
     * Get signed download URL for a file using its full filepath.
     *
     * @param filepath          The full filepath (e.g., "/photos/2024/image.jpg" or
     *                          "document.pdf" for root)
     * @param expirationMinutes URL expiration time in minutes (default: 60)
     * @return FileUrlResponse with signed URL and metadata
     */
    @GetMapping("/url-by-path")
    public ResponseEntity<FileUrlResponse> getFileUrlByPath(
            @RequestParam("filepath") String filepath,
            @RequestParam(defaultValue = "60") int expirationMinutes) {
        SecurityUtils.requirePermission("ROLE_READ");
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        // Validate filepath parameter
        if (filepath == null || filepath.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "filepath parameter is required and cannot be empty");
        }
        // Validate expirationMinutes: must be positive and non-zero
        if (expirationMinutes <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "expirationMinutes must be a positive non-zero value, but got: " + expirationMinutes);
        }

        // Cap expirationMinutes to a sensible maximum (24 hours = 1440 minutes)
        final int MAX_EXPIRATION_MINUTES = 24 * 60;
        if (expirationMinutes > MAX_EXPIRATION_MINUTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "expirationMinutes cannot exceed " + MAX_EXPIRATION_MINUTES + " minutes (24 hours), but got: "
                            + expirationMinutes);
        }

        FileUrlResponse response = fileService.getSignedDownloadUrlByFilepath(filepath, userId, expirationMinutes);
        return ResponseEntity.ok(response);
    }

    /**
     * Download file endpoint.
     * Download a file from Cloudinary.
     *
     * @param id File ID
     * @return File Resource with appropriate headers
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID id) {
        SecurityUtils.requirePermission("ROLE_READ");
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        // Get file metadata first
        FileResponse fileMetadata = fileService.getById(id, userId);
        Resource resource = fileService.download(id, userId);

        HttpHeaders headers = new HttpHeaders();
        String contentType = fileMetadata.getContentType();
        if (contentType != null && !contentType.isEmpty()) {
            headers.setContentType(MediaType.parseMediaType(contentType));
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        headers.setContentDispositionFormData("attachment", fileMetadata.getFilename());

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    /**
     * Download file by filepath endpoint.
     * Download a file using its full filepath (folder path + filename).
     *
     * @param filepath The full filepath (e.g., "/photos/2024/image.jpg" or
     *                 "document.pdf" for root)
     * @return File Resource with appropriate headers
     */
    @GetMapping("/download-by-path")
    public ResponseEntity<Resource> downloadFileByPath(
            @RequestParam("filepath") String filepath) {
        SecurityUtils.requirePermission("ROLE_READ");
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        // Validate filepath parameter
        if (filepath == null || filepath.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "filepath parameter is required and cannot be empty");
        }
        // Parse filepath to get folder path and filename
        String normalizedPath = filepath.trim();
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        if (normalizedPath.isEmpty() || normalizedPath.endsWith("/")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid filepath: must contain a filename, not just a folder path");
        }
        int lastSlashIndex = normalizedPath.lastIndexOf('/');
        String folderPath = lastSlashIndex == -1 ? null : "/" + normalizedPath.substring(0, lastSlashIndex);
        String filename = lastSlashIndex == -1 ? normalizedPath : normalizedPath.substring(lastSlashIndex + 1);

        // Get file metadata first
        Optional<File> fileOpt = fileRepository.findByUserIdAndFolderPathAndFilenameAndDeletedFalse(
                userId, folderPath, filename);
        if (fileOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + filepath);
        }
        File file = fileOpt.get();
        FileResponse fileMetadata = fileService.getById(file.getId(), userId);

        // Download file by filepath
        Resource resource = fileService.downloadByFilepath(filepath, userId);

        HttpHeaders headers = new HttpHeaders();
        String contentType = fileMetadata.getContentType();
        if (contentType != null && !contentType.isEmpty()) {
            headers.setContentType(MediaType.parseMediaType(contentType));
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        headers.setContentDispositionFormData("attachment", fileMetadata.getFilename());

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    /**
     * Update file metadata endpoint.
     * Update file metadata (filename, folder path).
     *
     * @param id      File ID
     * @param request Update request with new metadata
     * @return FileResponse with updated metadata
     */
    @PutMapping("/{id}")
    public ResponseEntity<FileResponse> updateFile(
            @PathVariable UUID id,
            @Valid @RequestBody FileUpdateRequest request) {
        SecurityUtils.requirePermission("ROLE_WRITE");
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        FileResponse response = fileService.update(id, request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete file endpoint.
     * Delete a file (soft delete).
     *
     * @param id File ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable UUID id) {
        SecurityUtils.requirePermission("ROLE_DELETE");
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        fileService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Transform image/video endpoint.
     * Transform image/video on-the-fly via Cloudinary.
     *
     * @param id      File ID
     * @param request Transformation request
     * @return TransformResponse with transformed URL
     */
    @PostMapping("/{id}/transform")
    public ResponseEntity<TransformResponse> transformFile(
            @PathVariable UUID id,
            @Valid @RequestBody TransformRequest request) {
        SecurityUtils.requirePermission("ROLE_READ");
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        TransformResponse response = fileService.transform(id, request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get transformation URL endpoint.
     * Get transformation URL for image/video via query parameters.
     *
     * @param id      File ID
     * @param width   Optional width
     * @param height  Optional height
     * @param crop    Optional crop mode
     * @param quality Optional quality setting
     * @param format  Optional output format
     * @return TransformResponse with transformed URL
     */
    @GetMapping("/{id}/transform")
    public ResponseEntity<TransformResponse> getTransformUrl(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer width,
            @RequestParam(required = false) Integer height,
            @RequestParam(required = false) String crop,
            @RequestParam(required = false) String quality,
            @RequestParam(required = false) String format) {
        SecurityUtils.requirePermission("ROLE_READ");
        UUID userId = SecurityUtils.getAuthenticatedUserId();

        Optional<String> cropOpt = Optional.ofNullable(crop);
        Optional<String> qualityOpt = Optional.ofNullable(quality);
        Optional<String> formatOpt = Optional.ofNullable(format);

        TransformResponse response = fileService.getTransformUrl(
                id, width, height, cropOpt, qualityOpt, formatOpt, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Search files endpoint.
     * Search files by filename with optional filters.
     *
     * @param q           Search query (required)
     * @param page        Page number (0-indexed, default: 0)
     * @param size        Page size (default: 20, max: 100)
     * @param contentType Optional content type filter
     * @param folderPath  Optional folder path filter
     * @return Page of FileResponse
     */
    @GetMapping("/search")
    public ResponseEntity<Page<FileResponse>> searchFiles(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String folderPath) {
        SecurityUtils.requirePermission("ROLE_READ");
        UUID userId = SecurityUtils.getAuthenticatedUserId();

        // Validate query parameter
        if (query == null || query.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search query cannot be empty");
        }

        // Validate pagination parameters
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be >= 0");
        }
        if (size <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size must be between 1 and 100");
        }
        if (size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must not exceed 100");
        }

        Pageable pageable = PageRequest.of(page, size);
        Optional<String> contentTypeOpt = Optional.ofNullable(contentType);
        Optional<String> folderPathOpt = Optional.ofNullable(folderPath);

        Page<FileResponse> response = fileService.search(
                query, contentTypeOpt, folderPathOpt, pageable, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get file statistics endpoint.
     * Get file statistics for the authenticated user.
     *
     * @return FileStatisticsResponse with statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<FileStatisticsResponse> getFileStatistics() {
        SecurityUtils.requirePermission("ROLE_READ");
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        FileStatisticsResponse response = fileService.getStatistics(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Parse sort parameter into Sort object.
     * Format: "field,direction" (e.g., "createdAt,desc")
     * Default: "createdAt,desc"
     *
     * @param sort Sort string
     * @return Sort object
     */
    // Allowed sort fields
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "filename", "size", "contentType");

    private Sort parseSort(String sort) {
        if (sort == null || sort.trim().isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String[] parts = sort.split(",");
        if (parts.length != 2) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String field = parts[0].trim();
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String direction = parts[1].trim().toUpperCase();

        Sort.Direction sortDirection = direction.equals("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return Sort.by(sortDirection, field);
    }
}
