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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudinaryStorageService implements StorageService {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryStorageService.class);
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

    private final Cloudinary cloudinary;

    @Autowired
    public CloudinaryStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public Map<String, Object> uploadFile(MultipartFile file, String folderPath, Map<String, Object> options) {
        // Validate file
        validateFile(file);

        try {
            // Generate unique public ID (UUID only, no extension)
            String publicId = UUID.randomUUID().toString();

            // Extract file extension from original filename
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                throw new IllegalArgumentException("File original filename cannot be null or blank");
            }
            String extension = getFileExtension(originalFilename);

            // Prepare upload options using SDK 2.0 ObjectUtils for cleaner code
            Map<String, Object> uploadOptions = new HashMap<>();
            if (options != null) {
                uploadOptions.putAll(options);
            }

            // Build options map with defaults using ObjectUtils
            uploadOptions.put("public_id", publicId);
            uploadOptions.put("use_filename", false);
            uploadOptions.put("unique_filename", false);
            uploadOptions.put("type", "authenticated"); // Require signed URLs for access
            uploadOptions.put("resource_type", uploadOptions.getOrDefault("resource_type", "auto"));

            if (folderPath != null && !folderPath.isEmpty()) {
                uploadOptions.put("folder", folderPath);
            }

            if (extension != null && !uploadOptions.containsKey("format")) {
                uploadOptions.put("format", extension);
            }

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
        try {
            // Get resource details to determine resource type for authenticated resources
            Map<String, Object> resourceDetails = getResourceDetails(publicId, null);
            String resourceType = (String) resourceDetails.get("resource_type");

            // Build signed authenticated URL for resources uploaded with
            // type="authenticated"
            com.cloudinary.Url urlBuilder = cloudinary.url()
                    .secure(true)
                    .signed(true)
                    .type("authenticated");

            // Include resourceType if needed (not "auto" or null)
            if (resourceType != null && !resourceType.isEmpty() && !resourceType.equals("auto")) {
                urlBuilder.resourceType(resourceType);
            }

            String url = urlBuilder.generate(publicId);
            java.net.URI uri = java.net.URI.create(url);
            java.net.URL fileUrl = uri.toURL();
            try (java.io.InputStream inputStream = fileUrl.openStream();
                    java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                byte[] fileBytes = outputStream.toByteArray();
                log.info("File downloaded successfully from Cloudinary: publicId={}", publicId);
                return fileBytes;
            }
        } catch (java.net.MalformedURLException e) {
            log.error("Invalid URL for file download: publicId={}, error={}",
                    publicId, e.getMessage(), e);
            throw new StorageException("Failed to generate download URL", e);
        } catch (java.io.FileNotFoundException e) {
            log.error("File not found in Cloudinary: publicId={}", publicId);
            throw new StorageException("File not found in Cloudinary: " + publicId, e);
        } catch (IOException e) {
            log.error("Failed to download file from Cloudinary: publicId={}, error={}",
                    publicId, e.getMessage(), e);
            throw new StorageException("Failed to download file from Cloudinary", e);
        } catch (Exception e) {
            log.error("Unexpected error during file download: publicId={}, error={}",
                    publicId, e.getMessage(), e);
            throw new StorageException("Unexpected error during file download", e);
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
            // Get resource details to determine resource type for authenticated resources
            Map<String, Object> resourceDetails = getResourceDetails(publicId, null);
            String resourceType = (String) resourceDetails.get("resource_type");

            // Build signed authenticated URL for resources uploaded with
            // type="authenticated"
            com.cloudinary.Url urlBuilder = cloudinary.url()
                    .secure(secure)
                    .signed(true)
                    .type("authenticated");

            // Include resourceType if needed (not "auto" or null)
            if (resourceType != null && !resourceType.isEmpty() && !resourceType.equals("auto")) {
                urlBuilder.resourceType(resourceType);
            }

            String url = urlBuilder.generate(publicId);

            log.info("Generated signed URL for authenticated resource: publicId={}, resourceType={}, secure={}",
                    publicId, resourceType, secure);
            return url;
        } catch (Exception e) {
            log.error("Failed to generate signed Cloudinary URL: publicId={}, error={}",
                    publicId, e.getMessage(), e);
            throw new StorageException("Failed to generate Cloudinary URL", e);
        }
    }

    @Override
    public String generateSignedDownloadUrl(String publicId, int expirationMinutes) {
        return generateSignedDownloadUrl(publicId, expirationMinutes, null);
    }

    @Override
    public String generateSignedDownloadUrl(String publicId, int expirationMinutes, String resourceType) {
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

            // Generate private download URL with enforced expiration
            // This approach uses privateDownload which enforces expiration via expires_at
            // First, get resource details to extract format
            Map<String, Object> resourceDetails = getResourceDetails(publicId, resourceType);
            String format = (String) resourceDetails.get("format");

            // Format is required for privateDownload - throw exception if missing
            if (format == null || format.isEmpty()) {
                log.error("Resource format is missing for privateDownload: publicId={}, resourceType={}", publicId,
                        resourceType);
                throw new StorageException(
                        "Cannot generate signed download URL: resource format is missing for publicId: " + publicId);
            }

            // Calculate expiration time in seconds (UNIX timestamp)
            long expirationTime = System.currentTimeMillis() / 1000L + expirationSeconds;

            // Generate private download URL with expiration using SDK 2.0 API
            @SuppressWarnings("unchecked")
            Map<String, Object> expiresOptions = ObjectUtils.asMap("expires_at", expirationTime);
            String signedUrl = cloudinary.privateDownload(publicId, format, expiresOptions);

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
                @SuppressWarnings("unchecked")
                Map<String, Object> resource = (Map<String, Object>) cloudinary.api().resource(publicId,
                        ObjectUtils.asMap("resource_type", resourceType));
                log.debug("Resource found with specified resource_type: publicId={}, resourceType={}", publicId,
                        resourceType);
                return resource;
            }

            // If resourceType is not provided or is "auto", iterate over resource types
            // Try common resource types: image, video, raw (auto is not valid for Admin
            // API)
            String[] resourceTypes = { "image", "video", "raw" };
            Exception lastException = null;

            for (String type : resourceTypes) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resource = (Map<String, Object>) cloudinary.api().resource(publicId,
                            ObjectUtils.asMap("resource_type", type));
                    log.debug("Resource found by iterating resource types: publicId={}, resourceType={}", publicId,
                            type);
                    return resource;
                } catch (Exception e) {
                    // If it's a "not found" error, try the next resource type
                    if (e.getMessage() != null && e.getMessage().contains("not found")) {
                        lastException = e;
                        continue;
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

            // Determine the actual resource type if not provided
            // Cloudinary rename API requires a valid resource_type: image, raw, or video
            // (not "auto")
            String actualResourceType = resourceType;
            if (actualResourceType == null || actualResourceType.isEmpty() || actualResourceType.equals("auto")) {
                // Get resource details to determine the actual resource type
                Map<String, Object> currentResourceDetails = getResourceDetails(currentPublicId, null);
                actualResourceType = (String) currentResourceDetails.get("resource_type");

                // Handle "auto" or missing resource_type by inferring from format
                if (actualResourceType == null || actualResourceType.isEmpty() || actualResourceType.equals("auto")) {
                    String format = (String) currentResourceDetails.get("format");
                    if (format != null && !format.isEmpty()) {
                        actualResourceType = inferResourceTypeFromFormat(format);
                        log.info(
                                "Inferred resource_type from format for rename: currentPublicId={}, format={}, resourceType={}",
                                currentPublicId, format, actualResourceType);
                    } else {
                        // If format is also missing, throw exception
                        log.error(
                                "Cannot determine resource type for rename: resource_type is 'auto' and format is missing: publicId={}",
                                currentPublicId);
                        throw new StorageException(
                                "Cannot determine resource type for rename: resource_type is 'auto' and format is missing for publicId: "
                                        + currentPublicId);
                    }
                }
            }

            // Use Cloudinary rename API to move the file
            // Note: rename API only accepts: image, raw, or video (not "auto")
            Map<String, Object> renameOptions = new HashMap<>();
            renameOptions.put("resource_type", actualResourceType);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().rename(currentPublicId, newPublicId, renameOptions);

            String resultValue = result.get("result") != null ? result.get("result").toString() : "";
            if (!"ok".equals(resultValue)) {
                log.error(
                        "Failed to move file in Cloudinary: currentPublicId={}, newPublicId={}, resourceType={}, result={}",
                        currentPublicId, newPublicId, actualResourceType, resultValue);
                throw new StorageException("Failed to move file in Cloudinary: " + resultValue);
            }

            // Get updated resource details with new URLs, using the determined resourceType
            Map<String, Object> resourceDetails = getResourceDetails(newPublicId, actualResourceType);

            log.info("File moved successfully in Cloudinary: currentPublicId={}, newPublicId={}, resourceType={}",
                    currentPublicId, newPublicId, actualResourceType);

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

}
