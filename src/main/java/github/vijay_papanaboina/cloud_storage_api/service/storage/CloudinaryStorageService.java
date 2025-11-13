package github.vijay_papanaboina.cloud_storage_api.service.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import github.vijay_papanaboina.cloud_storage_api.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import jakarta.annotation.PreDestroy;

@Service
public class CloudinaryStorageService implements StorageService {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryStorageService.class);
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes TTL for resource type cache

    private final Cloudinary cloudinary;
    private final github.vijay_papanaboina.cloud_storage_api.config.CloudinaryConfig cloudinaryConfig;
    // Thread-safe cache for resource type lookups: publicId -> CacheEntry
    private final ConcurrentHashMap<String, CacheEntry> resourceTypeCache = new ConcurrentHashMap<>();
    // Thread-safe metrics tracking for resource type success counts: resourceType
    // -> success count
    private final ConcurrentHashMap<String, Long> resourceTypeSuccessMetrics = new ConcurrentHashMap<>();
    // Dedicated thread pool for offloading blocking download I/O operations
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(10);

    @Autowired
    public CloudinaryStorageService(Cloudinary cloudinary,
            github.vijay_papanaboina.cloud_storage_api.config.CloudinaryConfig cloudinaryConfig) {
        this.cloudinary = cloudinary;
        this.cloudinaryConfig = cloudinaryConfig;
    }

    @PreDestroy
    public void shutdown() {
        downloadExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                downloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            downloadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Cache entry for resource type with timestamp for TTL-based eviction
     */
    private static class CacheEntry {
        final String resourceType;
        final long timestamp;

        CacheEntry(String resourceType, long timestamp) {
            this.resourceType = resourceType;
            this.timestamp = timestamp;
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }

    @Override
    public Map<String, Object> uploadFile(MultipartFile file, String folderPath, Map<String, Object> options) {
        // Validate file
        validateFile(file);

        try {
            // Generate unique public ID (UUID only, no extension)
            String publicId = UUID.randomUUID().toString();

            // Validate original filename (required for Cloudinary upload)
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                throw new IllegalArgumentException("File original filename cannot be null or blank");
            }

            // Prepare upload options using SDK 2.0 ObjectUtils for cleaner code
            Map<String, Object> uploadOptions = new HashMap<>();
            if (options != null) {
                uploadOptions.putAll(options);
            }

            // Build options map with defaults using ObjectUtils
            uploadOptions.put("public_id", publicId);
            uploadOptions.put("use_filename", false);
            uploadOptions.put("unique_filename", false);

            // IMPORTANT: All uploads use type="authenticated" for security.
            // This means:
            // 1. All files require signed URLs for access (use getFileUrl(),
            // generateSignedDownloadUrl(), etc.)
            // 2. Files cannot be accessed via public URLs without signatures
            // 3. This is a security best practice to prevent unauthorized access
            //
            // BREAKING CHANGE: If migrating from public uploads:
            // - Existing publicly-accessible files will continue to work with public URLs
            // - New files uploaded with this service require signed URLs
            // - Update all consuming applications to use signed URL methods
            // - Consider a migration strategy for existing files if needed
            //
            // To override this behavior, pass "type" in the options parameter.
            // However, this is NOT recommended for security reasons.
            if (!uploadOptions.containsKey("type")) {
                uploadOptions.put("type", "authenticated");
            }

            uploadOptions.put("resource_type", uploadOptions.getOrDefault("resource_type", "auto"));

            if (folderPath != null && !folderPath.isEmpty()) {
                uploadOptions.put("folder", folderPath);
            }

            // Note: Format parameter is not set at upload time. Format transformations
            // should be applied via transformation parameters in delivery URLs (e.g., f=
            // for images).
            // For videos requiring format conversion, upload with explicit
            // resource_type='video'
            // instead of 'auto'. The original file format is preserved at upload.

            // Upload to Cloudinary
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), uploadOptions);

            log.info("File uploaded successfully to Cloudinary: publicId={}, size={}",
                    publicId, file.getSize());

            return result;
        } catch (IOException e) {
            log.error("Failed to read file bytes: {}", e.getMessage(), e);
            throw new StorageException("Failed to read file", e);
        } catch (Exception e) {
            log.error("Error during file upload: {}", e.getMessage(), e);
            throw new StorageException("Failed to upload file to Cloudinary", e);
        }
    }

    @Override
    public byte[] downloadFile(String publicId) {
        // Offload blocking I/O to dedicated thread pool to avoid blocking request
        // threads
        // This prevents thread pool exhaustion under concurrent load
        CompletableFuture<byte[]> future = CompletableFuture.supplyAsync(
                () -> downloadFileInternal(publicId),
                downloadExecutor);

        try {
            // Wait for download with timeout (max 2 minutes: 3 types × 40s timeout)
            return future.get(2, TimeUnit.MINUTES);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            log.error("Download timeout for publicId={}", publicId);
            throw new StorageException("Download timeout: file download exceeded maximum time limit", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof StorageException) {
                throw (StorageException) cause;
            }
            log.error("Unexpected error during file download: publicId={}, error={}", publicId, cause.getMessage(),
                    cause);
            throw new StorageException("Failed to download file: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            log.error("Download interrupted for publicId={}", publicId);
            throw new StorageException("Download interrupted", e);
        }
    }

    /**
     * Internal download method that performs the actual blocking I/O.
     * This method is executed on a dedicated thread pool to avoid blocking request
     * threads.
     */
    private byte[] downloadFileInternal(String publicId) {
        try {
            // Try to resolve resource type using cached helper
            String resolvedResourceType = resolveResourceType(publicId);

            // If resolved, try downloading with that type first
            if (resolvedResourceType != null) {
                try {
                    byte[] fileBytes = downloadFileWithResourceType(publicId, resolvedResourceType);
                    // Cache successful resource type
                    resourceTypeCache.put(publicId, new CacheEntry(resolvedResourceType, System.currentTimeMillis()));
                    log.info("File downloaded successfully from Cloudinary: publicId={}, resourceType={}",
                            publicId, resolvedResourceType);
                    return fileBytes;
                } catch (Exception e) {
                    // If download fails with resolved type, fall through to fallback loop
                    log.debug(
                            "Download failed with resolved resourceType={}, falling back to iteration: publicId={}, error={}",
                            resolvedResourceType, publicId, e.getMessage());
                }
            }

            // Fallback: Try common resource types: raw, image, video
            // Note: Order may be suboptimal if most resources are of a different type
            String[] resourceTypes = { "raw", "image", "video" };
            Exception lastException = null;

            for (String resourceType : resourceTypes) {
                try {
                    byte[] fileBytes = downloadFileWithResourceType(publicId, resourceType);
                    // Cache successful resource type for future requests
                    resourceTypeCache.put(publicId, new CacheEntry(resourceType, System.currentTimeMillis()));
                    log.info("File downloaded successfully from Cloudinary: publicId={}, resourceType={}",
                            publicId, resourceType);
                    return fileBytes;
                } catch (java.io.FileNotFoundException e) {
                    // If file not found with this resource type, try next one
                    lastException = e;
                    log.debug("Failed to download with resourceType={}, trying next: publicId={}, error={}",
                            resourceType, publicId, e.getMessage());
                    continue;
                } catch (java.net.HttpRetryException e) {
                    // HTTP retry exception - try next resource type
                    lastException = e;
                    log.debug("HTTP retry error with resourceType={}, trying next: publicId={}, error={}", resourceType,
                            publicId, e.getMessage());
                    continue;
                } catch (java.net.UnknownHostException e) {
                    // Network error - don't try other types, fail immediately
                    log.error("Network error during download: publicId={}, error={}", publicId, e.getMessage());
                    throw new StorageException("Network error during file download: " + e.getMessage(), e);
                } catch (IOException e) {
                    // Check if it's an HTTP error (404, 403, etc.)
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && (errorMsg.contains("404") || errorMsg.contains("403")
                            || errorMsg.contains("Server returned HTTP response code"))) {
                        // HTTP error - try next resource type
                        lastException = e;
                        log.debug("HTTP error with resourceType={}, trying next: publicId={}, error={}", resourceType,
                                publicId, e.getMessage());
                        continue;
                    }
                    // For other IO errors, try next resource type
                    lastException = e;
                    log.debug("IO error with resourceType={}, trying next: publicId={}, error={}", resourceType,
                            publicId, e.getMessage());
                    continue;
                } catch (Exception e) {
                    // Other exceptions - try next resource type
                    lastException = e;
                    log.debug("Error with resourceType={}, trying next: publicId={}, error={}", resourceType,
                            publicId, e.getMessage());
                    continue;
                }
            }

            // If all resource types failed, throw exception
            if (lastException != null) {
                log.error("Failed to download file from Cloudinary with any resource type: publicId={}", publicId);
                throw new StorageException("Failed to download file from Cloudinary: " + lastException.getMessage(),
                        lastException);
            }

            // Should not reach here, but add fallback
            throw new StorageException("Failed to download file from Cloudinary: unable to determine resource type");
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during file download: publicId={}, error={}",
                    publicId, e.getMessage(), e);
            throw new StorageException("Unexpected error during file download", e);
        }
    }

    /**
     * Downloads a file with a specific resource type.
     * This method performs blocking I/O and should be called from a dedicated
     * thread pool.
     */
    private byte[] downloadFileWithResourceType(String publicId, String resourceType) throws IOException {
        com.cloudinary.Url urlBuilder = cloudinary.url()
                .secure(true)
                .signed(true)
                .type("authenticated")
                .resourceType(resourceType);

        String url = urlBuilder.generate(publicId);
        java.net.URI uri = java.net.URI.create(url);
        java.net.URL fileUrl = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) fileUrl.openConnection();
        try {
            // Set reasonable timeouts (10s connect, 30s read)
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP error: " + responseCode);
            }

            try (java.io.InputStream inputStream = connection.getInputStream();
                    java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                return outputStream.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public boolean deleteFile(String publicId) {
        try {
            // Fetch resource details to obtain the actual resource_type
            // This is necessary because destroy() defaults to resource_type="image",
            // which causes failures for non-image assets uploaded with resource_type="auto"
            Map<String, Object> resourceDetails = getResourceDetails(publicId, null);
            String resourceType = (String) resourceDetails.get("resource_type");

            // Handle "auto" or missing resource_type by inferring from format
            if (resourceType == null || resourceType.isEmpty() || resourceType.equals("auto")) {
                String format = (String) resourceDetails.get("format");
                if (format != null && !format.isEmpty()) {
                    resourceType = inferResourceTypeFromFormat(format);
                    log.info("Inferred resource_type from format for deletion: publicId={}, format={}, resourceType={}",
                            publicId, format, resourceType);
                } else {
                    // Try deleting as each type if format is missing
                    log.warn(
                            "Resource type is 'auto' and format is missing, attempting deletion for each type: publicId={}",
                            publicId);
                    String[] resourceTypes = { "image", "video", "raw" };
                    for (String type : resourceTypes) {
                        try {
                            Map<String, Object> destroyOptions = new HashMap<>();
                            destroyOptions.put("resource_type", type);
                            destroyOptions.put("type", "authenticated");
                            @SuppressWarnings("unchecked")
                            Map<String, Object> result = cloudinary.uploader().destroy(publicId, destroyOptions);
                            String resultValue = result.get("result") != null ? result.get("result").toString() : "";
                            if ("ok".equals(resultValue)) {
                                log.info("File deleted successfully by trying resource_type={}: publicId={}", type,
                                        publicId);
                                return true;
                            }
                        } catch (Exception e) {
                            // Continue to next type
                            continue;
                        }
                    }
                    // If all attempts failed, throw exception
                    throw new StorageException(
                            "Cannot determine resource type for deletion: resource_type is 'auto' and format is missing for publicId: "
                                    + publicId);
                }
            }

            // Prepare destroy options with the correct resource_type
            Map<String, Object> destroyOptions = new HashMap<>();
            destroyOptions.put("resource_type", resourceType);
            destroyOptions.put("type", "authenticated"); // Match the upload type

            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, destroyOptions);
            String resultValue = result.get("result") != null ? result.get("result").toString() : "";
            boolean deleted = "ok".equals(resultValue);

            if (deleted) {
                log.info("File deleted successfully from Cloudinary: publicId={}, resourceType={}",
                        publicId, resourceType);
            } else {
                log.warn("File deletion returned unexpected result: publicId={}, resourceType={}, result={}",
                        publicId, resourceType, resultValue);
            }

            return deleted;
        } catch (StorageException e) {
            // If resource not found, return false instead of throwing
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                log.warn("File not found in Cloudinary for deletion: publicId={}", publicId);
                return false;
            }
            // Re-throw StorageException for other cases
            throw e;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                log.warn("File not found in Cloudinary for deletion: publicId={}", publicId);
                return false;
            }
            log.error("Error during file deletion: publicId={}, error={}",
                    publicId, e.getMessage(), e);
            throw new StorageException("Failed to delete file from Cloudinary", e);
        }
    }

    @Override
    public String getFileUrl(String publicId, boolean secure) {
        try {
            // Generate URL without blocking Admin API call for performance
            // Try multiple resource types in order (image, video, raw) and return the first
            // URL generated
            // This avoids blocking network I/O during URL generation, which is expected to
            // be a fast, local operation
            // IMPORTANT: The returned URL is NOT VALIDATED and may be invalid (404) if the
            // guessed resource type is incorrect. Callers MUST handle 404 errors.
            // The order (image, video, raw) may be suboptimal if most resources are raw
            // files.
            // When resource type is known, use getFileUrl(publicId, secure, resourceType)
            // instead.
            String[] resourceTypes = { "image", "video", "raw" };

            for (String type : resourceTypes) {
                try {
                    // Build signed authenticated URL for resources uploaded with
                    // type="authenticated"
                    com.cloudinary.Url urlBuilder = cloudinary.url()
                            .secure(secure)
                            .signed(true)
                            .type("authenticated")
                            .resourceType(type);

                    String url = urlBuilder.generate(publicId);
                    log.debug(
                            "Generated signed URL (unvalidated) for authenticated resource: publicId={}, resourceType={}, secure={}",
                            publicId, type, secure);
                    // Return first URL generated - caller MUST handle 404s if incorrect
                    return url;
                } catch (Exception e) {
                    // If URL generation fails for this type, try next
                    log.debug("Failed to generate URL with resourceType={}, trying next: publicId={}, error={}", type,
                            publicId, e.getMessage());
                    continue;
                }
            }

            // If all resource types failed to generate URL, throw exception
            log.error("Failed to generate URL for any resource type: publicId={}", publicId);
            throw new StorageException("Failed to generate Cloudinary URL: unable to determine resource type");
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate signed Cloudinary URL: publicId={}, error={}",
                    publicId, e.getMessage(), e);
            throw new StorageException("Failed to generate Cloudinary URL", e);
        }
    }

    @Override
    public String getFileUrl(String publicId, boolean secure, String resourceType) {
        try {
            // Validate resource type
            if (resourceType == null || resourceType.trim().isEmpty() || resourceType.equals("auto")) {
                throw new IllegalArgumentException(
                        "Resource type must not be null, empty, or 'auto'. Provided: " + resourceType);
            }

            // Generate URL with known resource type - no guessing, no blocking Admin API
            // calls
            com.cloudinary.Url urlBuilder = cloudinary.url()
                    .secure(secure)
                    .signed(true)
                    .type("authenticated")
                    .resourceType(resourceType);

            String url = urlBuilder.generate(publicId);
            log.debug("Generated signed URL for authenticated resource: publicId={}, resourceType={}, secure={}",
                    publicId, resourceType, secure);
            return url;
        } catch (IllegalArgumentException e) {
            // Re-throw IllegalArgumentException as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate signed Cloudinary URL: publicId={}, resourceType={}, error={}",
                    publicId, resourceType, e.getMessage(), e);
            throw new StorageException("Failed to generate Cloudinary URL", e);
        }
    }

    @Override
    public String generateSignedDownloadUrl(String publicId, int expirationMinutes) {
        return generateSignedDownloadUrl(publicId, expirationMinutes, null);
    }

    @Override
    public String generateSignedDownloadUrl(String publicId, int expirationMinutes, String resourceType) {
        return generateSignedDownloadUrl(publicId, expirationMinutes, resourceType, null);
    }

    /**
     * Generate signed download URL with optional format parameter.
     * If format is provided, it will be used directly. Otherwise, format will be
     * retrieved from Cloudinary resource details.
     * 
     * @param publicId          Cloudinary public ID
     * @param expirationMinutes URL expiration time in minutes
     * @param resourceType      Resource type (image, video, raw)
     * @param format            Optional format. If provided, will be used directly.
     *                          If null, will be retrieved from resource details.
     * @return Signed download URL
     */
    public String generateSignedDownloadUrl(String publicId, int expirationMinutes, String resourceType,
            String format) {
        try {
            // Validate expirationMinutes to prevent overflow
            if (expirationMinutes < 0) {
                throw new IllegalArgumentException("expirationMinutes must be non-negative");
            }
            // Check for potential overflow: expirationMinutes * 60 should fit in long
            long expirationSeconds = (long) expirationMinutes * 60L;
            if (expirationSeconds > Long.MAX_VALUE / 1000L) {
                throw new IllegalArgumentException(
                        "expirationMinutes too large: " + expirationMinutes + " would cause overflow");
            }

            // If format is not provided, get resource details to extract format
            if (format == null || format.isEmpty()) {
                Map<String, Object> resourceDetails = getResourceDetails(publicId, resourceType);
                format = (String) resourceDetails.get("format");
            }

            // Calculate expiration time in seconds (UNIX timestamp)
            long expirationTime = System.currentTimeMillis() / 1000L + expirationSeconds;

            String signedUrl;

            // For raw files, use Admin API to generate download URL with proper resource
            // type
            // privateDownload defaults to image endpoint which doesn't work for raw files
            if ("raw".equals(resourceType)) {
                // For raw files, use "bin" as default format if format is missing
                // This is a safe default for binary/raw files
                String downloadFormat = (format != null && !format.isEmpty()) ? format : "bin";

                // For raw files, construct download URL manually with correct resource type
                // privateDownload defaults to /image/download which doesn't work for raw files
                // We need to use /raw/download endpoint with proper signing
                try {
                    String cloudName = cloudinaryConfig.getCloudName();
                    String apiKey = cloudinaryConfig.getApiKey();
                    String apiSecret = cloudinaryConfig.getApiSecret();
                    if (cloudName == null || cloudName.isEmpty() ||
                            apiKey == null || apiKey.isEmpty() ||
                            apiSecret == null || apiSecret.isEmpty()) {
                        throw new StorageException(
                                "Cloudinary configuration is incomplete: cloudName, apiKey, and apiSecret are required");
                    }

                    // Build parameters for signature generation (must be sorted alphabetically)
                    // timestamp: current UNIX time in seconds (used for signature generation)
                    // expires_at: future UNIX time (timestamp + validity period)
                    long timestamp = Instant.now().getEpochSecond();
                    long expiresAt = timestamp + expirationSeconds;
                    Map<String, Object> params = new HashMap<>();
                    params.put("api_key", apiKey);
                    params.put("expires_at", expiresAt);
                    params.put("format", downloadFormat);
                    params.put("public_id", publicId);
                    params.put("timestamp", timestamp);

                    // Generate signature using Cloudinary's signing method
                    String signature = cloudinary.apiSignRequest(params, apiSecret);

                    // Construct download URL:
                    // https://api.cloudinary.com/v1_1/{cloud_name}/raw/download
                    String baseUrl = String.format("https://api.cloudinary.com/v1_1/%s/raw/download", cloudName);

                    // Build query string with URL encoding
                    StringBuilder queryString = new StringBuilder();
                    queryString.append("?api_key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
                    queryString.append("&expires_at=").append(expirationTime);
                    queryString.append("&format=").append(URLEncoder.encode(downloadFormat, StandardCharsets.UTF_8));
                    queryString.append("&public_id=").append(URLEncoder.encode(publicId, StandardCharsets.UTF_8));
                    queryString.append("&timestamp=").append(expirationTime);
                    queryString.append("&signature=").append(URLEncoder.encode(signature, StandardCharsets.UTF_8));

                    signedUrl = baseUrl + queryString.toString();

                    log.info("Generated signed download URL for raw file: publicId={}, format={}, expiresAt={}",
                            publicId, downloadFormat, expirationTime);
                } catch (Exception e) {
                    log.error("Failed to generate download URL for raw file: publicId={}, error={}",
                            publicId, e.getMessage(), e);
                    // Fallback: try privateDownload as last resort (may not work correctly)
                    @SuppressWarnings("unchecked")
                    Map<String, Object> expiresOptions = ObjectUtils.asMap("expires_at", expirationTime);
                    signedUrl = cloudinary.privateDownload(publicId, downloadFormat, expiresOptions);
                    log.warn("Fell back to privateDownload for raw file (may not work correctly): publicId={}",
                            publicId);
                }
            } else {
                // For image/video files, use privateDownload which requires format
                if (format == null || format.isEmpty()) {
                    log.error("Resource format is missing for privateDownload: publicId={}, resourceType={}", publicId,
                            resourceType);
                    throw new StorageException(
                            "Cannot generate signed download URL: resource format is missing for publicId: "
                                    + publicId);
                }

                // Generate private download URL with expiration using SDK 2.0 API
                @SuppressWarnings("unchecked")
                Map<String, Object> expiresOptions = ObjectUtils.asMap("expires_at", expirationTime);
                signedUrl = cloudinary.privateDownload(publicId, format, expiresOptions);
            }

            log.info(
                    "Generated signed download URL with enforced expiration: publicId={}, format={}, resourceType={}, expiresIn={} minutes (expires_at={})",
                    publicId, format, resourceType, expirationMinutes, expirationTime);
            return signedUrl;
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to generate signed download URL: publicId={}, expirationMinutes={}, resourceType={}, error={}",
                    publicId, expirationMinutes, resourceType, e.getMessage(), e);
            throw new StorageException("Failed to generate signed download URL", e);
        }
    }

    @Override
    public Map<String, Object> getResourceDetails(String publicId) {
        return getResourceDetails(publicId, null);
    }

    @Override
    public Map<String, Object> getResourceDetails(String publicId, String resourceType) {
        try {
            // If resourceType is provided, use it directly
            if (resourceType != null && !resourceType.isEmpty() && !resourceType.equals("auto")) {
                // Try with type="authenticated" first, then without if that fails
                Map<String, Object> apiOptions = new HashMap<>();
                apiOptions.put("resource_type", resourceType);
                apiOptions.put("type", "authenticated");
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resource = (Map<String, Object>) cloudinary.api().resource(publicId,
                            apiOptions);
                    log.debug(
                            "Resource found with specified resource_type and type=authenticated: publicId={}, resourceType={}",
                            publicId,
                            resourceType);
                    return resource;
                } catch (Exception e) {
                    // If not found with type="authenticated", try without it
                    if (e.getMessage() != null && e.getMessage().contains("not found")) {
                        log.debug("Resource not found with type=authenticated, trying without type: publicId={}",
                                publicId);
                        apiOptions.remove("type");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resource = (Map<String, Object>) cloudinary.api().resource(publicId,
                                apiOptions);
                        log.debug("Resource found without type parameter: publicId={}, resourceType={}", publicId,
                                resourceType);
                        return resource;
                    }
                    throw e;
                }
            }

            // If resourceType is not provided or is "auto", try to resolve using cached
            // helper
            String resolvedResourceType = resolveResourceType(publicId);

            // If resolved, use it to get resource details
            if (resolvedResourceType != null) {
                try {
                    Map<String, Object> apiOptions = new HashMap<>();
                    apiOptions.put("resource_type", resolvedResourceType);
                    apiOptions.put("type", "authenticated");
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resource = (Map<String, Object>) cloudinary.api().resource(publicId,
                                apiOptions);
                        log.debug(
                                "Resource found with resolved resource_type and type=authenticated: publicId={}, resourceType={}",
                                publicId, resolvedResourceType);
                        return resource;
                    } catch (Exception e) {
                        // If not found with type="authenticated", try without it
                        if (e.getMessage() != null && e.getMessage().contains("not found")) {
                            log.debug("Resource not found with type=authenticated, trying without type: publicId={}",
                                    publicId);
                            apiOptions.remove("type");
                            @SuppressWarnings("unchecked")
                            Map<String, Object> resource = (Map<String, Object>) cloudinary.api().resource(publicId,
                                    apiOptions);
                            log.debug("Resource found without type parameter: publicId={}, resourceType={}", publicId,
                                    resolvedResourceType);
                            return resource;
                        }
                        throw e;
                    }
                } catch (Exception e) {
                    // If resolved type fails, fall through to fallback loop
                    log.debug(
                            "Resource details fetch failed with resolved resourceType={}, falling back to iteration: publicId={}, error={}",
                            resolvedResourceType, publicId, e.getMessage());
                }
            }

            // Fallback: Iterate over resource types
            // Try common resource types: image, video, raw (auto is not valid for Admin
            // API)
            String[] resourceTypes = { "image", "video", "raw" };
            Exception lastException = null;

            for (String type : resourceTypes) {
                try {
                    // Try with type="authenticated" first
                    Map<String, Object> apiOptions = new HashMap<>();
                    apiOptions.put("resource_type", type);
                    apiOptions.put("type", "authenticated");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resource = (Map<String, Object>) cloudinary.api().resource(publicId,
                            apiOptions);
                    log.debug(
                            "Resource found by iterating resource types with type=authenticated: publicId={}, resourceType={}",
                            publicId,
                            type);
                    return resource;
                } catch (Exception e) {
                    // If it's a "not found" error, try without type parameter
                    if (e.getMessage() != null && e.getMessage().contains("not found")) {
                        try {
                            Map<String, Object> apiOptions = new HashMap<>();
                            apiOptions.put("resource_type", type);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> resource = (Map<String, Object>) cloudinary.api().resource(publicId,
                                    apiOptions);
                            log.debug(
                                    "Resource found by iterating resource types without type parameter: publicId={}, resourceType={}",
                                    publicId, type);
                            return resource;
                        } catch (Exception e2) {
                            // If still not found, try next resource type
                            if (e2.getMessage() != null && e2.getMessage().contains("not found")) {
                                lastException = e2;
                                continue;
                            }
                            // For other errors, log and rethrow
                            log.error("Error getting resource details with resource_type={}: publicId={}, error={}",
                                    type, publicId, e2.getMessage(), e2);
                            throw new StorageException("Failed to get resource details from Cloudinary", e2);
                        }
                    }
                    // For other errors, log and rethrow
                    log.error("Error getting resource details with resource_type={}: publicId={}, error={}",
                            type, publicId, e.getMessage(), e);
                    throw new StorageException("Failed to get resource details from Cloudinary", e);
                }
            }

            // If we get here, all resource types returned "not found"
            if (lastException != null) {
                log.error("Resource not found in Cloudinary for any resource type: publicId={}", publicId);
                throw new StorageException("Resource not found in Cloudinary: " + publicId, lastException);
            }

            // Fallback (should not reach here)
            log.error("Unexpected error: failed to get resource details: publicId={}", publicId);
            throw new StorageException("Failed to get resource details from Cloudinary: " + publicId);
        } catch (StorageException e) {
            // Re-throw StorageException as-is
            throw e;
        } catch (Exception e) {
            log.error("Error getting resource details: publicId={}, resourceType={}, error={}",
                    publicId, resourceType, e.getMessage(), e);
            throw new StorageException("Failed to get resource details from Cloudinary", e);
        }
    }

    @Override
    public String getTransformUrl(String publicId, boolean secure, Integer width, Integer height,
            String crop, String quality, String format) {
        try {
            @SuppressWarnings("rawtypes")
            Transformation transformation = new Transformation();

            if (width != null) {
                transformation.width(width);
            }
            if (height != null) {
                transformation.height(height);
            }
            if (crop != null && !crop.isEmpty()) {
                transformation.crop(crop);
            }
            if (quality != null && !quality.isEmpty()) {
                transformation.quality(quality);
            }

            com.cloudinary.Url urlBuilder = cloudinary.url()
                    .secure(secure)
                    .transformation(transformation);

            if (format != null && !format.isEmpty()) {
                urlBuilder.format(format);
            }

            String url = urlBuilder.generate(publicId);

            log.info("Generated transformation URL: publicId={}, width={}, height={}, crop={}, quality={}, format={}",
                    publicId, width, height, crop, quality, format);
            return url;
        } catch (Exception e) {
            log.error("Failed to generate transformation URL: publicId={}, error={}",
                    publicId, e.getMessage(), e);
            throw new StorageException("Failed to generate transformation URL", e);
        }
    }

    /**
     * Validate file before upload
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("File is required");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new StorageException("File size exceeds maximum limit of 100MB");
        }

        // Additional validation can be added here (file type, extension, etc.)
    }

    @Override
    public Map<String, Object> moveFile(String currentPublicId, String newFolderPath) {
        return moveFile(currentPublicId, newFolderPath, null);
    }

    /**
     * Move a file to a different folder in Cloudinary by renaming its public ID.
     * <p>
     * <strong>Non-Transactional Operation:</strong> This operation is NOT
     * transactional.
     * If the rename succeeds but downstream operations fail, no automatic rollback
     * is
     * performed. The file will remain in its new location even if subsequent
     * operations
     * fail. Callers should implement their own compensation logic if needed.
     * <p>
     * <strong>Performance Optimizations:</strong>
     * <ul>
     * <li>Checks a thread-safe in-memory cache before attempting API calls to avoid
     * unnecessary network requests</li>
     * <li>Uses metrics-based ordering to try the most-likely resource type first,
     * reducing latency</li>
     * <li>Caches successful resource type resolutions for future operations</li>
     * </ul>
     * <p>
     * <strong>Compensation on Failure:</strong> If downstream operations fail after
     * a
     * successful rename, callers can implement compensation by calling this method
     * again
     * with the new and original public IDs reversed. However, this is not automatic
     * and
     * must be implemented by the caller.
     *
     * @param currentPublicId Current public ID (may include folder path)
     * @param newFolderPath   New folder path (null or empty means root folder)
     * @param resourceType    Optional resource type (image, video, raw). If null or
     *                        "auto", will attempt to resolve from cache or Admin
     *                        API.
     *                        Falls back to trying all resource types in
     *                        metrics-based
     *                        order.
     * @return Map containing the updated Cloudinary response with new public_id and
     *         resource_type
     * @throws StorageException if the file cannot be moved (e.g., file not found,
     *                          rename fails with all resource types)
     */
    @Override
    public Map<String, Object> moveFile(String currentPublicId, String newFolderPath, String resourceType) {
        try {
            // Extract only the filename/UUID segment from currentPublicId (substring after
            // last '/')
            String filename;
            int lastSlashIndex = currentPublicId.lastIndexOf('/');
            if (lastSlashIndex >= 0 && lastSlashIndex < currentPublicId.length() - 1) {
                filename = currentPublicId.substring(lastSlashIndex + 1);
            } else {
                filename = currentPublicId;
            }

            // Construct the new public ID with the new folder path
            String newPublicId;
            if (newFolderPath != null && !newFolderPath.isEmpty()) {
                // Remove leading slash if present and normalize
                String normalizedFolder = newFolderPath.startsWith("/")
                        ? newFolderPath.substring(1)
                        : newFolderPath;
                // Remove trailing slash if present
                normalizedFolder = normalizedFolder.endsWith("/")
                        ? normalizedFolder.substring(0, normalizedFolder.length() - 1)
                        : normalizedFolder;
                newPublicId = normalizedFolder + "/" + filename;
            } else {
                // Move to root folder - just use the filename without folder
                newPublicId = filename;
            }

            // Try to resolve resource type using cached helper if not provided
            String actualResourceType = resourceType;
            if (actualResourceType == null || actualResourceType.isEmpty() || actualResourceType.equals("auto")) {
                actualResourceType = resolveResourceType(currentPublicId);
            }

            // If we have a resolved resource type, try it first
            if (actualResourceType != null && !actualResourceType.isEmpty() && !actualResourceType.equals("auto")) {
                try {
                    Map<String, Object> renameOptions = new HashMap<>();
                    renameOptions.put("resource_type", actualResourceType);
                    renameOptions.put("type", "authenticated");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> renameResult = cloudinary.uploader().rename(currentPublicId, newPublicId,
                            renameOptions);

                    String resultValue = renameResult.get("result") != null ? renameResult.get("result").toString()
                            : "";
                    if ("ok".equals(resultValue)) {
                        // Store successful resource type in cache for future operations
                        String cacheKey = currentPublicId;
                        resourceTypeCache.put(cacheKey, new CacheEntry(actualResourceType, System.currentTimeMillis()));
                        // Also cache the new public ID with the same resource type
                        resourceTypeCache.put(newPublicId,
                                new CacheEntry(actualResourceType, System.currentTimeMillis()));

                        // Emit metric/counter for successful resource type
                        resourceTypeSuccessMetrics.compute(actualResourceType, (k, v) -> (v == null) ? 1L : v + 1L);

                        Map<String, Object> resourceDetails = new HashMap<>();
                        resourceDetails.put("public_id", newPublicId);
                        resourceDetails.put("resource_type", actualResourceType);
                        log.info(
                                "File moved successfully in Cloudinary: currentPublicId={}, newPublicId={}, resourceType={}, successCount={}",
                                currentPublicId, newPublicId, actualResourceType,
                                resourceTypeSuccessMetrics.get(actualResourceType));
                        return resourceDetails;
                    }
                } catch (Exception e) {
                    // If rename fails with resolved type, fall through to fallback loop
                    log.debug(
                            "Rename failed with resolved resourceType={}, falling back to iteration: currentPublicId={}, error={}",
                            actualResourceType, currentPublicId, e.getMessage());
                }
            }

            // Fallback: Try rename operation with different resource types until one
            // succeeds
            // Check cache first - if we have a cached type, try it before the
            // metrics-ordered loop
            String cacheKey = currentPublicId;
            CacheEntry cached = resourceTypeCache.get(cacheKey);
            if (cached != null && !cached.isExpired(CACHE_TTL_MS)) {
                String cachedType = cached.resourceType;
                log.debug("Found cached resource type, trying first: publicId={}, resourceType={}", cacheKey,
                        cachedType);
                try {
                    Map<String, Object> renameOptions = new HashMap<>();
                    renameOptions.put("resource_type", cachedType);
                    renameOptions.put("type", "authenticated");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> renameResult = cloudinary.uploader().rename(currentPublicId, newPublicId,
                            renameOptions);

                    String resultValue = renameResult.get("result") != null ? renameResult.get("result").toString()
                            : "";
                    if ("ok".equals(resultValue)) {
                        // Store successful resource type in cache for future operations
                        resourceTypeCache.put(cacheKey, new CacheEntry(cachedType, System.currentTimeMillis()));
                        // Also cache the new public ID with the same resource type
                        resourceTypeCache.put(newPublicId, new CacheEntry(cachedType, System.currentTimeMillis()));

                        // Emit metric/counter for successful resource type
                        resourceTypeSuccessMetrics.compute(cachedType, (k, v) -> (v == null) ? 1L : v + 1L);
                        log.info(
                                "File moved successfully in Cloudinary using cached type: currentPublicId={}, newPublicId={}, resourceType={}, successCount={}",
                                currentPublicId, newPublicId, cachedType,
                                resourceTypeSuccessMetrics.get(cachedType));

                        Map<String, Object> resourceDetails = new HashMap<>();
                        resourceDetails.put("public_id", newPublicId);
                        resourceDetails.put("resource_type", cachedType);
                        return resourceDetails;
                    }
                } catch (Exception e) {
                    // If cached type fails, fall through to metrics-ordered loop
                    log.debug(
                            "Rename failed with cached resourceType={}, falling back to metrics-ordered iteration: currentPublicId={}, error={}",
                            cachedType, currentPublicId, e.getMessage());
                }
            }

            // Get resource types ordered by success metrics (most likely first)
            String[] resourceTypes = getOrderedResourceTypes();
            Exception lastException = null;
            Map<String, Object> result = null;
            String successfulResourceType = null;

            // Try rename with each resource type until one succeeds
            for (String type : resourceTypes) {
                // Skip if we already tried this type from cache
                if (cached != null && !cached.isExpired(CACHE_TTL_MS) && cached.resourceType.equals(type)) {
                    log.debug("Skipping resourceType={} as it was already tried from cache: publicId={}", type,
                            cacheKey);
                    continue;
                }

                try {
                    // Use Cloudinary rename API to move the file
                    // Note: rename API only accepts: image, raw, or video (not "auto")
                    Map<String, Object> renameOptions = new HashMap<>();
                    renameOptions.put("resource_type", type);
                    renameOptions.put("type", "authenticated"); // Required for authenticated resources

                    @SuppressWarnings("unchecked")
                    Map<String, Object> renameResult = cloudinary.uploader().rename(currentPublicId, newPublicId,
                            renameOptions);

                    String resultValue = renameResult.get("result") != null ? renameResult.get("result").toString()
                            : "";
                    if ("ok".equals(resultValue)) {
                        result = renameResult;
                        successfulResourceType = type;

                        // Store successful resource type in cache for future operations
                        resourceTypeCache.put(cacheKey, new CacheEntry(type, System.currentTimeMillis()));
                        // Also cache the new public ID with the same resource type
                        resourceTypeCache.put(newPublicId, new CacheEntry(type, System.currentTimeMillis()));

                        // Emit metric/counter for successful resource type
                        resourceTypeSuccessMetrics.compute(type, (k, v) -> (v == null) ? 1L : v + 1L);
                        log.info(
                                "File moved successfully in Cloudinary: currentPublicId={}, newPublicId={}, resourceType={}, successCount={}",
                                currentPublicId, newPublicId, type,
                                resourceTypeSuccessMetrics.get(type));

                        break;
                    } else {
                        log.debug("Rename failed with resourceType={}, result={}: currentPublicId={}, newPublicId={}",
                                type, resultValue, currentPublicId, newPublicId);
                        lastException = new StorageException("Failed to move file in Cloudinary: " + resultValue);
                    }
                } catch (Exception e) {
                    // If rename fails, try next resource type
                    lastException = e;
                    log.debug("Rename failed with resourceType={}, trying next: currentPublicId={}, error={}",
                            type, currentPublicId, e.getMessage());
                    continue;
                }
            }

            // If all resource types failed, throw exception
            if (result == null || successfulResourceType == null) {
                log.error(
                        "Failed to move file in Cloudinary with any resource type: currentPublicId={}, newPublicId={}",
                        currentPublicId, newPublicId);
                if (lastException != null) {
                    throw new StorageException("Failed to move file in Cloudinary: " + lastException.getMessage(),
                            lastException);
                } else {
                    throw new StorageException("Failed to move file in Cloudinary: all resource types failed");
                }
            }

            // For authenticated resources, we can't use Admin API to get updated resource
            // details
            // Return the rename result which contains the new public_id
            // The URLs will be regenerated when needed using getFileUrl()
            Map<String, Object> resourceDetails = new HashMap<>();
            resourceDetails.put("public_id", newPublicId);
            resourceDetails.put("resource_type", successfulResourceType);
            // Note: URL fields may not be available for authenticated resources via rename
            // API
            // They will be generated on-demand using getFileUrl()

            return resourceDetails;
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Error moving file in Cloudinary: currentPublicId={}, newFolderPath={}, resourceType={}, error={}",
                    currentPublicId, newFolderPath, resourceType, e.getMessage(), e);
            throw new StorageException("Failed to move file in Cloudinary", e);
        }
    }

    /**
     * Resolves the resource type for a given publicId by checking cache first,
     * then calling Admin API if needed. Caches the result with TTL.
     * 
     * @param publicId The public ID of the resource
     * @return The resolved resource type ("image", "video", or "raw"), or null if
     *         unresolved
     */
    private String resolveResourceType(String publicId) {
        // Check cache first
        CacheEntry cached = resourceTypeCache.get(publicId);
        if (cached != null && !cached.isExpired(CACHE_TTL_MS)) {
            log.debug("Resource type cache hit: publicId={}, resourceType={}", publicId, cached.resourceType);
            return cached.resourceType;
        }

        // Cache miss or expired - call Admin API directly
        // Try common resource types: image, video, raw
        String[] resourceTypes = { "image", "video", "raw" };

        for (String type : resourceTypes) {
            try {
                // Try with type="authenticated" first
                Map<String, Object> apiOptions = new HashMap<>();
                apiOptions.put("resource_type", type);
                apiOptions.put("type", "authenticated");
                @SuppressWarnings("unchecked")
                Map<String, Object> resource = (Map<String, Object>) cloudinary.api().resource(publicId, apiOptions);
                String resourceType = (String) resource.get("resource_type");

                // Ignore "auto" - it's not a valid resource type for API calls
                if (resourceType != null && !resourceType.isEmpty() && !resourceType.equals("auto")) {
                    // Cache the result
                    resourceTypeCache.put(publicId, new CacheEntry(resourceType, System.currentTimeMillis()));
                    log.debug("Resource type resolved and cached: publicId={}, resourceType={}", publicId,
                            resourceType);
                    return resourceType;
                }
            } catch (Exception e) {
                // If it's a "not found" error, try without type parameter
                if (e.getMessage() != null && e.getMessage().contains("not found")) {
                    try {
                        Map<String, Object> apiOptions = new HashMap<>();
                        apiOptions.put("resource_type", type);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resource = (Map<String, Object>) cloudinary.api().resource(publicId,
                                apiOptions);
                        String resourceType = (String) resource.get("resource_type");

                        if (resourceType != null && !resourceType.isEmpty() && !resourceType.equals("auto")) {
                            // Cache the result
                            resourceTypeCache.put(publicId, new CacheEntry(resourceType, System.currentTimeMillis()));
                            log.debug("Resource type resolved and cached: publicId={}, resourceType={}", publicId,
                                    resourceType);
                            return resourceType;
                        }
                    } catch (Exception e2) {
                        // Continue to next resource type
                        continue;
                    }
                }
                // For other errors, continue to next resource type
                continue;
            }
        }

        // All resource types failed - log debug and return null to trigger fallback
        log.debug("Admin API failed to resolve resource type for any type, returning null for fallback: publicId={}",
                publicId);
        return null;
    }

    /**
     * Infer resource_type from format (file extension)
     * 
     * @param format File format/extension (e.g., "jpg", "png", "mp4", "pdf")
     * @return Inferred resource_type: "image", "video", or "raw"
     */
    private String inferResourceTypeFromFormat(String format) {
        if (format == null || format.isEmpty()) {
            return "raw";
        }

        String lowerFormat = format.toLowerCase();

        // Image formats
        String[] imageFormats = { "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico", "tiff", "tif", "heic",
                "heif" };
        for (String imgFormat : imageFormats) {
            if (lowerFormat.equals(imgFormat)) {
                return "image";
            }
        }

        // Video formats
        String[] videoFormats = { "mp4", "mov", "avi", "mkv", "webm", "flv", "wmv", "m4v", "3gp", "ogv", "mpg",
                "mpeg" };
        for (String vidFormat : videoFormats) {
            if (lowerFormat.equals(vidFormat)) {
                return "video";
            }
        }

        // Default to "raw" for other formats (PDF, documents, etc.)
        return "raw";
    }

    /**
     * Returns resource types ordered by success metrics (most likely to succeed
     * first).
     * This method uses collected metrics to optimize the order of resource type
     * attempts,
     * reducing latency by trying the most successful types first.
     * <p>
     * If no metrics are available, returns the default order: ["raw", "image",
     * "video"].
     *
     * @return Array of resource types ordered by success metrics (descending)
     */
    private String[] getOrderedResourceTypes() {
        // Default order if no metrics available
        String[] defaultOrder = { "raw", "image", "video" };

        // If metrics are empty, return default order
        if (resourceTypeSuccessMetrics.isEmpty()) {
            return defaultOrder;
        }

        // Sort resource types by success count (descending)
        // Create a list of entries and sort by value
        List<Map.Entry<String, Long>> entries = new ArrayList<>(
                resourceTypeSuccessMetrics.entrySet());
        entries.sort((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()));

        // Extract ordered resource types
        String[] orderedTypes = new String[defaultOrder.length];
        int index = 0;

        // Add types from metrics (in order of success)
        for (Map.Entry<String, Long> entry : entries) {
            if (index < orderedTypes.length) {
                orderedTypes[index++] = entry.getKey();
            }
        }

        // Fill remaining slots with default types not in metrics
        for (String defaultType : defaultOrder) {
            boolean found = false;
            for (int i = 0; i < index; i++) {
                if (orderedTypes[i].equals(defaultType)) {
                    found = true;
                    break;
                }
            }
            if (!found && index < orderedTypes.length) {
                orderedTypes[index++] = defaultType;
            }
        }

        log.debug("Resource types ordered by metrics: {}", java.util.Arrays.toString(orderedTypes));
        return orderedTypes;
    }

}
