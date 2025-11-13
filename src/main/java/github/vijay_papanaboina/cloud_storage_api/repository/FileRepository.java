package github.vijay_papanaboina.cloud_storage_api.repository;

import github.vijay_papanaboina.cloud_storage_api.model.File;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<File, UUID> {

        // ========== FileController Methods ==========

        /**
         * List files for a user with pagination (GET /api/files).
         * Returns only non-deleted files belonging to the user.
         */
        @Query("SELECT f FROM File f WHERE f.user.id = :userId AND f.deleted = false")
        Page<File> findByUserIdAndDeletedFalse(@Param("userId") UUID userId, Pageable pageable);

        /**
         * List files filtered by content type (GET /api/files?contentType=...).
         * Returns only non-deleted files belonging to the user.
         */
        @Query("SELECT f FROM File f WHERE f.user.id = :userId AND f.deleted = false AND f.contentType = :contentType")
        Page<File> findByUserIdAndDeletedFalseAndContentType(
                        @Param("userId") UUID userId,
                        @Param("contentType") String contentType,
                        Pageable pageable);

        /**
         * List files filtered by folder path (GET /api/files?folderPath=...).
         * Returns only non-deleted files belonging to the user.
         */
        @Query("SELECT f FROM File f WHERE f.user.id = :userId AND f.deleted = false AND f.folderPath = :folderPath")
        Page<File> findByUserIdAndDeletedFalseAndFolderPath(
                        @Param("userId") UUID userId,
                        @Param("folderPath") String folderPath,
                        Pageable pageable);

        /**
         * List files filtered by both content type and folder path (GET
         * /api/files?contentType=...&folderPath=...).
         * Returns only non-deleted files belonging to the user.
         */
        @Query("SELECT f FROM File f WHERE f.user.id = :userId AND f.deleted = false AND f.contentType = :contentType AND f.folderPath = :folderPath")
        Page<File> findByUserIdAndDeletedFalseAndContentTypeAndFolderPath(
                        @Param("userId") UUID userId,
                        @Param("contentType") String contentType,
                        @Param("folderPath") String folderPath,
                        Pageable pageable);

        /**
         * Get file by ID and user ID (GET /api/files/{id}).
         * Returns file only if it belongs to the user.
         */
        @Query("SELECT f FROM File f WHERE f.id = :id AND f.user.id = :userId AND f.deleted = false")
        Optional<File> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

        /**
         * Find file by user ID, folder path, and filename (for download by filepath).
         * Returns only non-deleted files belonging to the user.
         * 
         * @param userId     The user ID
         * @param folderPath The folder path (null or empty for root)
         * @param filename   The filename
         * @return Optional File if found
         */
        @Query("SELECT f FROM File f WHERE f.user.id = :userId " +
                        "AND f.deleted = false " +
                        "AND (((:folderPath IS NULL OR :folderPath = '') AND (f.folderPath IS NULL OR f.folderPath = '')) OR f.folderPath = :folderPath) "
                        +
                        "AND f.filename = :filename")
        Optional<File> findByUserIdAndFolderPathAndFilenameAndDeletedFalse(
                        @Param("userId") UUID userId,
                        @Param("folderPath") String folderPath,
                        @Param("filename") String filename);

        /**
         * Find file by user ID, folder path, and filename, excluding a specific file
         * ID.
         * Used during updates to check for filename conflicts while excluding the
         * current file.
         * Returns only non-deleted files belonging to the user.
         * 
         * @param userId        The user ID
         * @param folderPath    The folder path (null or empty for root)
         * @param filename      The filename
         * @param excludeFileId The file ID to exclude from the search (typically the
         *                      current file being updated)
         * @return Optional File if found
         */
        @Query("SELECT f FROM File f WHERE f.user.id = :userId " +
                        "AND f.deleted = false " +
                        "AND (((:folderPath IS NULL OR :folderPath = '') AND (f.folderPath IS NULL OR f.folderPath = '')) OR f.folderPath = :folderPath) "
                        +
                        "AND f.filename = :filename " +
                        "AND f.id != :excludeFileId")
        Optional<File> findByUserIdAndFolderPathAndFilenameAndDeletedFalseExcludingFileId(
                        @Param("userId") UUID userId,
                        @Param("folderPath") String folderPath,
                        @Param("filename") String filename,
                        @Param("excludeFileId") UUID excludeFileId);

        /**
         * Find files by user ID, folder path, and filename starting with pattern (for
         * auto-renaming).
         * Returns only non-deleted files belonging to the user.
         * Used to check if files with similar names exist (e.g., "image.jpg",
         * "image-1.jpg", "image-2.jpg").
         * 
         * <p>
         * <strong>Important:</strong> The filenamePrefix parameter should have SQL LIKE
         * wildcards (% and _) escaped before being passed to this method. Use
         * {@link FileServiceImpl#escapeLikeWildcards(String)} to escape user-supplied
         * input to prevent wildcard injection.
         * 
         * @param userId         The user ID
         * @param folderPath     The folder path (null or empty for root)
         * @param filenamePrefix The filename prefix to match (e.g., "image" or
         *                       "image-"). Must have wildcards escaped if from user
         *                       input.
         * @return List of files matching the pattern
         */
        @Query("SELECT f FROM File f WHERE f.user.id = :userId " +
                        "AND f.deleted = false " +
                        "AND (((:folderPath IS NULL OR :folderPath = '') AND (f.folderPath IS NULL OR f.folderPath = '')) OR f.folderPath = :folderPath) "
                        +
                        "AND f.filename LIKE CONCAT(:filenamePrefix, '%') ESCAPE '\\'")
        List<File> findByUserIdAndFolderPathAndFilenameStartingWithAndDeletedFalse(
                        @Param("userId") UUID userId,
                        @Param("folderPath") String folderPath,
                        @Param("filenamePrefix") String filenamePrefix);

        /**
         * Search files by filename (GET /api/files/search?q=...).
         * Returns only non-deleted files belonging to the user.
         */
        @Query("SELECT f FROM File f WHERE f.user.id = :userId AND f.deleted = false AND LOWER(f.filename) LIKE LOWER(CONCAT('%', :query, '%'))")
        Page<File> findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCase(
                        @Param("userId") UUID userId,
                        @Param("query") String query,
                        Pageable pageable);

        /**
         * Search files by filename with optional filters (GET
         * /api/files/search?q=...&contentType=...&folderPath=...).
         */
        @Query("SELECT f FROM File f WHERE f.user.id = :userId AND f.deleted = false " +
                        "AND LOWER(f.filename) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "AND (:contentType IS NULL OR f.contentType = :contentType) " +
                        "AND (:folderPath IS NULL OR f.folderPath = :folderPath)")
        Page<File> findByUserIdAndDeletedFalseAndFilenameContainingIgnoreCaseWithFilters(
                        @Param("userId") UUID userId,
                        @Param("query") String query,
                        @Param("contentType") String contentType,
                        @Param("folderPath") String folderPath,
                        Pageable pageable);

        /**
         * Count files for a user (for statistics).
         */
        @Query("SELECT COUNT(f) FROM File f WHERE f.user.id = :userId AND f.deleted = false")
        long countByUserIdAndDeletedFalse(@Param("userId") UUID userId);

        /**
         * Get file statistics for a user (GET /api/files/statistics).
         * Returns totalFiles, totalSize, byContentType, byFolder.
         */
        @Query(value = "SELECT " +
                        "COUNT(*) as total_files, " +
                        "COALESCE(SUM(file_size), 0) as total_size, " +
                        "COALESCE(AVG(file_size), 0) as average_file_size " +
                        "FROM files " +
                        "WHERE user_id = :userId AND deleted = false", nativeQuery = true)
        Map<String, Object> getFileStatisticsByUserId(@Param("userId") UUID userId);

        /**
         * Get content type counts for a user (for statistics byContentType).
         */
        @Query(value = "SELECT content_type, COUNT(*) as count " +
                        "FROM files " +
                        "WHERE user_id = :userId AND deleted = false " +
                        "GROUP BY content_type", nativeQuery = true)
        List<Object[]> getContentTypeCountsByUserId(@Param("userId") UUID userId);

        /**
         * Get folder counts for a user (for statistics byFolder).
         */
        @Query(value = "SELECT COALESCE(folder_path, '') as folder_path, COUNT(*) as count " +
                        "FROM files " +
                        "WHERE user_id = :userId AND deleted = false " +
                        "GROUP BY folder_path", nativeQuery = true)
        List<Object[]> getFolderCountsByUserId(@Param("userId") UUID userId);

        /**
         * Find files by IDs and user ID (for batch delete - POST
         * /api/files/batch/delete).
         * Returns only files belonging to the user.
         */
        @Query("SELECT f FROM File f WHERE f.user.id = :userId AND f.id IN :ids AND f.deleted = false")
        List<File> findByUserIdAndIdIn(@Param("userId") UUID userId, @Param("ids") List<UUID> ids);

        // ========== FolderController Methods ==========

        /**
         * Find distinct folder paths for a user (GET /api/folders).
         * Returns only folders containing non-deleted files belonging to the user.
         */
        @Query(value = "SELECT DISTINCT folder_path FROM files " +
                        "WHERE user_id = :userId AND deleted = false AND folder_path IS NOT NULL " +
                        "ORDER BY folder_path", nativeQuery = true)
        List<String> findDistinctFolderPathsByUserIdAndDeletedFalse(@Param("userId") UUID userId);

        /**
         * Count files in a folder for a user (for folder deletion check).
         */
        @Query("SELECT COUNT(f) FROM File f WHERE f.user.id = :userId AND f.folderPath = :folderPath AND f.deleted = false")
        long countByUserIdAndFolderPathAndDeletedFalse(@Param("userId") UUID userId,
                        @Param("folderPath") String folderPath);

        /**
         * Get folder statistics for a user (GET /api/folders/{path}/statistics).
         * Returns file count, total size, creation date.
         */
        @Query(value = "SELECT " +
                        "COUNT(*) as file_count, " +
                        "COALESCE(SUM(file_size), 0) as total_size, " +
                        "MIN(created_at) as created_at " +
                        "FROM files " +
                        "WHERE user_id = :userId AND folder_path = :folderPath AND deleted = false", nativeQuery = true)
        Map<String, Object> getFolderStatisticsByUserIdAndFolderPath(
                        @Param("userId") UUID userId,
                        @Param("folderPath") String folderPath);

        /**
         * Get content type counts for a folder (for folder statistics).
         */
        @Query(value = "SELECT content_type, COUNT(*) as count " +
                        "FROM files " +
                        "WHERE user_id = :userId AND folder_path = :folderPath AND deleted = false " +
                        "GROUP BY content_type", nativeQuery = true)
        List<Object[]> getFolderContentTypeCountsByUserIdAndFolderPath(
                        @Param("userId") UUID userId,
                        @Param("folderPath") String folderPath);
}
