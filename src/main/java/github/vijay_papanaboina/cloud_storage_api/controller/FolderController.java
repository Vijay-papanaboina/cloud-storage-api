package github.vijay_papanaboina.cloud_storage_api.controller;

import github.vijay_papanaboina.cloud_storage_api.dto.FolderCreateRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.FolderPathValidationRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.FolderResponse;
import github.vijay_papanaboina.cloud_storage_api.dto.FolderStatisticsResponse;
import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import github.vijay_papanaboina.cloud_storage_api.exception.ConflictException;
import github.vijay_papanaboina.cloud_storage_api.security.SecurityUtils;
import github.vijay_papanaboina.cloud_storage_api.service.FolderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for folder management operations.
 * Handles folder creation, listing, deletion, and statistics.
 * Note: Folders are virtual - they exist when files have the matching
 * folder_path.
 */
@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;

    @Autowired
    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    /**
     * Create folder endpoint.
     * Validates the folder path and returns folder information.
     * Since folders are virtual, the folder will "exist" once files are uploaded to
     * that path.
     *
     * @param request Folder creation request with path and optional description
     * @return FolderResponse with folder information (fileCount will be 0 for new
     *         folders)
     */
    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(@Valid @RequestBody FolderCreateRequest request) {
        SecurityUtils.requirePermission("ROLE_WRITE");
        UUID userId = SecurityUtils.getAuthenticatedUserId();

        // Validate the folder path
        FolderPathValidationRequest validationRequest = new FolderPathValidationRequest();
        validationRequest.setPath(request.getPath());
        validationRequest.setDescription(request.getDescription());

        var validationResult = folderService.validateFolderPath(validationRequest, userId);

        if (!validationResult.isValid()) {
            throw new BadRequestException(
                    "Invalid folder path: " + validationResult.getMessage());
        }

        if (validationResult.isExists()) {
            throw new ConflictException(
                    "Folder already exists: " + request.getPath());
        }

        // Return folder response (virtual folder, fileCount is 0 for new folders)
        FolderResponse response = new FolderResponse();
        response.setPath(request.getPath());
        response.setDescription(request.getDescription());
        response.setFileCount(0);
        response.setCreatedAt(Instant.now());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List folders endpoint.
     * List all folders for the authenticated user.
     *
     * @param parentPath Optional parent path filter
     * @return List of FolderResponse
     */
    @GetMapping
    public ResponseEntity<List<FolderResponse>> listFolders(
            @RequestParam(required = false) String parentPath) {
        SecurityUtils.requirePermission("ROLE_READ");
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        Optional<String> parentPathOpt = Optional.ofNullable(parentPath);
        List<FolderResponse> response = folderService.listFolders(parentPathOpt, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get folder statistics endpoint.
     * Get folder statistics including file count, total size, and breakdowns.
     *
     * @param path Folder path (query parameter, automatically URL decoded by
     *             Spring)
     * @return FolderStatisticsResponse with folder statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<FolderStatisticsResponse> getFolderStatistics(
            @RequestParam(required = true) String path) {
        SecurityUtils.requirePermission("ROLE_READ");
        UUID userId = SecurityUtils.getAuthenticatedUserId();

        // Validate path parameter
        if (path == null || path.trim().isEmpty()) {
            throw new BadRequestException("Folder path parameter is required and cannot be empty");
        }

        // Spring automatically URL decodes query parameters, so no manual decoding
        // needed
        // Validate the decoded path is not malformed (basic check)
        String decodedPath = path.trim();

        FolderStatisticsResponse response = folderService.getFolderStatistics(decodedPath, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete folder endpoint.
     * Delete a folder (must be empty).
     *
     * @param path Folder path (query parameter, automatically URL decoded by
     *             Spring)
     * @return 204 No Content
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteFolder(@RequestParam(required = true) String path) {
        SecurityUtils.requirePermission("ROLE_DELETE");
        UUID userId = SecurityUtils.getAuthenticatedUserId();

        // Validate path parameter
        if (path == null || path.trim().isEmpty()) {
            throw new BadRequestException("Folder path parameter is required and cannot be empty");
        }

        // Spring automatically URL decodes query parameters, so no manual decoding
        // needed
        String decodedPath = path.trim();

        folderService.deleteFolder(decodedPath, userId);
        return ResponseEntity.noContent().build();
    }
}
