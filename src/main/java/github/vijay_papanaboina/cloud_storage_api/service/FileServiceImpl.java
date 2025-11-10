package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.*;
import github.vijay_papanaboina.cloud_storage_api.model.BatchJob;
import github.vijay_papanaboina.cloud_storage_api.model.BatchJobStatus;
import github.vijay_papanaboina.cloud_storage_api.model.BatchJobType;
import github.vijay_papanaboina.cloud_storage_api.model.File;
import github.vijay_papanaboina.cloud_storage_api.model.User;
import github.vijay_papanaboina.cloud_storage_api.repository.BatchJobRepository;
import github.vijay_papanaboina.cloud_storage_api.repository.FileRepository;
import github.vijay_papanaboina.cloud_storage_api.repository.UserRepository;
import github.vijay_papanaboina.cloud_storage_api.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FileServiceImpl implements FileService {
    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final BatchJobRepository batchJobRepository;

    @Autowired
    public FileServiceImpl(FileRepository fileRepository, UserRepository userRepository,
            StorageService storageService, BatchJobRepository batchJobRepository) {
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.batchJobRepository = batchJobRepository;
    }

    @Override
    @Transactional
    public FileResponse upload(MultipartFile file, String folderPath, UUID userId) {
        log.info("Uploading file for user: userId={}, filename={}, size={}", userId, file.getOriginalFilename(),
                file.getSize());
        // Validate userId is not null
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Upload to Cloudinary
        Map<String, Object> uploadOptions = new HashMap<>();
        Map<String, Object> cloudinaryResult = storageService.uploadFile(file, folderPath, uploadOptions);

        // Extract Cloudinary response
        String publicId = (String) cloudinaryResult.get("public_id");
        String cloudinaryUrl = (String) cloudinaryResult.get("url");
        String cloudinarySecureUrl = (String) cloudinaryResult.get("secure_url");

        // Generate unique filename (UUID with extension)
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String filename = UUID.randomUUID().toString() + (extension != null ? "." + extension : "");

        // Create File entity
        File fileEntity = new File(filename, file.getContentType(), file.getSize(), user);
        fileEntity.setFolderPath(folderPath);
        fileEntity.setCloudinaryPublicId(publicId);
        fileEntity.setCloudinaryUrl(cloudinaryUrl);
        fileEntity.setCloudinarySecureUrl(cloudinarySecureUrl);
        fileEntity.setCreatedAt(Instant.now());
        fileEntity.setUpdatedAt(Instant.now());

        // Save to database
        File savedFile = fileRepository.save(fileEntity);
        log.info("File uploaded successfully: fileId={}, publicId={}", savedFile.getId(), publicId);

        return toFileResponse(savedFile);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource download(UUID id, UUID userId) {
        log.info("Downloading file: fileId={}, userId={}", id, userId);

        // Get file (user-scoped)
        File file = fileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("File not found: " + id));

        // Download from Cloudinary
        byte[] fileBytes = storageService.downloadFile(file.getCloudinaryPublicId());
        log.info("File downloaded successfully: fileId={}", id);

        // Validate fileBytes is not null
        if (fileBytes == null) {
            throw new RuntimeException("File bytes are null");
        }

        return new ByteArrayResource(fileBytes);
    }

    @Override
    @Transactional(readOnly = true)
    public FileResponse getById(UUID id, UUID userId) {
        log.info("Getting file by ID: fileId={}, userId={}", id, userId);

        File file = fileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("File not found: " + id));

        return toFileResponse(file);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FileResponse> list(Pageable pageable, String contentType, String folderPath, UUID userId) {
        log.info("Listing files: userId={}, contentType={}, folderPath={}, page={}, size={}",
                userId, contentType, folderPath, pageable.getPageNumber(), pageable.getPageSize());

        Page<File> files;
        if (contentType != null && folderPath != null) {
            // Filter by both contentType and folderPath
            files = fileRepository.findByUserIdAndDeletedFalseAndContentTypeAndFolderPath(
                    userId, contentType, folderPath, pageable);
        } else if (contentType != null) {
            files = fileRepository.findByUserIdAndDeletedFalseAndContentType(userId, contentType, pageable);
        } else if (folderPath != null) {
            files = fileRepository.findByUserIdAndDeletedFalseAndFolderPath(userId, folderPath, pageable);
        } else {
            files = fileRepository.findByUserIdAndDeletedFalse(userId, pageable);
        }

        // Convert to DTOs
        List<FileResponse> content = files.getContent().stream()
                .map(this::toFileResponse)
                .collect(Collectors.toList());

        // Validate content is not null
        if (content == null) {
            throw new RuntimeException("Content is null");
        }

        return new PageImpl<>(content, pageable, files.getTotalElements());
    }

    @Override
    @Transactional
    public FileResponse update(UUID id, FileUpdateRequest request, UUID userId) {
        log.info("Updating file: fileId={}, userId={}", id, userId);

        // Get file (user-scoped)
        File file = fileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("File not found: " + id));

        // Validate request is not null
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        // Update fields if provided
        if (request.getFilename() != null && !request.getFilename().isBlank()) {
            file.setFilename(request.getFilename());
        }
        if (request.getFolderPath() != null) {
            file.setFolderPath(request.getFolderPath());
        }
        file.setUpdatedAt(Instant.now());

        // Save to database
        File updatedFile = fileRepository.save(file);
        log.info("File updated successfully: fileId={}", id);

        return toFileResponse(updatedFile);
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID userId) {
        log.info("Deleting file: fileId={}, userId={}", id, userId);

        // Get file (user-scoped)
        File file = fileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("File not found: " + id));

        // Soft delete
        file.setDeleted(true);
        file.setDeletedAt(Instant.now());
        file.setUpdatedAt(Instant.now());

        // Save to database
        fileRepository.save(file);
        log.info("File deleted successfully: fileId={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FileResponse> search(String query, String contentType, String folderPath, Pageable pageable,
            UUID userId) {
        log.info("Searching files: userId={}, query={}, contentType={}, folderPath={}",
                userId, query, contentType, folderPath);

        Page<File> files;
        if (contentType != null || folderPath != null) {
            files = fileRepository.findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCaseWithFilters(
                    userId, query, contentType, folderPath, pageable);
        } else {
            files = fileRepository.findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCase(
                    userId, query, pageable);
        }

        // Convert to DTOs
        List<FileResponse> content = files.getContent().stream()
                .map(this::toFileResponse)
                .collect(Collectors.toList());

        // Validate content is not null
        if (content == null) {
            throw new RuntimeException("Content is null");
        }
        // Validate pageable is not null
        if (pageable == null) {
            throw new IllegalArgumentException("Pageable cannot be null");
        }

        return new PageImpl<>(content, pageable, files.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics(UUID userId) {
        log.info("Getting file statistics: userId={}", userId);

        // Get base statistics
        Map<String, Object> baseStats = fileRepository.getFileStatisticsByUserId(userId);
        long totalFiles = ((Number) baseStats.get("total_files")).longValue();
        long totalSize = ((Number) baseStats.get("total_size")).longValue();
        long averageFileSize = ((Number) baseStats.get("average_file_size")).longValue();

        // Get content type counts
        List<Object[]> contentTypeCounts = fileRepository.getContentTypeCountsByUserId(userId);
        Map<String, Long> byContentType = new HashMap<>();
        for (Object[] row : contentTypeCounts) {
            byContentType.put((String) row[0], ((Number) row[1]).longValue());
        }

        // Get folder counts
        List<Object[]> folderCounts = fileRepository.getFolderCountsByUserId(userId);
        Map<String, Long> byFolder = new HashMap<>();
        for (Object[] row : folderCounts) {
            String folderPath = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            byFolder.put(folderPath.isEmpty() ? "/" : folderPath, count);
        }

        // Format storage used
        String storageUsed = formatFileSize(totalSize);

        // Build response
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalFiles", totalFiles);
        statistics.put("totalSize", totalSize);
        statistics.put("byContentType", byContentType);
        statistics.put("byFolder", byFolder);
        statistics.put("storageUsed", storageUsed);
        statistics.put("averageFileSize", averageFileSize);

        return statistics;
    }

    @Override
    @Transactional
    public int batchDelete(List<UUID> fileIds, UUID userId) {
        log.info("Batch deleting files: userId={}, count={}", userId, fileIds.size());

        // Get files (user-scoped)
        List<File> files = fileRepository.findByUserIdAndIdIn(userId, fileIds);

        // Soft delete all files
        Instant now = Instant.now();
        for (File file : files) {
            file.setDeleted(true);
            file.setDeletedAt(now);
            file.setUpdatedAt(now);
        }

        // Validate files is not null
        if (files == null) {
            throw new RuntimeException("Files are null");
        }

        // Save to database
        fileRepository.saveAll(files);
        log.info("Batch deleted {} files successfully", files.size());

        return files.size();
    }

    @Override
    @Transactional(readOnly = true)
    public FileUrlResponse getFileUrl(UUID id, boolean secure, UUID userId) {
        log.info("Getting file URL: fileId={}, userId={}, secure={}", id, userId, secure);

        // Get file (user-scoped)
        File file = fileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("File not found: " + id));

        // Get resource details from Cloudinary to extract format and resource type
        Map<String, Object> resourceDetails = storageService.getResourceDetails(file.getCloudinaryPublicId());
        String format = (String) resourceDetails.get("format");
        String resourceType = (String) resourceDetails.get("resource_type");

        // Generate URL
        String url = storageService.getFileUrl(file.getCloudinaryPublicId(), secure);

        FileUrlResponse response = new FileUrlResponse();
        response.setUrl(url);
        response.setPublicId(file.getCloudinaryPublicId());
        response.setFormat(format);
        response.setResourceType(resourceType);

        log.info("File URL generated successfully: fileId={}", id);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public TransformResponse transform(UUID id, TransformRequest request, UUID userId) {
        log.info("Transforming file: fileId={}, userId={}, width={}, height={}, crop={}, quality={}, format={}",
                id, userId, request.getWidth(), request.getHeight(), request.getCrop(), request.getQuality(),
                request.getFormat());

        // Get file (user-scoped)
        File file = fileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("File not found: " + id));

        // Validate file type supports transformations
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
            throw new RuntimeException("File type does not support transformations: " + contentType);
        }

        // Get original URL
        String originalUrl = storageService.getFileUrl(file.getCloudinaryPublicId(), true);

        // Generate transformed URL
        String transformedUrl = storageService.getTransformUrl(
                file.getCloudinaryPublicId(),
                true,
                request.getWidth(),
                request.getHeight(),
                request.getCrop(),
                request.getQuality(),
                request.getFormat());

        TransformResponse response = new TransformResponse();
        response.setTransformedUrl(transformedUrl);
        response.setOriginalUrl(originalUrl);

        log.info("File transformed successfully: fileId={}", id);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public TransformResponse getTransformUrl(UUID id, Integer width, Integer height, String crop, String quality,
            String format, UUID userId) {
        log.info(
                "Getting transformation URL: fileId={}, userId={}, width={}, height={}, crop={}, quality={}, format={}",
                id, userId, width, height, crop, quality, format);

        // Get file (user-scoped)
        File file = fileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("File not found: " + id));

        // Validate file type supports transformations
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
            throw new RuntimeException("File type does not support transformations: " + contentType);
        }

        // Get original URL
        String originalUrl = storageService.getFileUrl(file.getCloudinaryPublicId(), true);

        // Generate transformed URL
        String transformedUrl = storageService.getTransformUrl(
                file.getCloudinaryPublicId(),
                true,
                width,
                height,
                crop,
                quality,
                format);

        TransformResponse response = new TransformResponse();
        response.setTransformedUrl(transformedUrl);
        response.setOriginalUrl(originalUrl);

        log.info("Transformation URL generated successfully: fileId={}", id);
        return response;
    }

    @Override
    @Transactional
    public BulkUploadResponse bulkUpload(MultipartFile[] files, String folderPath, UUID userId) {
        log.info("Bulk uploading files: userId={}, count={}, folderPath={}", userId, files.length, folderPath);

        // Validate userId is not null
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Validate files array
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No files provided");
        }

        // Validate maximum file count (100)
        if (files.length > 100) {
            throw new IllegalArgumentException("Maximum 100 files allowed per batch");
        }

        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Create batch job
        BatchJob batchJob = new BatchJob(user, BatchJobType.UPLOAD, files.length);
        batchJob.setStatus(BatchJobStatus.QUEUED);
        batchJob.setCreatedAt(Instant.now());
        batchJob.setUpdatedAt(Instant.now());

        // Save batch job
        BatchJob savedBatchJob = batchJobRepository.save(batchJob);
        log.info("Batch job created: batchId={}, totalItems={}", savedBatchJob.getId(), files.length);

        // TODO: Process files asynchronously (this will be implemented in a separate
        // service/component)
        // For now, we just create the batch job and return the response

        BulkUploadResponse response = new BulkUploadResponse();
        response.setBatchId(savedBatchJob.getId());
        response.setJobType("UPLOAD"); // Using UPLOAD instead of BULK_UPLOAD to match enum
        response.setStatus(savedBatchJob.getStatus().name());
        response.setTotalItems(savedBatchJob.getTotalItems());
        response.setMessage("Bulk upload job has been queued for processing");
        response.setStatusUrl("/api/batches/" + savedBatchJob.getId() + "/status");

        log.info("Bulk upload job queued successfully: batchId={}", savedBatchJob.getId());
        return response;
    }

    /**
     * Convert File entity to FileResponse DTO
     */
    private FileResponse toFileResponse(File file) {
        FileResponse response = new FileResponse();
        response.setId(file.getId());
        response.setFilename(file.getFilename());
        response.setContentType(file.getContentType());
        response.setFileSize(file.getFileSize());
        response.setFolderPath(file.getFolderPath());
        response.setCloudinaryUrl(file.getCloudinaryUrl());
        response.setCloudinarySecureUrl(file.getCloudinarySecureUrl());
        response.setCreatedAt(file.getCreatedAt());
        response.setUpdatedAt(file.getUpdatedAt());
        return response;
    }

    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1).toLowerCase();
        }
        return null;
    }

    /**
     * Format file size in human-readable format
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
