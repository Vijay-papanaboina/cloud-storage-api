package github.vijay_papanaboina.cloud_storage_api.service;

import github.vijay_papanaboina.cloud_storage_api.dto.FolderPathValidationRequest;
import github.vijay_papanaboina.cloud_storage_api.dto.FolderPathValidationResult;
import github.vijay_papanaboina.cloud_storage_api.dto.FolderResponse;
import github.vijay_papanaboina.cloud_storage_api.dto.FolderStatisticsResponse;
import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import github.vijay_papanaboina.cloud_storage_api.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderService {
    /**
     * Validate a folder path (user-scoped)
     * <p>
     * Folders are virtual - they exist only when files have the matching
     * folder_path.
     * This method validates the path format and checks if the path is already in
     * use.
     * The folder is not persisted; it will "exist" once files are uploaded with
     * this path.
     * <p>
     * This is a validation-only operation (read-only transaction). No database
     * writes occur.
     * Conflicts (e.g., path already exists) are represented in the validation
     * result rather
     * than thrown as exceptions.
     *
     * @param request Folder path validation request with path and optional
     *                description
     * @param userId  The authenticated user's ID
     * @return FolderPathValidationResult containing validation status, existence
     *         check, message, and file count
     * @throws BadRequestException       if the folder path format is invalid
     *                                   (syntax errors)
     * @throws ResourceNotFoundException if the user is not found
     */
    FolderPathValidationResult validateFolderPath(FolderPathValidationRequest request, UUID userId);

    /**
     * List folders for a user (user-scoped)
     * <p>
     * Returns all distinct folder paths that contain files belonging to the user.
     * Optionally filters by parent path.
     *
     * @param parentPath Optional parent path to filter folders. If provided,
     *                   returns
     *                   only folders that are direct children of the parent path.
     *                   Empty Optional means list all folders.
     * @param userId     The authenticated user's ID
     * @return List of FolderResponse with folder information
     * @throws ResourceNotFoundException if the user is not found
     */
    List<FolderResponse> listFolders(Optional<String> parentPath, UUID userId);

    /**
     * Delete a folder (user-scoped)
     * <p>
     * Deletes a folder only if it is empty (contains no files). Folders are
     * virtual,
     * so deletion means ensuring no files have the matching folder_path.
     *
     * @param path   Folder path to delete
     * @param userId The authenticated user's ID
     * @throws BadRequestException       if the folder path is invalid or folder is
     *                                   not
     *                                   empty
     * @throws ResourceNotFoundException if the folder does not exist or user is not
     *                                   found
     */
    void deleteFolder(String path, UUID userId);

    /**
     * Get folder statistics (user-scoped)
     * <p>
     * Returns statistics for a folder including file count, total size, average
     * file
     * size, and breakdowns by content type and subfolders.
     *
     * @param path   Folder path
     * @param userId The authenticated user's ID
     * @return FolderStatisticsResponse with folder statistics
     * @throws BadRequestException       if the folder path is invalid
     * @throws ResourceNotFoundException if the folder does not exist or user is not
     *                                   found
     */
    FolderStatisticsResponse getFolderStatistics(String path, UUID userId);
}
