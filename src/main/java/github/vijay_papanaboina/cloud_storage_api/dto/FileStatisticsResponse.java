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
        this.totalFiles = totalFiles;
        this.totalSize = totalSize;
        this.averageFileSize = averageFileSize;
        if (storageUsed == null) {
            throw new IllegalArgumentException("storageUsed must not be null");
        }
        this.storageUsed = storageUsed;
        this.byContentType = byContentType != null ? Map.copyOf(byContentType) : Map.of();
        this.byFolder = byFolder != null ? Map.copyOf(byFolder) : Map.of();
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
