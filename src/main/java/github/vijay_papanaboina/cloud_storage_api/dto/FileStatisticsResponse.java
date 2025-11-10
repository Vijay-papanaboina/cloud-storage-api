package github.vijay_papanaboina.cloud_storage_api.dto;

import java.util.Map;

public class FileStatisticsResponse {
    private final long totalFiles;
    private final long totalSize;
    private final long averageFileSize;
    private final String storageUsed;
    private final Map<String, Long> byContentType;
    private final Map<String, Long> byFolder;

    // Constructors
    public FileStatisticsResponse() {
        this(0, 0, 0, "0 B", Map.of(), Map.of());
    }

    public FileStatisticsResponse(long totalFiles, long totalSize, long averageFileSize, String storageUsed,
            Map<String, Long> byContentType, Map<String, Long> byFolder) {
        if (totalFiles < 0 || totalSize < 0 || averageFileSize < 0) {
            throw new IllegalArgumentException("Statistics values must not be negative");
        }
        this.totalFiles = totalFiles;
        this.totalSize = totalSize;
        this.averageFileSize = averageFileSize;
        if (storageUsed == null) {
            throw new IllegalArgumentException("storageUsed must not be null");
        }
        this.storageUsed = storageUsed;
        this.byContentType = byContentType != null ? validateAndCopyMap(byContentType, "byContentType") : Map.of();
        this.byFolder = byFolder != null ? validateAndCopyMap(byFolder, "byFolder") : Map.of();
    }

    /**
     * Validate that the map contains no null keys or values, then create an
     * immutable copy.
     * 
     * @param map     The map to validate and copy
     * @param mapName The name of the map parameter (for error messages)
     * @return An immutable copy of the map
     * @throws IllegalArgumentException if the map contains null keys or values
     */
    private static Map<String, Long> validateAndCopyMap(Map<String, Long> map, String mapName) {
        if (map.containsKey(null)) {
            throw new IllegalArgumentException(
                    String.format("%s must not contain null keys", mapName));
        }
        if (map.containsValue(null)) {
            throw new IllegalArgumentException(
                    String.format("%s must not contain null values", mapName));
        }
        return Map.copyOf(map);
    }

    // Getters
    public long getTotalFiles() {
        return totalFiles;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public long getAverageFileSize() {
        return averageFileSize;
    }

    public String getStorageUsed() {
        return storageUsed;
    }

    public Map<String, Long> getByContentType() {
        return byContentType;
    }

    public Map<String, Long> getByFolder() {
        return byFolder;
    }
}
