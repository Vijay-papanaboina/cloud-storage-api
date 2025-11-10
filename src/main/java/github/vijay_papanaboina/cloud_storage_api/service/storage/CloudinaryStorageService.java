package github.vijay_papanaboina.cloud_storage_api.service.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
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
            // Generate unique filename (UUID with original extension)
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String publicId = UUID.randomUUID().toString() + (extension != null ? "." + extension : "");

            // Prepare upload options
            Map<String, Object> uploadOptions = new HashMap<>();
            if (options != null) {
                uploadOptions.putAll(options);
            }

            // Add folder path if provided
            if (folderPath != null && !folderPath.isEmpty()) {
                uploadOptions.put("folder", folderPath);
            }

            // Set resource type to auto-detect
            if (!uploadOptions.containsKey("resource_type")) {
                uploadOptions.put("resource_type", "auto");
            }

            // Use generated public ID
            uploadOptions.put("public_id", publicId);
            uploadOptions.put("use_filename", false);
            uploadOptions.put("unique_filename", false);

            // Upload to Cloudinary
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
            // Get file URL and download via HTTP
            String url = cloudinary.url().secure(true).generate(publicId);
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
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            String resultValue = result.get("result") != null ? result.get("result").toString() : "";
            boolean deleted = "ok".equals(resultValue);

            if (deleted) {
                log.info("File deleted successfully from Cloudinary: publicId={}", publicId);
            } else {
                log.warn("File deletion returned unexpected result: publicId={}, result={}",
                        publicId, resultValue);
            }

            return deleted;
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
            String url = cloudinary.url()
                    .secure(secure)
                    .generate(publicId);
            return url;
        } catch (Exception e) {
            log.error("Failed to generate Cloudinary URL: publicId={}, error={}",
                    publicId, e.getMessage(), e);
            throw new StorageException("Failed to generate Cloudinary URL", e);
        }
    }

    @Override
    public Map<String, Object> getResourceDetails(String publicId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = (Map<String, Object>) cloudinary.api().resource(publicId,
                    ObjectUtils.emptyMap());
            return resource;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                log.error("Resource not found in Cloudinary: publicId={}", publicId);
                throw new StorageException("Resource not found in Cloudinary: " + publicId, e);
            }
            log.error("Error getting resource details: publicId={}, error={}",
                    publicId, e.getMessage(), e);
            throw new StorageException("Failed to get resource details from Cloudinary", e);
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
     * Custom exception for storage operations
     */
    public static class StorageException extends RuntimeException {
        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
