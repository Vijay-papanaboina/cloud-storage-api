package github.vijay_papanaboina.cloud_storage_api.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface StorageService {
    /**
     * Upload a file to Cloudinary
     *
     * @param file       The file to upload
     * @param folderPath Optional folder path (e.g., "/photos/2024")
     * @param options    Additional upload options (resource_type, format, etc.)
     * @return Map containing Cloudinary response with public_id, url, secure_url,
     *         etc.
     */
    Map<String, Object> uploadFile(MultipartFile file, String folderPath, Map<String, Object> options);

    /**
     * Download a file from Cloudinary
     *
     * @param publicId Cloudinary public ID
     * @return File bytes
     */
    byte[] downloadFile(String publicId);

    /**
     * Delete a file from Cloudinary
     *
     * @param publicId Cloudinary public ID
     * @return true if successful, false otherwise
     */
    boolean deleteFile(String publicId);

    /**
     * Generate Cloudinary CDN URL
     *
     * @param publicId Cloudinary public ID
     * @param secure   Use HTTPS URL
     * @return Cloudinary CDN URL
     */
    String getFileUrl(String publicId, boolean secure);

    /**
     * Get resource details from Cloudinary
     *
     * @param publicId Cloudinary public ID
     * @return Map containing resource information
     */
    Map<String, Object> getResourceDetails(String publicId);
}
