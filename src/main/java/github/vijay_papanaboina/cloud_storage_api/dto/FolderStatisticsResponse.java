package github.vijay_papanaboina.cloud_storage_api.dto;

import java.util.Map;

public class FolderStatisticsResponse {
    private String path;
    private long totalFiles;
    private long totalSize;
    private long averageFileSize;
    private String storageUsed;
    private Map<String, Long> byContentType;
    private Map<String, Long> byFolder;

    // Constructors
    public FolderStatisticsResponse() {
        this.byContentType = Map.of();
        this.byFolder = Map.of();
    }

    public FolderStatisticsResponse(String path, long totalFiles, long totalSize, long averageFileSize,
            String storageUsed, Map<String, Long> byContentType, Map<String, Long> byFolder) {
        if (totalFiles < 0 || totalSize < 0 || averageFileSize < 0) {
            throw new IllegalArgumentException("Statistics values must not be negative");
        }
        this.path = path;
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

    // Getters and Setters
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(long totalFiles) {
        if (totalFiles < 0) {
            throw new IllegalArgumentException("totalFiles must not be negative");
        }
        this.totalFiles = totalFiles;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        if (totalSize < 0) {
            throw new IllegalArgumentException("totalSize must not be negative");
        }
        this.totalSize = totalSize;
    }

    public long getAverageFileSize() {
        return averageFileSize;
    }

    public void setAverageFileSize(long averageFileSize) {
        if (averageFileSize < 0) {
            throw new IllegalArgumentException("averageFileSize must not be negative");
        }
        this.averageFileSize = averageFileSize;
    }

    public String getStorageUsed() {
        return storageUsed;
    }

    public void setStorageUsed(String storageUsed) {
        if (storageUsed == null) {
            throw new IllegalArgumentException("storageUsed must not be null");
        }
        this.storageUsed = storageUsed;
    }

    public Map<String, Long> getByContentType() {
        return byContentType;
    }

    public void setByContentType(Map<String, Long> byContentType) {
        if (byContentType == null) {
            throw new IllegalArgumentException("byContentType must not be null");
        }
        this.byContentType = byContentType != null ? validateAndCopyMap(byContentType, "byContentType") : Map.of();
    }

    public Map<String, Long> getByFolder() {
        return byFolder;
    }

    public void setByFolder(Map<String, Long> byFolder) {
        if (byFolder == null) {
            throw new IllegalArgumentException("byFolder must not be null");
        }
        this.byFolder = byFolder != null ? validateAndCopyMap(byFolder, "byFolder") : Map.of();
    }
}
