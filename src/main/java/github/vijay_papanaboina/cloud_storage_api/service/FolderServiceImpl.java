package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.FolderPathValidationRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.FolderPathValidationResult;
import github.vijay_papanaboina.cloud_storage_api.dto.FolderResponse;
import github.vijay_papanaboina.cloud_storage_api.dto.FolderStatisticsResponse;
import github.vijay_papanaboina.cloud_storage_api.exception.*;
import github.vijay_papanaboina.cloud_storage_api.repository.FileRepository;
import github.vijay_papanaboina.cloud_storage_api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FolderServiceImpl implements FolderService {
    private static final Logger log = LoggerFactory.getLogger(FolderServiceImpl.class);

    private final FileRepository fileRepository;
    private final UserRepository userRepository;

    @Autowired
    public FolderServiceImpl(FileRepository fileRepository, UserRepository userRepository) {
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
    }

    /**
     * Validate a folder path.
     * <p>
     * Note: This is a validation-only operation. Folders are virtual and exist only
     * when
     * files have the matching folder_path. This method validates the path format
     * and
     * checks if the path is already in use. Conflicts are represented in the
     * validation
     * result rather than thrown as exceptions. No database writes occur (read-only
     * transaction).
     */
    @Override
    @Transactional(readOnly = true)
    public FolderPathValidationResult validateFolderPath(FolderPathValidationRequest request, UUID userId) {
        log.info("Validating folder path: path={}, userId={}", request.getPath(), userId);

        // Validate userId
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Validate user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId, userId));

        // Validate and normalize folder path
        String normalizedPath = normalizeFolderPath(request.getPath());
        try {
            validateFolderPath(normalizedPath);
        } catch (BadRequestException e) {
            // Path format is invalid - return validation result with isValid=false
            FolderPathValidationResult result = new FolderPathValidationResult();
            result.setPath(normalizedPath);
            result.setValid(false);
            result.setExists(false);
            result.setMessage(e.getMessage());
            result.setFileCount(null);
            log.warn("Folder path validation failed: path={}, reason={}", normalizedPath, e.getMessage());
            return result;
        }

        // Check if folder already exists (has files)
        long fileCount = fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, normalizedPath);

        FolderPathValidationResult result = new FolderPathValidationResult();
        result.setPath(normalizedPath);

        if (fileCount > 0) {
            // Path exists and has files - conflict, but return as validation result
            result.setValid(true); // Path format is valid
            result.setExists(true);
            result.setMessage("Folder path already exists and contains " + fileCount + " file(s)");
            result.setFileCount(fileCount);
            log.info("Folder path validation: path exists with files: path={}, fileCount={}, userId={}",
                    normalizedPath, fileCount, userId);
        } else {
            // Path is valid and available
            result.setValid(true);
            result.setExists(false);
            result.setMessage("Folder path is valid and available for use");
            result.setFileCount(0L);
            log.info("Folder path validated successfully: path={}, userId={}", normalizedPath, userId);
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderResponse> listFolders(Optional<String> parentPath, UUID userId) {
        log.info("Listing folders: parentPath={}, userId={}", parentPath.orElse(null), userId);

        // Validate userId
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Validate user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId, userId));

        // Get all distinct folder paths for the user
        List<String> folderPaths = fileRepository.findDistinctFolderPathsByUserIdAndDeletedFalse(userId);

        // Filter by parent path if provided
        if (parentPath.isPresent()) {
            String normalizedParentPath = normalizeFolderPath(parentPath.get());
            String parentPrefix = normalizedParentPath.endsWith("/")
                    ? normalizedParentPath
                    : normalizedParentPath + "/";

            folderPaths = folderPaths.stream()
                    .filter(path -> path.startsWith(parentPrefix) && !path.equals(normalizedParentPath))
                    .filter(path -> {
                        // Only include direct children (not nested deeper)
                        String relativePath = path.substring(parentPrefix.length());
                        return !relativePath.contains("/");
                    })
                    .collect(Collectors.toList());
        }

        // Build folder responses with file counts and creation dates
        List<FolderResponse> folders = new ArrayList<>();
        for (String path : folderPaths) {
            long fileCount = fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, path);

            // Get creation date from earliest file in folder
            Instant createdAt = getFolderCreationDate(userId, path);

            FolderResponse folder = new FolderResponse();
            folder.setPath(path);
            folder.setDescription(null); // Description not stored, would need separate table
            folder.setFileCount(fileCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) fileCount);
            folder.setCreatedAt(createdAt);

            folders.add(folder);
        }
        log.info("Listed {} folders for user: userId={}", folders.size(), userId);
        return folders;
    }

    @Override
    @Transactional
    public void deleteFolder(String path, UUID userId) {
        log.info("Deleting folder: path={}, userId={}", path, userId);

        // Validate userId
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Validate user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId, userId));

        // Validate and normalize folder path
        String normalizedPath = normalizeFolderPath(path);
        validateFolderPath(normalizedPath);

        // Check if folder exists (by checking if any files have this folder path)
        long fileCount = fileRepository.countByUserIdAndFolderPathAndDeletedFalse(userId, normalizedPath);

        // Check if folder is non-empty
        if (fileCount > 0) {
            log.warn("Cannot delete non-empty folder: path={}, userId={}, fileCount={}",
                    normalizedPath, userId, fileCount);
            throw new BadRequestException("Cannot delete non-empty folder");
        }

        // Folder is virtual, so deletion means ensuring no files have this path
        // If fileCount == 0, the folder is already effectively deleted (no files with
        // this path)
        log.info("Folder deleted successfully: path={}, userId={}", normalizedPath, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public FolderStatisticsResponse getFolderStatistics(String path, UUID userId) {
        log.info("Getting folder statistics: path={}, userId={}", path, userId);

        // Validate userId
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Validate user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId, userId));

        // Validate and normalize folder path
        String normalizedPath = normalizeFolderPath(path);
        validateFolderPath(normalizedPath);

        // Note: Empty folders (fileCount == 0) are allowed - they may have been created
        // via createFolder
        // and will return statistics with 0 files, 0 size, etc.

        // Get folder statistics from repository
        Map<String, Object> stats = fileRepository.getFolderStatisticsByUserIdAndFolderPath(userId, normalizedPath);

        // Extract values from map
        Long totalFiles = stats.get("file_count") != null
                ? ((Number) stats.get("file_count")).longValue()
                : 0L;
        Long totalSize = stats.get("total_size") != null
                ? ((Number) stats.get("total_size")).longValue()
                : 0L;

        // Calculate average file size
        long averageFileSize = totalFiles > 0 ? totalSize / totalFiles : 0;

        // Get content type counts
        List<Object[]> contentTypeCounts = fileRepository.getFolderContentTypeCountsByUserIdAndFolderPath(
                userId, normalizedPath);
        Map<String, Long> byContentType = new HashMap<>();
        for (Object[] row : contentTypeCounts) {
            String contentType = (String) row[0];
            Long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            byContentType.put(contentType, count);
        }

        // Get subfolder counts (folders within this folder)
        List<String> allFolders = fileRepository.findDistinctFolderPathsByUserIdAndDeletedFalse(userId);
        String folderPrefix = normalizedPath.endsWith("/")
                ? normalizedPath
                : normalizedPath + "/";

        Map<String, Long> byFolder = new HashMap<>();
        for (String folderPath : allFolders) {
            if (folderPath.startsWith(folderPrefix) && !folderPath.equals(normalizedPath)) {
                // Get direct subfolder name
                String relativePath = folderPath.substring(folderPrefix.length());
                int nextSlash = relativePath.indexOf('/');
                String subfolderName = nextSlash > 0
                        ? folderPrefix + relativePath.substring(0, nextSlash)
                        : folderPath;

                // Count files in subfolder
                long subfolderFileCount = fileRepository.countByUserIdAndFolderPathAndDeletedFalse(
                        userId, subfolderName);
                byFolder.put(subfolderName, subfolderFileCount);
            }
        }

        // Format storage used
        String storageUsed = formatFileSize(totalSize);

        // Build response
        FolderStatisticsResponse response = new FolderStatisticsResponse(
                normalizedPath,
                totalFiles,
                totalSize,
                averageFileSize,
                storageUsed,
                byContentType,
                byFolder);

        log.info("Folder statistics retrieved: path={}, totalFiles={}, totalSize={}",
                normalizedPath, totalFiles, totalSize);
        return response;
    }

    /**
     * Normalize folder path (remove trailing slash, ensure starts with /)
     */
    // Package-private for testing
    String normalizeFolderPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        // Remove trailing slash (except for root)
        String normalized = path.trim();
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Ensure starts with /
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }

        return normalized;
    }

    /**
     * Validate folder path format
     * 
     * @param folderPath Folder path to validate
     * @throws BadRequestException if folder path is invalid
     */
    // Package-private for testing
    void validateFolderPath(String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new BadRequestException("Folder path is required");
        }

        if (!folderPath.startsWith("/")) {
            throw new BadRequestException("Folder path must start with '/'");
        }

        if (folderPath.length() > 500) {
            throw new BadRequestException("Folder path must not exceed 500 characters");
        }

        // Check for consecutive slashes
        if (folderPath.contains("//")) {
            throw new BadRequestException("Folder path must not contain consecutive slashes");
        }

        // Check for parent directory references
        if (folderPath.contains("../") || folderPath.contains("..")) {
            throw new BadRequestException("Folder path must not contain parent directory references");
        }
    }

    /**
     * Get folder creation date from earliest file in folder
     */
    private Instant getFolderCreationDate(UUID userId, String folderPath) {
        Map<String, Object> stats = fileRepository.getFolderStatisticsByUserIdAndFolderPath(userId, folderPath);
        Object createdAtObj = stats.get("created_at");
        if (createdAtObj instanceof Instant) {
            return (Instant) createdAtObj;
        }
        return Instant.now();
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
}
