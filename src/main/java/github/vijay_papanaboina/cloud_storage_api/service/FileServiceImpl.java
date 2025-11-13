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
import github.vijay_papanaboina.cloud_storage_api.validation.SafeFolderPathValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
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

        // Validate original filename early (before storage service call)
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new BadRequestException("File original filename cannot be null or empty");
        }

        // Validate custom filename early if provided (before storage service call)
        String filenameToUse = filename.orElse(originalFilename);
        if (filename.isPresent()) {
            // Validate custom filename early - check for path separators first
            if (filenameToUse.contains("/") || filenameToUse.contains("\\")) {
                throw new BadRequestException("Filename cannot be set to a value containing path separators");
            }
            // Validate other filename constraints - sanitizeFilename throws
            // BadRequestException for invalid filenames
            sanitizeFilename(filenameToUse);
        }

        // Normalize folderPath and convert to String for StorageService
        String normalizedFolderPath = optionalToString(normalizeOptionalString(folderPath));
        // Validate folder path format
        validateFolderPath(normalizedFolderPath);

        // Sanitize filename (validation already done earlier, this just sanitizes)
        String sanitizedFilename = sanitizeFilename(filenameToUse);

        // FIXED TOCTOU: Reserve unique filename in database FIRST (before Cloudinary
        // upload)
        // This ensures the final filename is determined and locked in before committing
        // to
        // Cloudinary storage.
        // If Cloudinary upload fails after filename reservation, we clean up the DB
        // record.
        // This prevents mismatches between DB filename and Cloudinary resource.
        File savedFile = null;
        int maxRetries = 5;
        int retryCount = 0;
        String finalUniqueFilename = null;
        StorageException finalException = null;

        // Step 1: Reserve unique filename in database with retry logic
        try {
            while (retryCount <= maxRetries) {
                try {
                    // Generate unique filename for this retry attempt
                    String currentFilename = generateUniqueFilename(normalizedFolderPath, sanitizedFilename, userId);
                    finalUniqueFilename = currentFilename; // Track the final filename used

                    // Generate temporary UUID for Cloudinary public ID (required for NOT NULL
                    // constraint)
                    // This will be replaced with the actual Cloudinary public ID after upload
                    String tempCloudinaryPublicId = UUID.randomUUID().toString();
                    String tempCloudinaryUrl = "https://placeholder.cloudinary.com/temp/" + tempCloudinaryPublicId;
                    String tempCloudinarySecureUrl = "https://placeholder.cloudinary.com/temp/"
                            + tempCloudinaryPublicId;

                    // Create File entity with the generated unique filename
                    // Use temporary placeholder values for Cloudinary fields - will be updated
                    // after upload
                    File fileEntity = new File(currentFilename, file.getContentType(), file.getSize(), user);
                    fileEntity.setFolderPath(normalizedFolderPath); // Store user's original path (without userId
                                                                    // prefix)
                    // Set temporary Cloudinary values to satisfy NOT NULL constraints
                    fileEntity.setCloudinaryPublicId(tempCloudinaryPublicId);
                    fileEntity.setCloudinaryUrl(tempCloudinaryUrl);
                    fileEntity.setCloudinarySecureUrl(tempCloudinarySecureUrl);
                    fileEntity.setCreatedAt(Instant.now());

                    // Save to database - this reserves the filename and enforces uniqueness
                    // constraint
                    savedFile = fileRepository.save(fileEntity);
                    log.info(
                            "Filename reserved in database: fileId={}, userId={}, filename={}, uniqueFilename={}, retries={}",
                            savedFile.getId(), userId, sanitizedFilename, finalUniqueFilename, retryCount);
                    break; // Success, exit retry loop - filename is now reserved
                } catch (DataIntegrityViolationException e) {
                    // Constraint violation - likely a race condition where another thread created
                    // a file with the same name between our check and save
                    retryCount++;
                    if (retryCount > maxRetries) {
                        log.error(
                                "Failed to reserve filename after {} retries due to constraint violations: userId={}, filename={}, error={}",
                                maxRetries, userId, sanitizedFilename, e.getMessage(), e);
                        finalException = new StorageException(
                                "Failed to upload file: unable to generate unique filename after multiple attempts. Please try again.",
                                e);
                        break; // Exit retry loop
                    }

                    // Log retry attempt - next iteration will generate a new unique filename
                    log.warn(
                            "Constraint violation during filename reservation (retry {}/{}): userId={}, filename={}, will generate new unique name on next attempt",
                            retryCount, maxRetries, userId, sanitizedFilename);
                    // Note: Don't generate filename here - let the next loop iteration generate it
                    // This ensures we always check the latest DB state before generating
                }
            }

            // This should never be null due to the retry logic, but Java requires the check
            if (savedFile == null) {
                finalException = new StorageException(
                        "Failed to reserve filename: unexpected error during save operation");
            }
        } catch (Exception e) {
            // Catch any other unexpected exceptions during DB save
            log.error("Unexpected exception during filename reservation: userId={}, filename={}, error={}",
                    userId, sanitizedFilename, e.getMessage(), e);
            finalException = new StorageException("Failed to reserve filename in database: " + e.getMessage(), e);
        }

        // If filename reservation failed, abort early (no Cloudinary upload needed)
        if (finalException != null || savedFile == null) {
            if (finalException != null) {
                throw finalException;
            }
            throw new StorageException("Failed to reserve filename: unexpected error during save operation");
        }

        // At this point, savedFile is guaranteed to be non-null
        final File reservedFile = savedFile;

        // Step 2: Upload to Cloudinary now that filename is reserved
        // Construct Cloudinary folder path with userId prefix for user isolation
        // Format: {userId}/{userFolderPath} or {userId} for root
        String cloudinaryFolderPath = constructCloudinaryFolderPath(userId, normalizedFolderPath);
        Map<String, Object> uploadOptions = new HashMap<>();
        Map<String, Object> cloudinaryResult = null;
        String fullPublicId = null;
        String cloudinaryUrl = null;
        String cloudinarySecureUrl = null;
        String uuid = null;

        try {
            cloudinaryResult = storageService.uploadFile(file, cloudinaryFolderPath, uploadOptions);
        } catch (StorageException e) {
            // Cloudinary upload failed - clean up the reserved filename in database
            try {
                log.warn(
                        "Cloudinary upload failed after filename reservation, cleaning up database record: fileId={}, userId={}, filename={}",
                        reservedFile.getId(), userId, finalUniqueFilename);
                fileRepository.delete(reservedFile);
                log.info("Successfully cleaned up reserved filename in database: fileId={}", reservedFile.getId());
            } catch (Exception cleanupException) {
                log.error("Error during database cleanup after Cloudinary upload failure: fileId={}, error={}",
                        reservedFile.getId(), cleanupException.getMessage(), cleanupException);
                // Continue to throw the original Cloudinary exception
            }
            // Re-throw the original Cloudinary exception
            throw e;
        } catch (Exception e) {
            // Cloudinary upload failed with unexpected exception - clean up the reserved
            // filename
            try {
                log.error(
                        "Unexpected exception during Cloudinary upload, cleaning up database record: fileId={}, userId={}, filename={}, error={}",
                        reservedFile.getId(), userId, finalUniqueFilename, e.getMessage());
                fileRepository.delete(reservedFile);
                log.info("Successfully cleaned up reserved filename in database: fileId={}", reservedFile.getId());
            } catch (Exception cleanupException) {
                log.error("Error during database cleanup after Cloudinary upload failure: fileId={}, error={}",
                        reservedFile.getId(), cleanupException.getMessage(), cleanupException);
            }
            log.error("Unexpected exception during file upload: userId={}, filename={}, error={}",
                    userId, file.getOriginalFilename(), e.getMessage(), e);
            throw new StorageException("Failed to upload file: " + e.getMessage(), e);
        }

        // Validate Cloudinary response
        if (cloudinaryResult == null) {
            // Clean up database record
            try {
                log.error(
                        "Cloudinary returned null response, cleaning up database record: fileId={}, userId={}, filename={}",
                        reservedFile.getId(), userId, finalUniqueFilename);
                fileRepository.delete(reservedFile);
            } catch (Exception cleanupException) {
                log.error("Error during database cleanup: fileId={}, error={}",
                        reservedFile.getId(), cleanupException.getMessage(), cleanupException);
            }
            throw new StorageException("Storage service returned invalid response: upload result is null");
        }

        // Extract Cloudinary response
        fullPublicId = (String) cloudinaryResult.get("public_id");
        cloudinaryUrl = (String) cloudinaryResult.get("url");
        cloudinarySecureUrl = (String) cloudinaryResult.get("secure_url");

        // Validate required fields
        if (fullPublicId == null || fullPublicId.isEmpty()) {
            // Clean up database record
            try {
                log.error(
                        "Cloudinary response missing public_id, cleaning up database record: fileId={}, userId={}, filename={}",
                        reservedFile.getId(), userId, finalUniqueFilename);
                fileRepository.delete(reservedFile);
            } catch (Exception cleanupException) {
                log.error("Error during database cleanup: fileId={}, error={}",
                        reservedFile.getId(), cleanupException.getMessage(), cleanupException);
            }
            throw new StorageException("Storage service returned invalid response: missing public_id");
        }

        // Extract UUID from full public ID (remove userId and folder path prefix)
        // Cloudinary returns: {userId}/{folderPath}/{uuid}, we need just the UUID for
        // database
        uuid = extractUuidFromPublicId(fullPublicId);

        // Step 3: Update the reserved File entity with Cloudinary metadata
        try {
            reservedFile.setCloudinaryPublicId(uuid); // Store only UUID (userId prefix is added when needed)
            reservedFile.setCloudinaryUrl(cloudinaryUrl);
            reservedFile.setCloudinarySecureUrl(cloudinarySecureUrl);
            savedFile = fileRepository.save(reservedFile); // Update the entity with Cloudinary data

            log.info(
                    "File uploaded successfully: fileId={}, userId={}, filename={}, uniqueFilename={}, cloudinaryPath={}, uuid={}",
                    savedFile.getId(), userId, sanitizedFilename, finalUniqueFilename, fullPublicId, uuid);
        } catch (Exception e) {
            // Update failed - clean up both database and Cloudinary
            log.error("Failed to update file with Cloudinary metadata: fileId={}, userId={}, filename={}, error={}",
                    reservedFile.getId(), userId, finalUniqueFilename, e.getMessage(), e);

            // Clean up database
            try {
                fileRepository.delete(reservedFile);
                log.info("Cleaned up database record after update failure: fileId={}", reservedFile.getId());
            } catch (Exception dbCleanupException) {
                log.error("Error during database cleanup: fileId={}, error={}",
                        reservedFile.getId(), dbCleanupException.getMessage(), dbCleanupException);
            }

            // Clean up Cloudinary
            try {
                storageService.deleteFile(fullPublicId);
                log.info("Cleaned up Cloudinary resource after update failure: publicId={}", fullPublicId);
            } catch (Exception cloudinaryCleanupException) {
                log.error("Error during Cloudinary cleanup: publicId={}, error={}",
                        fullPublicId, cloudinaryCleanupException.getMessage(), cloudinaryCleanupException);
            }

            throw new StorageException("Failed to update file with Cloudinary metadata: " + e.getMessage(), e);
        }

        // Verify savedFile is not null (should never happen, but safety check)
        if (savedFile == null) {
            // This should not happen, but if it does, attempt cleanup
            if (fullPublicId != null) {
                try {
                    log.error("Unexpected null savedFile, attempting Cloudinary cleanup: publicId={}", fullPublicId);
                    storageService.deleteFile(fullPublicId);
                } catch (Exception cleanupException) {
                    log.error("Error during Cloudinary cleanup: publicId={}, error={}",
                            fullPublicId, cleanupException.getMessage(), cleanupException);
                }
            }
            throw new StorageException("Failed to upload file: unexpected error during save operation");
        }

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
    public Resource downloadByFilepath(String filepath, UUID userId) {
        log.info("Downloading file by filepath: filepath={}, userId={}", filepath, userId);

        // Parse filepath to extract folder path and filename
        String[] pathParts = parseFilepath(filepath);
        String folderPath = pathParts[0];
        String filename = pathParts[1];

        // Find file by folder path and filename
        Optional<File> fileOpt = fileRepository.findByUserIdAndFolderPathAndFilenameAndDeletedFalse(
                userId, folderPath, filename);

        if (fileOpt.isEmpty()) {
            throw new ResourceNotFoundException(
                    String.format("File not found: %s", filepath));
        }

        File file = fileOpt.get();

        // Use existing download logic
        return download(file.getId(), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public FileUrlResponse getSignedDownloadUrl(UUID id, UUID userId, int expirationMinutes) {
        log.info("Generating signed download URL: fileId={}, userId={}, expiresIn={} minutes",
                id, userId, expirationMinutes);

        // Validate user ownership (user-scoped access control)
        File file = validateFileAccess(id, userId);

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

        // If format is missing from Cloudinary response, try to infer it from file
        // metadata
        if (format == null || format.isEmpty()) {
            // Try to infer format from filename extension
            String filename = file.getFilename();
            if (filename != null && filename.contains(".")) {
                int lastDotIndex = filename.lastIndexOf('.');
                if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
                    format = filename.substring(lastDotIndex + 1).toLowerCase();
                    log.info("Inferred format from filename: fileId={}, filename={}, format={}", id, filename, format);
                }
            }

            // If still no format, try to infer from content type
            if ((format == null || format.isEmpty()) && file.getContentType() != null) {
                String contentType = file.getContentType();
                // Map common content types to formats
                if (contentType.contains("text/plain")) {
                    format = "txt";
                } else if (contentType.contains("application/pdf")) {
                    format = "pdf";
                } else if (contentType.contains("application/json")) {
                    format = "json";
                } else if (contentType.contains("application/octet-stream")) {
                    // Default to "bin" for binary files
                    format = "bin";
                }
                if (format != null) {
                    log.info("Inferred format from content type: fileId={}, contentType={}, format={}", id, contentType,
                            format);
                }
            }

            // Final fallback: use "bin" for raw files if format is still missing
            if (format == null || format.isEmpty()) {
                format = "bin";
                log.warn(
                        "Format is missing and could not be inferred, using default 'bin': fileId={}, filename={}, contentType={}",
                        id, file.getFilename(), file.getContentType());
            }
        }

        // Generate signed URL with expiration, passing resourceType and format
        // Cast to CloudinaryStorageService to use the overloaded method with format
        String signedUrl;
        try {
            // Use the format we inferred/retrieved to avoid re-fetching resource details
            if (storageService instanceof github.vijay_papanaboina.cloud_storage_api.service.storage.CloudinaryStorageService) {
                github.vijay_papanaboina.cloud_storage_api.service.storage.CloudinaryStorageService cloudinaryService = (github.vijay_papanaboina.cloud_storage_api.service.storage.CloudinaryStorageService) storageService;
                signedUrl = cloudinaryService.generateSignedDownloadUrl(fullPublicId, expirationMinutes,
                        resourceType, format);
            } else {
                // Fallback to standard method if not CloudinaryStorageService
                signedUrl = storageService.generateSignedDownloadUrl(fullPublicId, expirationMinutes,
                        resourceType);
            }
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
    public FileUrlResponse getSignedDownloadUrlByFilepath(String filepath, UUID userId, int expirationMinutes) {
        log.info("Getting signed download URL by filepath: filepath={}, userId={}, expiresIn={} minutes",
                filepath, userId, expirationMinutes);

        // Parse filepath to extract folder path and filename
        String[] pathParts = parseFilepath(filepath);
        String folderPath = pathParts[0];
        String filename = pathParts[1];

        // Find file by folder path and filename
        Optional<File> fileOpt = fileRepository.findByUserIdAndFolderPathAndFilenameAndDeletedFalse(
                userId, folderPath, filename);

        if (fileOpt.isEmpty()) {
            throw new ResourceNotFoundException(
                    String.format("File not found: %s", filepath));
        }

        File file = fileOpt.get();

        // Use existing getSignedDownloadUrl logic
        return getSignedDownloadUrl(file.getId(), userId, expirationMinutes);
    }

    @Override
    @Transactional(readOnly = true)
    public FileResponse getById(UUID id, UUID userId) {
        log.info("Getting file by ID: fileId={}, userId={}", id, userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Validate file access (throws CloudFileNotFoundException or
        // AccessDeniedException)
        File file = validateFileAccess(id, userId);

        return toFileResponse(file);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FileResponse> list(Pageable pageable, Optional<String> contentType, Optional<String> folderPath,
            UUID userId) {
        // Normalize optional parameters
        Optional<String> normalizedContentType = normalizeOptionalString(contentType);
        Optional<String> normalizedFolderPath = normalizeOptionalString(folderPath);

        // Validate folder path if provided
        if (normalizedFolderPath.isPresent()) {
            validateFolderPath(normalizedFolderPath.get());
        }

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

        // Determine the target folder path (new if changed, otherwise current)
        String targetFolderPath = folderPathChanged ? newFolderPath : oldFolderPath;

        // Update filename if provided
        UUID fileId = file.getId(); // Get file ID to exclude from uniqueness check
        if (request.getFilename() != null && !request.getFilename().isBlank()) {
            // Generate unique filename if duplicate exists (auto-renaming)
            // Exclude current file ID to avoid self-collision
            String requestedFilename = request.getFilename();
            String uniqueFilename = generateUniqueFilename(targetFolderPath, requestedFilename, userId, fileId);
            file.setFilename(uniqueFilename);
        } else if (folderPathChanged) {
            // If only folderPath is changing, check if filename needs to be made unique in
            // new folder
            // Exclude current file ID to avoid self-collision
            String currentFilename = file.getFilename();
            String uniqueFilename = generateUniqueFilename(newFolderPath, currentFilename, userId, fileId);
            file.setFilename(uniqueFilename);
        }

        // Update folderPath and move file in Cloudinary if folderPath changed
        if (folderPathChanged) {
            // Validate new folder path
            validateFolderPath(newFolderPath);
            try {
                // Construct current full Cloudinary public ID with userId prefix
                String currentFullPublicId = constructCloudinaryPublicId(userId, oldFolderPath,
                        file.getCloudinaryPublicId());

                // Construct new Cloudinary folder path with userId prefix
                String newCloudinaryFolderPath = constructCloudinaryFolderPath(userId, newFolderPath);

                // Move file in Cloudinary to new folder
                // moveFile will try different resource types automatically for authenticated
                // resources
                Map<String, Object> moveResult = storageService.moveFile(currentFullPublicId, newCloudinaryFolderPath,
                        null);

                // Validate moveResult and newFullPublicId
                if (moveResult == null) {
                    log.error("Move result is null after moving file: fileId={}, userId={}, oldFolder={}, newFolder={}",
                            id, userId, oldFolderPath, newFolderPath);
                    throw new StorageException("Move operation returned null result");
                }

                String newFullPublicId = (String) moveResult.get("public_id");
                if (newFullPublicId == null || newFullPublicId.trim().isEmpty()) {
                    log.error("Move result missing public_id: fileId={}, userId={}, oldFolder={}, newFolder={}",
                            id, userId, oldFolderPath, newFolderPath);
                    throw new StorageException("Move operation did not return a valid public_id");
                }

                // For authenticated resources, URLs may not be in moveResult
                // Extract URLs from moveResult if present, otherwise they will be null
                String newCloudinaryUrl = (String) moveResult.get("url");
                String newCloudinarySecureUrl = (String) moveResult.get("secure_url");

                // Regenerate URLs independently - only replace null URLs
                // Do not fall back to old URLs as they may be invalid after move
                if (newCloudinaryUrl == null) {
                    try {
                        newCloudinaryUrl = storageService.getFileUrl(newFullPublicId, false);
                        log.debug("Regenerated HTTP URL after move: fileId={}, newPublicId={}", id, newFullPublicId);
                    } catch (Exception e) {
                        log.warn(
                                "Could not generate HTTP URL after move, setting to null: fileId={}, newPublicId={}, error={}",
                                id, newFullPublicId, e.getMessage());
                        newCloudinaryUrl = null; // Set to null rather than using old URL
                    }
                }

                if (newCloudinarySecureUrl == null) {
                    try {
                        newCloudinarySecureUrl = storageService.getFileUrl(newFullPublicId, true);
                        log.debug("Regenerated HTTPS URL after move: fileId={}, newPublicId={}", id, newFullPublicId);
                    } catch (Exception e) {
                        log.warn(
                                "Could not generate HTTPS URL after move, setting to null: fileId={}, newPublicId={}, error={}",
                                id, newFullPublicId, e.getMessage());
                        newCloudinarySecureUrl = null; // Set to null rather than using old URL
                    }
                }

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

        // Validate folder path if provided
        if (normalizedFolderPath.isPresent()) {
            validateFolderPath(normalizedFolderPath.get());
        }

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

        // Validate file access (user-scoped)
        File file = validateFileAccess(id, userId);

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
        // Use the known resource type to avoid guessing and potential 404s
        String url;
        try {
            if (resourceType != null && !resourceType.isEmpty() && !resourceType.equals("auto")) {
                // Use the known resource type for accurate URL generation
                url = storageService.getFileUrl(fullPublicId, secure, resourceType);
            } else {
                // Fallback to guessing if resource type is unknown or "auto"
                log.warn("Resource type is unknown or 'auto', using fallback URL generation: fileId={}, publicId={}",
                        id, file.getCloudinaryPublicId());
                url = storageService.getFileUrl(fullPublicId, secure);
            }
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

        // Validate file access (user-scoped)
        File file = validateFileAccess(id, userId);

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
        // Validate userId is not null
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Validate files array is not null or empty
        if (files == null || files.length == 0) {
            throw new BadRequestException("No files provided. At least one file is required.");
        }

        // Validate maximum file count (100)
        if (files.length > 100) {
            throw new BadRequestException(
                    String.format("Maximum 100 files allowed per batch. Provided: %d files", files.length));
        }

        // File size limits (independent constraints: 100MB per file, 100MB total)
        // Note: Both limits must be satisfied. The per-file limit applies to each file
        // individually, while the total limit applies to the sum of all file sizes.
        final long MAX_FILE_SIZE = 100L * 1024 * 1024; // 100MB in bytes (per-file limit)
        final long MAX_TOTAL_SIZE = 100L * 1024 * 1024; // 100MB in bytes (total payload limit)

        // Validate per-file size limits and calculate total size
        long totalSize = 0;
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            if (file == null) {
                throw new BadRequestException(String.format("File at index %d is null", i));
            }

            long fileSize = file.getSize();
            if (fileSize < 0) {
                throw new BadRequestException(
                        String.format("File at index %d has invalid size: %d bytes", i, fileSize));
            }

            // Validate per-file size limit (independent constraint)
            if (fileSize > MAX_FILE_SIZE) {
                throw new BadRequestException(String.format(
                        "File '%s' at index %d exceeds the per-file size limit of 100MB. File size: %.2f MB. "
                                + "The per-file limit is independent of the total payload limit.",
                        file.getOriginalFilename(), i, fileSize / (1024.0 * 1024.0)));
            }

            totalSize += fileSize;
        }

        // Validate total payload size limit (independent constraint)
        if (totalSize > MAX_TOTAL_SIZE) {
            throw new BadRequestException(String.format(
                    "Total payload size (%.2f MB) exceeds the total payload limit of 100MB. Total size: %d bytes. "
                            + "The combined size of all files must not exceed 100MB, regardless of individual file sizes. "
                            + "The total payload limit is independent of the per-file limit.",
                    totalSize / (1024.0 * 1024.0), totalSize));
        }

        // Note: Duplicate filenames within the batch are allowed and will be handled
        // during
        // asynchronous processing. Each file will be automatically renamed with a
        // unique suffix
        // (e.g., "file-1.pdf", "file-2.pdf") if a filename already exists in the target
        // folder.
        // This is handled by the generateUniqueFilename method in the upload processing
        // logic.

        // Normalize folderPath
        String normalizedFolderPath = optionalToString(normalizeOptionalString(folderPath));
        // Validate folder path format
        validateFolderPath(normalizedFolderPath);
        log.info("Bulk uploading files: userId={}, count={}, totalSize={} bytes, folderPath={}", userId,
                files.length, totalSize, normalizedFolderPath);

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
    // Package-private for testing
    Optional<String> normalizeOptionalString(Optional<String> value) {
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
    // Package-private for testing
    String constructCloudinaryFolderPath(UUID userId, String userFolderPath) {
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
    // Package-private for testing
    String constructCloudinaryPublicId(UUID userId, String userFolderPath, String uuid) {
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
    // Package-private for testing
    String extractUuidFromPublicId(String fullPublicId) {
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
    // Package-private for testing
    String sanitizeFilename(String filename) {
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
    // Package-private for testing
    String getFileExtension(String filename) {
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
     * Escape SQL LIKE wildcards (% and _) in a string for safe use in LIKE queries.
     * Replaces '%' with '\%' and '_' with '\_' so they are treated as literal
     * characters.
     * 
     * @param input The input string that may contain wildcards
     * @return The escaped string safe for use in LIKE queries with ESCAPE clause
     */
    // Package-private for testing
    String escapeLikeWildcards(String input) {
        if (input == null) {
            return null;
        }
        // Escape backslash first, then escape wildcards
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * Format file size in human-readable format
     */
    // Package-private for testing
    String formatFileSize(long bytes) {
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
    // Package-private for testing
    void validateFolderPath(String folderPath) {
        if (folderPath != null && !folderPath.isEmpty()) {
            // Use SafeFolderPathValidator to prevent path traversal attacks
            SafeFolderPathValidator.validatePath(folderPath);

            // Additional length check
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
    // Package-private for testing
    void validateFileTypeForTransformation(String contentType, String operation) {
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
            throw new BadRequestException(
                    String.format("File type does not support %s: %s", operation, contentType));
        }
    }

    /**
     * Parse filepath into folder path and filename.
     * 
     * @param filepath The full filepath (e.g., "/photos/2024/image.jpg" or
     *                 "document.pdf")
     * @return Array with [folderPath, filename] where folderPath can be null for
     *         root folder
     * @throws BadRequestException if filepath is invalid
     */
    // Package-private for testing
    String[] parseFilepath(String filepath) {
        if (filepath == null || filepath.trim().isEmpty()) {
            throw new BadRequestException("Filepath cannot be null or empty");
        }

        String normalizedPath = filepath.trim();

        // Remove leading slash if present
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }

        // Split into folder path and filename
        int lastSlashIndex = normalizedPath.lastIndexOf('/');
        String folderPath;
        String filename;

        if (lastSlashIndex == -1) {
            // No folder path, file is in root
            folderPath = null;
            filename = normalizedPath;
        } else {
            // Extract folder path and filename
            folderPath = "/" + normalizedPath.substring(0, lastSlashIndex);
            filename = normalizedPath.substring(lastSlashIndex + 1);
        }

        // Validate folder path format
        if (folderPath != null) {
            validateFolderPath(folderPath);
        }

        // Validate filename
        if (filename == null || filename.trim().isEmpty()) {
            throw new BadRequestException("Filename cannot be empty in filepath");
        }

        return new String[] { folderPath, filename };
    }

    /**
     * Generate a unique filename by appending -1, -2, etc. if the filename already
     * exists in the folder.
     * 
     * @param folderPath    The folder path (null for root folder)
     * @param filename      The desired filename
     * @param userId        The user ID
     * @param excludeFileId Optional file ID to exclude from uniqueness check (used
     *                      during updates to avoid self-collision)
     * @return Unique filename that doesn't exist in the folder
     */
    private String generateUniqueFilename(String folderPath, String filename, UUID userId, UUID excludeFileId) {
        // Check if filename already exists (excluding the specified file ID if
        // provided)
        Optional<File> existingFile;
        if (excludeFileId != null) {
            existingFile = fileRepository.findByUserIdAndFolderPathAndFilenameAndDeletedFalseExcludingFileId(
                    userId, folderPath, filename, excludeFileId);
        } else {
            existingFile = fileRepository.findByUserIdAndFolderPathAndFilenameAndDeletedFalse(
                    userId, folderPath, filename);
        }

        if (existingFile.isEmpty()) {
            // Filename is available, return as-is
            return filename;
        }

        // Filename exists, need to generate unique name
        // Extract base name and extension
        int lastDotIndex = filename.lastIndexOf('.');
        String baseName;
        String extension;

        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            // Has extension
            baseName = filename.substring(0, lastDotIndex);
            extension = filename.substring(lastDotIndex);
        } else {
            // No extension
            baseName = filename;
            extension = "";
        }

        // Try incrementing numbers until we find an available name
        int counter = 1;
        String newFilename;
        do {
            newFilename = baseName + "-" + counter + extension;
            if (excludeFileId != null) {
                existingFile = fileRepository.findByUserIdAndFolderPathAndFilenameAndDeletedFalseExcludingFileId(
                        userId, folderPath, newFilename, excludeFileId);
            } else {
                existingFile = fileRepository.findByUserIdAndFolderPathAndFilenameAndDeletedFalse(
                        userId, folderPath, newFilename);
            }
            counter++;

            // Safety check to prevent infinite loop
            if (counter > 10000) {
                log.warn("Reached maximum attempts for generating unique filename: folderPath={}, baseName={}",
                        folderPath, baseName);
                // Fallback: append UUID for better uniqueness in high-concurrency scenarios
                newFilename = baseName + "-" + UUID.randomUUID().toString().substring(0, 8) + extension;
                break;
            }
        } while (existingFile.isPresent());

        log.info("Generated unique filename: original={}, unique={}, folderPath={}, excludeFileId={}",
                filename, newFilename, folderPath, excludeFileId);

        return newFilename;
    }

    /**
     * Generate a unique filename by appending -1, -2, etc. if the filename already
     * exists in the folder. Convenience method for uploads (no file to exclude).
     * 
     * @param folderPath The folder path (null for root folder)
     * @param filename   The desired filename
     * @param userId     The user ID
     * @return Unique filename that doesn't exist in the folder
     */
    private String generateUniqueFilename(String folderPath, String filename, UUID userId) {
        return generateUniqueFilename(folderPath, filename, userId, null);
    }

    /**
     * Validates that a file exists and belongs to the specified user.
     * Throws appropriate exceptions based on the validation result.
     * 
     * @param id     The file ID
     * @param userId The user ID
     * @return The file if it exists and belongs to the user
     * @throws CloudFileNotFoundException if the file does not exist
     * @throws AccessDeniedException      if the file exists but does not belong to
     *                                    the user
     *                                    (business-level denial)
     */
    private File validateFileAccess(UUID id, UUID userId) {
        Optional<File> fileOpt = fileRepository.findById(id);

        if (fileOpt.isEmpty()) {
            throw new CloudFileNotFoundException(id);
        }

        File file = fileOpt.get();

        // Check if file is deleted
        if (Boolean.TRUE.equals(file.getDeleted())) {
            throw new CloudFileNotFoundException(id);
        }

        // Check if file belongs to the user
        if (!file.getUser().getId().equals(userId)) {
            log.warn("Access denied: fileId={} does not belong to userId={}", id, userId);
            throw new AccessDeniedException("Access denied: You do not have permission to access this file");
        }

        return file;
    }
}
