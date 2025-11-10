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
     * @throws StorageException if the file is null or empty, if file size exceeds
     *                          the maximum limit, if an I/O error occurs during
     *                          file read, or if the upload to Cloudinary fails
     */
    Map<String, Object> uploadFile(MultipartFile file, String folderPath, Map<String, Object> options);

    /**
     * Download a file from Cloudinary
     *
     * @param publicId Cloudinary public ID
     * @return File bytes (non-null, non-empty array on success)
     * @throws StorageException if the file is not found, if the download URL cannot
     *                          be generated,
     *                          if an I/O error occurs during download, or if any
     *                          other error occurs
     *                          during the download process. The exception message
     *                          will indicate the
     *                          specific failure reason (e.g., "File not found in
     *                          Cloudinary: {publicId}"
     *                          for missing files, "Failed to download file from
     *                          Cloudinary" for I/O errors).
     */
    byte[] downloadFile(String publicId);

    /**
     * Delete a file from Cloudinary
     *
     * @param publicId Cloudinary public ID
     * @return true if the file was successfully deleted, false if the file was
     *         not found or deletion failed
     * @throws StorageException if an unexpected error occurs during the deletion
     *                          process (e.g., network errors, API errors). Note:
     *                          "not found" cases return false rather than throwing
     *                          an exception.
     */
    boolean deleteFile(String publicId);

    /**
     * Generate Cloudinary CDN URL
     *
     * @param publicId Cloudinary public ID
     * @param secure   Use HTTPS URL
     * @return Cloudinary CDN URL (non-null on success)
     * @throws StorageException if the URL cannot be generated due to invalid
     *                          publicId or other errors
     */
    String getFileUrl(String publicId, boolean secure);

    /**
     * Generate a signed download URL for a file
     *
     * @param publicId          Cloudinary public ID
     * @param expirationMinutes URL expiration time in minutes (must be
     *                          non-negative)
     * @return Signed Cloudinary URL with expiration
     * @throws IllegalArgumentException if expirationMinutes is negative or would
     *                                  cause overflow
     * @throws StorageException         if URL generation fails, if resource format
     *                                  is missing, or if resource is not found
     */
    String generateSignedDownloadUrl(String publicId, int expirationMinutes);

    /**
     * Generate a signed download URL for a file with resource type
     *
     * @param publicId          Cloudinary public ID
     * @param expirationMinutes URL expiration time in minutes (must be
     *                          non-negative)
     * @param resourceType      Optional resource type (image, video, raw). If
     *                          null, will try all supported resource types (image,
     *                          video, raw)
     *                          until the resource is found. Note: This sequential
     *                          iteration may have
     *                          a performance impact when the resource type is
     *                          unknown. Note: "auto" is not a valid resource type
     *                          for Admin API calls.
     * @return Signed Cloudinary URL with expiration
     * @throws IllegalArgumentException if expirationMinutes is negative or would
     *                                  cause overflow
     * @throws StorageException         if URL generation fails, if resource format
     *                                  is missing, or if resource is not found
     */
    String generateSignedDownloadUrl(String publicId, int expirationMinutes, String resourceType);

    /**
     * Get resource details from Cloudinary
     *
     * @param publicId Cloudinary public ID
     * @return Map containing resource information (non-null on success)
     * @throws StorageException if the resource is not found, if an API error
     *                          occurs, or if any other error occurs during the
     *                          retrieval process. The exception message will
     *                          indicate the specific failure reason (e.g.,
     *                          "Resource not found in Cloudinary: {publicId}" for
     *                          missing resources).
     */
    Map<String, Object> getResourceDetails(String publicId);

    /**
     * Get resource details from Cloudinary with resource type
     *
     * @param publicId     Cloudinary public ID
     * @param resourceType Optional resource type (image, video, raw). If
     *                     null or "auto", will try all supported resource types
     *                     (image,
     *                     video, raw)
     *                     until the resource is found. Note: This sequential
     *                     iteration may have
     *                     a performance impact when the resource type is unknown.
     *                     Note: "auto" is not a valid resource type for Admin API
     *                     calls.
     * @return Map containing resource information (non-null on success)
     * @throws StorageException if the resource is not found, if an API error
     *                          occurs, or if any other error occurs during the
     *                          retrieval process. The exception message will
     *                          indicate the specific failure reason (e.g.,
     *                          "Resource not found in Cloudinary: {publicId}" for
     *                          missing resources).
     */
    Map<String, Object> getResourceDetails(String publicId, String resourceType);

    /**
     * Generate transformation URL for image/video
     *
     * @param publicId Cloudinary public ID
     * @param secure   Use HTTPS URL
     * @param width    Optional width
     * @param height   Optional height
     * @param crop     Optional crop mode
     * @param quality  Optional quality setting
     * @param format   Optional output format
     * @return Transformed Cloudinary URL (non-null on success)
     * @throws StorageException if the transformation URL cannot be generated
     *                          due to invalid parameters, invalid publicId, or
     *                          other errors
     */
    String getTransformUrl(String publicId, boolean secure, Integer width, Integer height,
            String crop, String quality, String format);

    /**
     * Move a file to a different folder in Cloudinary by renaming its public ID.
     * This updates the file's location in Cloudinary to match the new folder path.
     *
     * @param currentPublicId Current public ID (may include folder path)
     * @param newFolderPath   New folder path (null or empty means root folder)
     * @return Map containing the updated Cloudinary response with new public_id,
     *         url, secure_url, etc.
     * @throws StorageException if the file cannot be moved (e.g., file not found,
     *                          rename fails)
     */
    Map<String, Object> moveFile(String currentPublicId, String newFolderPath);

    /**
     * Move a file to a different folder in Cloudinary by renaming its public ID.
     * This updates the file's location in Cloudinary to match the new folder path.
     *
     * @param currentPublicId Current public ID (may include folder path)
     * @param newFolderPath   New folder path (null or empty means root folder)
     * @param resourceType    Optional resource type (image, video, raw). If
     *                        null or "auto", will infer the resource type from the
     *                        resource's format field. If format is missing, throws
     *                        StorageException. Note: "auto" is not a valid resource
     *                        type
     *                        for Admin API calls.
     * @return Map containing the updated Cloudinary response with new public_id,
     *         url, secure_url, etc.
     * @throws StorageException if the file cannot be moved (e.g., file not found,
     *                          rename fails, or resource type cannot be determined)
     */
    Map<String, Object> moveFile(String currentPublicId, String newFolderPath, String resourceType);
}
