package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.*;
import github.vijay_papanaboina.cloud_storage_api.exception.*;
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
    public FileResponse upload(MultipartFile file, Optional<String> folderPath, Optional<String> filename,
            UUID userId) {
        log.info("Uploading file for user: userId={}, filename={}, size={}", userId, file.getOriginalFilename(),
                file.getSize());
        // Validate userId is not null
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId, userId));

        // Normalize folderPath and convert to String for StorageService
        String normalizedFolderPath = optionalToString(normalizeOptionalString(folderPath));
        // Validate folder path format
        validateFolderPath(normalizedFolderPath);

        // Construct Cloudinary folder path with userId prefix for user isolation
        // Format: {userId}/{userFolderPath} or {userId} for root
        String cloudinaryFolderPath = constructCloudinaryFolderPath(userId, normalizedFolderPath);

        // Upload to Cloudinary with error handling
        Map<String, Object> uploadOptions = new HashMap<>();
        Map<String, Object> cloudinaryResult;
        try {
            cloudinaryResult = storageService.uploadFile(file, cloudinaryFolderPath, uploadOptions);
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected exception during file upload: userId={}, filename={}, error={}",
                    userId, file.getOriginalFilename(), e.getMessage(), e);
            throw new StorageException("Failed to upload file: " + e.getMessage(), e);
        }

        // Validate Cloudinary response
        if (cloudinaryResult == null) {
            log.error("Cloudinary returned null response: userId={}, filename={}", userId, file.getOriginalFilename());
            throw new StorageException("Storage service returned invalid response: upload result is null");
        }

        // Extract Cloudinary response
        String fullPublicId = (String) cloudinaryResult.get("public_id");
        String cloudinaryUrl = (String) cloudinaryResult.get("url");
        String cloudinarySecureUrl = (String) cloudinaryResult.get("secure_url");

        // Validate required fields
        if (fullPublicId == null || fullPublicId.isEmpty()) {
            log.error("Cloudinary response missing public_id: userId={}, filename={}", userId,
                    file.getOriginalFilename());
            throw new StorageException("Storage service returned invalid response: missing public_id");
        }

        // Extract UUID from full public ID (remove userId and folder path prefix)
        // Cloudinary returns: {userId}/{folderPath}/{uuid}, we need just the UUID for
        // database
        String uuid = extractUuidFromPublicId(fullPublicId);

        // Use provided filename or original filename, then sanitize it
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new BadRequestException("File original filename cannot be null or empty");
        }
        String filenameToUse = filename.orElse(originalFilename);
        String sanitizedFilename = sanitizeFilename(filenameToUse);

        // Create File entity
        File fileEntity = new File(sanitizedFilename, file.getContentType(), file.getSize(), user);
        fileEntity.setFolderPath(normalizedFolderPath); // Store user's original path (without userId prefix)
        fileEntity.setCloudinaryPublicId(uuid); // Store only UUID (userId prefix is added when needed)
        fileEntity.setCloudinaryUrl(cloudinaryUrl);
        fileEntity.setCloudinarySecureUrl(cloudinarySecureUrl);
        fileEntity.setCreatedAt(Instant.now());

        // Save to database
        File savedFile = fileRepository.save(fileEntity);
        log.info("File uploaded successfully: fileId={}, userId={}, filename={}, cloudinaryPath={}, uuid={}",
                savedFile.getId(), userId, sanitizedFilename, fullPublicId, uuid);

        return toFileResponse(savedFile);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource download(UUID id, UUID userId) {
        log.info("Downloading file: fileId={}, userId={}", id, userId);

        // Get file (user-scoped)
        File file = fileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File with ID " + id + " not found", id));

        // Reconstruct full Cloudinary public ID with userId prefix
        String fullPublicId = constructCloudinaryPublicId(userId, file.getFolderPath(), file.getCloudinaryPublicId());

        // Download from Cloudinary with error handling
        byte[] fileBytes;
        try {
            fileBytes = storageService.downloadFile(fullPublicId);
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (CloudFileNotFoundException e) {
            // Wrap CloudFileNotFoundException from storage service as
            // ResourceNotFoundException
            throw new ResourceNotFoundException("File not found in storage: " + e.getMessage(), id);
        } catch (Exception e) {
            String publicId = file.getCloudinaryPublicId();
            log.error("Unexpected error during file download: fileId={}, publicId={}, error={}",
                    id, publicId, e.getMessage(), e);
            throw new StorageException("Failed to download file: " + e.getMessage(), e);
        }

        // Validate fileBytes is not null
        if (fileBytes == null) {
            log.error("Storage service returned null file bytes: fileId={}, publicId={}", id,
                    file.getCloudinaryPublicId());
            throw new StorageException("Storage service returned invalid response: file bytes are null");
        }

        log.info("File downloaded successfully: fileId={}", id);
        return new ByteArrayResource(fileBytes);
    }

    @Override
    @Transactional(readOnly = true)
    public FileUrlResponse getSignedDownloadUrl(UUID id, UUID userId, int expirationMinutes) {
        log.info("Generating signed download URL: fileId={}, userId={}, expiresIn={} minutes",
                id, userId, expirationMinutes);

        // Validate user ownership (user-scoped access control)
        File file = fileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new CloudFileNotFoundException(id));

        // Reconstruct full Cloudinary public ID with userId prefix
        String fullPublicId = constructCloudinaryPublicId(userId, file.getFolderPath(), file.getCloudinaryPublicId());

        // Get resource details from Cloudinary to extract format and resource type
        Map<String, Object> resourceDetails;
        try {
            resourceDetails = storageService.getResourceDetails(fullPublicId);
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting resource details: fileId={}, publicId={}, error={}",
                    id, file.getCloudinaryPublicId(), e.getMessage(), e);
            throw new StorageException("Failed to get resource details: " + e.getMessage(), e);
        }

        if (resourceDetails == null) {
            log.error("Storage service returned null resource details: fileId={}, publicId={}", id,
                    file.getCloudinaryPublicId());
            throw new StorageException("Storage service returned invalid response: resource details are null");
        }

        String format = (String) resourceDetails.get("format");
        String resourceType = (String) resourceDetails.get("resource_type");

        // Generate signed URL with expiration, passing resourceType to avoid "not
        // found" errors
        String signedUrl;
        try {
            signedUrl = storageService.generateSignedDownloadUrl(fullPublicId, expirationMinutes,
                    resourceType);
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error generating signed URL: fileId={}, publicId={}, error={}",
                    id, file.getCloudinaryPublicId(), e.getMessage(), e);
            throw new StorageException("Failed to generate signed URL: " + e.getMessage(), e);
        }

        // Calculate expiration timestamp
        Instant expiresAt = Instant.now().plusSeconds(expirationMinutes * 60L);

        FileUrlResponse response = new FileUrlResponse();
        response.setUrl(signedUrl);
        response.setPublicId(file.getCloudinaryPublicId()); // Return only UUID to user (not full path)
        response.setFormat(format);
        response.setResourceType(resourceType);
        response.setExpiresAt(expiresAt);

        log.info("Signed download URL generated successfully: fileId={}, expiresAt={}", id, expiresAt);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public FileResponse getById(UUID id, UUID userId) {
        log.info("Getting file by ID: fileId={}, userId={}", id, userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        File file = fileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new CloudFileNotFoundException(id));

        return toFileResponse(file);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FileResponse> list(Pageable pageable, Optional<String> contentType, Optional<String> folderPath,
            UUID userId) {
        // Normalize optional parameters
        Optional<String> normalizedContentType = normalizeOptionalString(contentType);
        Optional<String> normalizedFolderPath = normalizeOptionalString(folderPath);

        log.info("Listing files: userId={}, contentType={}, folderPath={}, page={}, size={}",
                userId, normalizedContentType.orElse(null), normalizedFolderPath.orElse(null),
                pageable.getPageNumber(), pageable.getPageSize());

        Page<File> files;
        if (normalizedContentType.isPresent() && normalizedFolderPath.isPresent()) {
            // Filter by both contentType and folderPath
            files = fileRepository.findByUserIdAndDeletedFalseAndContentTypeAndFolderPath(
                    userId, normalizedContentType.get(), normalizedFolderPath.get(), pageable);
        } else if (normalizedContentType.isPresent()) {
            files = fileRepository.findByUserIdAndDeletedFalseAndContentType(userId, normalizedContentType.get(),
                    pageable);
        } else if (normalizedFolderPath.isPresent()) {
            files = fileRepository.findByUserIdAndDeletedFalseAndFolderPath(userId, normalizedFolderPath.get(),
                    pageable);
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
                .orElseThrow(() -> new CloudFileNotFoundException(id));

        // Validate request is not null
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        // Track if folderPath is changing
        String oldFolderPath = file.getFolderPath();
        String newFolderPath = request.getFolderPath();
        boolean folderPathChanged = newFolderPath != null
                && !java.util.Objects.equals(oldFolderPath, newFolderPath);

        // Update filename if provided
        if (request.getFilename() != null && !request.getFilename().isBlank()) {
            file.setFilename(request.getFilename());
        }

        // Update folderPath and move file in Cloudinary if folderPath changed
        if (folderPathChanged) {
            // Validate new folder path
            validateFolderPath(newFolderPath);
            try {
                // Construct current full Cloudinary public ID with userId prefix
                String currentFullPublicId = constructCloudinaryPublicId(userId, oldFolderPath,
                        file.getCloudinaryPublicId());

                // Get resource type to pass to moveFile to avoid "not found" errors
                String resourceType = null;
                try {
                    Map<String, Object> resourceDetails = storageService.getResourceDetails(currentFullPublicId);
                    resourceType = (String) resourceDetails.get("resource_type");
                } catch (Exception e) {
                    log.warn("Could not get resource type before move, will use auto-detection: fileId={}, error={}",
                            id, e.getMessage());
                }

                // Construct new Cloudinary folder path with userId prefix
                String newCloudinaryFolderPath = constructCloudinaryFolderPath(userId, newFolderPath);

                // Move file in Cloudinary to new folder, passing resourceType if available
                Map<String, Object> moveResult = storageService.moveFile(currentFullPublicId, newCloudinaryFolderPath,
                        resourceType);

                // Extract updated public ID and URLs from Cloudinary response
                String newFullPublicId = (String) moveResult.get("public_id");
                String newCloudinaryUrl = (String) moveResult.get("url");
                String newCloudinarySecureUrl = (String) moveResult.get("secure_url");

                // Update file metadata
                // Extract just the UUID part from the new public_id (remove userId and folder
                // path)
                String uuidPart = extractUuidFromPublicId(newFullPublicId);
                file.setCloudinaryPublicId(uuidPart != null ? uuidPart : newFullPublicId);
                file.setCloudinaryUrl(newCloudinaryUrl);
                file.setCloudinarySecureUrl(newCloudinarySecureUrl);
                file.setFolderPath(newFolderPath); // Store user's original path (without userId prefix)

                log.info(
                        "File moved in Cloudinary: fileId={}, userId={}, oldFolder={}, newFolder={}, newFullPublicId={}",
                        id, userId, oldFolderPath, newFolderPath, newFullPublicId);
            } catch (Exception e) {
                log.error("Failed to move file in Cloudinary: fileId={}, oldFolder={}, newFolder={}, error={}",
                        id, oldFolderPath, newFolderPath, e.getMessage(), e);
                throw new StorageException("Failed to move file in Cloudinary: " + e.getMessage(), e);
            }
        } else if (newFolderPath != null) {
            // Folder path provided but same as current - just update in database
            file.setFolderPath(newFolderPath);
        }

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
                .orElseThrow(() -> new CloudFileNotFoundException(id));

        // Soft delete
        file.setDeleted(true);
        file.setDeletedAt(Instant.now());

        // Save to database
        fileRepository.save(file);
        log.info("File deleted successfully: fileId={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FileResponse> search(String query, Optional<String> contentType, Optional<String> folderPath,
            Pageable pageable,
            UUID userId) {
        // Normalize optional parameters
        Optional<String> normalizedContentType = normalizeOptionalString(contentType);
        Optional<String> normalizedFolderPath = normalizeOptionalString(folderPath);

        log.info("Searching files: userId={}, query={}, contentType={}, folderPath={}",
                userId, query, normalizedContentType.orElse(null), normalizedFolderPath.orElse(null));

        Page<File> files;
        if (normalizedContentType.isPresent() || normalizedFolderPath.isPresent()) {
            files = fileRepository.findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCaseWithFilters(
                    userId, query, optionalToString(normalizedContentType), optionalToString(normalizedFolderPath),
                    pageable);
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
    public FileStatisticsResponse getStatistics(UUID userId) {
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

        // Build and return typed response
        return new FileStatisticsResponse(totalFiles, totalSize, averageFileSize, storageUsed, byContentType,
                byFolder);
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
                .orElseThrow(() -> new CloudFileNotFoundException(id));

        // Reconstruct full Cloudinary public ID with userId prefix
        String fullPublicId = constructCloudinaryPublicId(userId, file.getFolderPath(), file.getCloudinaryPublicId());

        // Get resource details from Cloudinary to extract format and resource type
        Map<String, Object> resourceDetails;
        try {
            resourceDetails = storageService.getResourceDetails(fullPublicId);
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting resource details: fileId={}, publicId={}, error={}",
                    id, file.getCloudinaryPublicId(), e.getMessage(), e);
            throw new StorageException("Failed to get resource details: " + e.getMessage(), e);
        }

        if (resourceDetails == null) {
            log.error("Storage service returned null resource details: fileId={}, publicId={}", id,
                    file.getCloudinaryPublicId());
            throw new StorageException("Storage service returned invalid response: resource details are null");
        }

        String format = (String) resourceDetails.get("format");
        String resourceType = (String) resourceDetails.get("resource_type");

        // Generate URL with error handling
        String url;
        try {
            url = storageService.getFileUrl(fullPublicId, secure);
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error generating file URL: fileId={}, publicId={}, error={}",
                    id, file.getCloudinaryPublicId(), e.getMessage(), e);
            throw new StorageException("Failed to generate file URL: " + e.getMessage(), e);
        }

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
                .orElseThrow(() -> new CloudFileNotFoundException(id));

        // Validate file type supports transformations
        validateFileTypeForTransformation(file.getContentType(), "transformations");

        // Reconstruct full Cloudinary public ID with userId prefix
        String fullPublicId = constructCloudinaryPublicId(userId, file.getFolderPath(), file.getCloudinaryPublicId());

        // Get original URL with error handling
        String originalUrl;
        try {
            originalUrl = storageService.getFileUrl(fullPublicId, true);
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting original URL: fileId={}, publicId={}, error={}",
                    id, fullPublicId, e.getMessage(), e);
            throw new StorageException("Failed to get original URL: " + e.getMessage(), e);
        }

        // Generate transformed URL with error handling
        String transformedUrl;
        try {
            transformedUrl = storageService.getTransformUrl(
                    fullPublicId,
                    true,
                    request.getWidth(),
                    request.getHeight(),
                    request.getCrop(),
                    request.getQuality(),
                    request.getFormat());
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error generating transformed URL: fileId={}, publicId={}, error={}",
                    id, file.getCloudinaryPublicId(), e.getMessage(), e);
            throw new StorageException("Failed to generate transformed URL: " + e.getMessage(), e);
        }

        TransformResponse response = new TransformResponse();
        response.setTransformedUrl(transformedUrl);
        response.setOriginalUrl(originalUrl);

        log.info("File transformed successfully: fileId={}", id);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public TransformResponse getTransformUrl(UUID id, Integer width, Integer height, Optional<String> crop,
            Optional<String> quality,
            Optional<String> format, UUID userId) {
        // Normalize optional parameters
        Optional<String> normalizedCrop = normalizeOptionalString(crop);
        Optional<String> normalizedQuality = normalizeOptionalString(quality);
        Optional<String> normalizedFormat = normalizeOptionalString(format);

        log.info(
                "Getting transformation URL: fileId={}, userId={}, width={}, height={}, crop={}, quality={}, format={}",
                id, userId, width, height, normalizedCrop.orElse(null), normalizedQuality.orElse(null),
                normalizedFormat.orElse(null));

        // Get file (user-scoped)
        File file = fileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new CloudFileNotFoundException(id));

        // Validate file type supports transformations
        validateFileTypeForTransformation(file.getContentType(), "transformations");

        // Reconstruct full Cloudinary public ID with userId prefix
        String fullPublicId = constructCloudinaryPublicId(userId, file.getFolderPath(), file.getCloudinaryPublicId());

        // Get original URL with error handling
        String originalUrl;
        try {
            originalUrl = storageService.getFileUrl(fullPublicId, true);
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting original URL: fileId={}, publicId={}, error={}",
                    id, fullPublicId, e.getMessage(), e);
            throw new StorageException("Failed to get original URL: " + e.getMessage(), e);
        }

        // Generate transformed URL with error handling
        String transformedUrl;
        try {
            transformedUrl = storageService.getTransformUrl(
                    fullPublicId,
                    true,
                    width,
                    height,
                    optionalToString(normalizedCrop),
                    optionalToString(normalizedQuality),
                    optionalToString(normalizedFormat));
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error generating transformed URL: fileId={}, publicId={}, error={}",
                    id, file.getCloudinaryPublicId(), e.getMessage(), e);
            throw new StorageException("Failed to generate transformed URL: " + e.getMessage(), e);
        }

        TransformResponse response = new TransformResponse();
        response.setTransformedUrl(transformedUrl);
        response.setOriginalUrl(originalUrl);

        log.info("Transformation URL generated successfully: fileId={}", id);
        return response;
    }

    @Override
    @Transactional
    public BulkUploadResponse bulkUpload(MultipartFile[] files, Optional<String> folderPath, UUID userId) {
        // Normalize folderPath
        String normalizedFolderPath = optionalToString(normalizeOptionalString(folderPath));
        // Validate folder path format
        validateFolderPath(normalizedFolderPath);
        log.info("Bulk uploading files: userId={}, count={}, folderPath={}", userId, files.length,
                normalizedFolderPath);

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
                .orElseThrow(() -> new NotFoundException("User not found: " + userId, userId));

        // Create batch job
        BatchJob batchJob = new BatchJob(user, BatchJobType.UPLOAD, files.length);
        batchJob.setStatus(BatchJobStatus.QUEUED);
        batchJob.setCreatedAt(Instant.now());

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
        response.setProcessedItems(savedBatchJob.getProcessedItems());
        response.setFailedItems(savedBatchJob.getFailedItems());
        response.setProgress(savedBatchJob.getProgress());
        response.setMessage("Bulk upload job has been queued for processing");
        response.setStatusUrl("/api/batches/" + savedBatchJob.getId() + "/status");

        log.info("Bulk upload job queued successfully: batchId={}", savedBatchJob.getId());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public BulkUploadResponse getBulkUploadStatus(UUID jobId, UUID userId) {
        log.info("Getting bulk upload status: jobId={}, userId={}", jobId, userId);

        // Validate userId is not null
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Get batch job (user-scoped)
        BatchJob batchJob = batchJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new NotFoundException("Batch job not found: " + jobId, jobId));

        // Build response with current status
        BulkUploadResponse response = new BulkUploadResponse();
        response.setBatchId(batchJob.getId());
        response.setJobType(batchJob.getJobType().name());
        response.setStatus(batchJob.getStatus().name());
        response.setTotalItems(batchJob.getTotalItems());
        response.setProcessedItems(batchJob.getProcessedItems());
        response.setFailedItems(batchJob.getFailedItems());
        response.setProgress(batchJob.getProgress());
        response.setStatusUrl("/api/batches/" + batchJob.getId() + "/status");

        // Set appropriate message based on status
        String message;
        switch (batchJob.getStatus()) {
            case QUEUED:
                message = "Bulk upload job is queued for processing";
                break;
            case PROCESSING:
                message = String.format("Bulk upload job is processing: %d/%d items completed",
                        batchJob.getProcessedItems(), batchJob.getTotalItems());
                break;
            case COMPLETED:
                message = String.format("Bulk upload job completed: %d items processed, %d failed",
                        batchJob.getProcessedItems(), batchJob.getFailedItems());
                break;
            case FAILED:
                message = "Bulk upload job failed: " + batchJob.getErrorMessage() != null ? batchJob.getErrorMessage()
                        : "Unknown error";
                break;
            default:
                message = "Bulk upload job status: " + batchJob.getStatus().name();
        }
        response.setMessage(message);

        log.info("Bulk upload status retrieved: jobId={}, status={}, progress={}%", jobId, batchJob.getStatus(),
                batchJob.getProgress());
        return response;
    }

    /**
     * Normalize Optional<String> by treating empty or blank strings as empty
     * Optional.
     * This ensures consistent handling of optional parameters.
     *
     * @param value Optional string value
     * @return Empty Optional if value is empty or blank, otherwise the original
     *         Optional
     */
    private Optional<String> normalizeOptionalString(Optional<String> value) {
        return value.filter(s -> s != null && !s.isBlank());
    }

    /**
     * Convert Optional<String> to String for use with StorageService.
     * Returns null if Optional is empty.
     *
     * @param value Optional string value
     * @return String value or null if empty
     */
    private String optionalToString(Optional<String> value) {
        return normalizeOptionalString(value).orElse(null);
    }

    /**
     * Construct Cloudinary folder path with userId prefix for user isolation.
     * Format: {userId}/{userFolderPath} or {userId} for root.
     *
     * @param userId         The user ID (required for user isolation)
     * @param userFolderPath The user's folder path (may be null or empty for root
     *                       folder)
     * @return Cloudinary folder path with userId prefix
     */
    private String constructCloudinaryFolderPath(UUID userId, String userFolderPath) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Start with userId prefix
        StringBuilder fullPath = new StringBuilder(userId.toString());

        // Add folder path if provided
        if (userFolderPath != null && !userFolderPath.isEmpty() && !userFolderPath.isBlank()) {
            // Remove leading slash if present
            String normalizedFolder = userFolderPath.startsWith("/") ? userFolderPath.substring(1) : userFolderPath;
            // Remove trailing slash if present
            normalizedFolder = normalizedFolder.endsWith("/")
                    ? normalizedFolder.substring(0, normalizedFolder.length() - 1)
                    : normalizedFolder;
            if (!normalizedFolder.isEmpty()) {
                fullPath.append("/").append(normalizedFolder);
            }
        }

        return fullPath.toString();
    }

    /**
     * Construct the full Cloudinary public ID with userId prefix and folder path.
     * This ensures user isolation in Cloudinary storage.
     * Format: {userId}/{userFolderPath}/{uuid} or {userId}/{uuid} for root.
     *
     * @param userId         The user ID (required for user isolation)
     * @param userFolderPath The user's folder path (may be null or empty for root
     *                       folder)
     * @param uuid           The base public ID (UUID)
     * @return Full public ID with userId prefix and folder path
     */
    private String constructCloudinaryPublicId(UUID userId, String userFolderPath, String uuid) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (uuid == null || uuid.isEmpty()) {
            throw new IllegalArgumentException("UUID cannot be null or empty");
        }

        // Start with userId prefix
        StringBuilder fullPath = new StringBuilder(userId.toString());

        // Add folder path if provided
        if (userFolderPath != null && !userFolderPath.isEmpty() && !userFolderPath.isBlank()) {
            // Remove leading slash if present
            String normalizedFolder = userFolderPath.startsWith("/") ? userFolderPath.substring(1) : userFolderPath;
            // Remove trailing slash if present
            normalizedFolder = normalizedFolder.endsWith("/")
                    ? normalizedFolder.substring(0, normalizedFolder.length() - 1)
                    : normalizedFolder;
            if (!normalizedFolder.isEmpty()) {
                fullPath.append("/").append(normalizedFolder);
            }
        }

        // Add UUID
        fullPath.append("/").append(uuid);

        return fullPath.toString();
    }

    /**
     * Extract the UUID part from a full public ID that may include folder path.
     * In Cloudinary, the public_id can be "folder/path/uuid" or just "uuid".
     *
     * @param fullPublicId The full public ID (may include folder path)
     * @return The UUID part (last segment after the last slash), or the original if
     *         no slash found
     */
    private String extractUuidFromPublicId(String fullPublicId) {
        if (fullPublicId == null || fullPublicId.isEmpty()) {
            return fullPublicId;
        }
        int lastSlashIndex = fullPublicId.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < fullPublicId.length() - 1) {
            return fullPublicId.substring(lastSlashIndex + 1);
        }
        return fullPublicId;
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
     * Sanitize filename to prevent security issues
     * Removes path separators, null bytes, control characters, and validates
     * against reserved names
     * 
     * @param filename Filename to sanitize
     * @return Sanitized filename
     * @throws BadRequestException if filename is invalid
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new BadRequestException("Filename cannot be null or empty");
        }

        // Remove leading/trailing whitespace
        String sanitized = filename.trim();

        // Remove path separators (prevent path traversal)
        sanitized = sanitized.replace("/", "_").replace("\\", "_");

        // Remove null bytes
        sanitized = sanitized.replace("\0", "");

        // Remove control characters (including tab, newline, carriage return)
        // Only allow printable characters (code >= 32)
        StringBuilder sb = new StringBuilder();
        for (char c : sanitized.toCharArray()) {
            if (c >= 32) {
                sb.append(c);
            }
        }
        sanitized = sb.toString();

        // Validate against reserved names (Windows)
        String upperName = sanitized.toUpperCase();
        String[] reservedNames = { "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7",
                "COM8", "COM9",
                "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" };
        for (String reserved : reservedNames) {
            if (upperName.equals(reserved) || upperName.startsWith(reserved + ".")) {
                throw new BadRequestException("Filename cannot be a reserved name: " + reserved);
            }
        }

        // Validate against special names
        if (sanitized.equals(".") || sanitized.equals("..")) {
            throw new BadRequestException("Filename cannot be '.' or '..'");
        }

        // Ensure filename is not empty after sanitization
        if (sanitized.isEmpty()) {
            throw new BadRequestException("Filename cannot be empty after sanitization");
        }

        // Limit filename length (reasonable limit)
        if (sanitized.length() > 255) {
            throw new BadRequestException("Filename cannot exceed 255 characters");
        }

        return sanitized;
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

    /**
     * Validate folder path format
     * 
     * @param folderPath Folder path to validate
     * @throws BadRequestException if folder path is invalid
     */
    private void validateFolderPath(String folderPath) {
        if (folderPath != null && !folderPath.isEmpty()) {
            if (!folderPath.startsWith("/")) {
                throw new BadRequestException("Folder path must start with '/'");
            }
            if (folderPath.length() > 500) {
                throw new BadRequestException("Folder path must not exceed 500 characters");
            }
        }
    }

    /**
     * Validate file type supports transformations
     * 
     * @param contentType Content type to validate
     * @param operation   Operation name for error message
     * @throws BadRequestException if file type does not support transformations
     */
    private void validateFileTypeForTransformation(String contentType, String operation) {
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
            throw new BadRequestException(
                    String.format("File type does not support %s: %s", operation, contentType));
        }
    }
}
