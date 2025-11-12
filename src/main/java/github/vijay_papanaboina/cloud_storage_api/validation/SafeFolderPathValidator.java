package github.vijay_papanaboina.cloud_storage_api.validation;

import github.vijay_papanaboina.cloud_storage_api.exception.BadRequestException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Validator for safe folder paths that prevents path traversal attacks.
 * 
 * Validates that the path:
 * 1. Does not contain null characters
 * 2. Does not contain ".." segments (path traversal)
 * 3. Does not contain backslashes (Windows-style paths)
 * 4. When normalized, does not escape the root directory
 * 5. Must start with '/' (absolute Unix-style path)
 * 
 * This validator treats paths as Unix-style virtual paths regardless of the OS,
 * since folder paths are stored in the database and not actual file system
 * paths. It uses pure string-based validation to ensure consistent behavior
 * across all operating systems without relying on OS-specific path APIs.
 */
public class SafeFolderPathValidator implements ConstraintValidator<SafeFolderPath, String> {

    @Override
    public void initialize(SafeFolderPath constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String path, ConstraintValidatorContext context) {
        if (path == null) {
            // Let @NotBlank handle null validation
            return true;
        }

        // Check for null characters
        if (path.contains("\0")) {
            return buildViolation(context, "Folder path must not contain null characters");
        }

        // Ensure Unix-style paths only (no backslashes)
        if (path.contains("\\")) {
            return buildViolation(context, "Folder path must use Unix-style format with forward slashes only");
        }

        // Must start with '/' for absolute virtual paths
        if (!path.startsWith("/")) {
            return buildViolation(context, "Folder path must start with '/' (absolute path required)");
        }

        // Check for invalid characters in path
        if (path.matches(".*[<>:\"|?*].*")) {
            return buildViolation(context, "Folder path contains invalid characters");
        }

        // Perform Unix-style path normalization and validate
        String normalized = normalizeUnixPath(path);

        if (normalized == null) {
            return buildViolation(context, "Folder path normalization failed due to path traversal attempt");
        }

        // After normalization, ensure it still starts with '/' (hasn't escaped root)
        if (!normalized.startsWith("/")) {
            return buildViolation(context, "Folder path cannot escape the root directory");
        }

        // Additional safety check: verify no ".." segments remain in the normalized
        // path
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if ("..".equals(segment)) {
                return buildViolation(context, "Folder path contains invalid '..' segments");
            }
        }

        return true;
    }

    /**
     * Normalizes a Unix-style path by resolving "." and ".." segments.
     * Returns null if the path attempts to escape the root directory.
     * 
     * This implementation is OS-independent and treats all paths as Unix-style
     * virtual paths, ensuring consistent behavior across Windows, Linux, and macOS.
     * 
     * @param path the path to normalize (must start with '/')
     * @return the normalized path, or null if path traversal would escape root
     */
    private String normalizeUnixPath(String path) {
        return normalizeUnixPathStatic(path);
    }

    /**
     * Helper method to build a constraint violation with a custom message.
     * 
     * @param context the constraint validator context
     * @param message the error message
     * @return false (always returns false to indicate validation failure)
     */
    private boolean buildViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
        return false;
    }

    /**
     * Programmatically validate a folder path and throw BadRequestException if
     * invalid.
     * This method can be used outside of annotation-based validation contexts.
     * 
     * @param path the folder path to validate (null/empty paths are allowed and
     *             will pass)
     * @throws BadRequestException if the path is invalid
     */
    public static void validatePath(String path) {
        if (path == null || path.isEmpty()) {
            // Allow null/empty paths - let callers handle this if needed
            return;
        }

        // Check for null characters
        if (path.contains("\0")) {
            throw new BadRequestException("Folder path must not contain null characters");
        }

        // Ensure Unix-style paths only (no backslashes)
        if (path.contains("\\")) {
            throw new BadRequestException("Folder path must use Unix-style format with forward slashes only");
        }

        // Must start with '/' for absolute virtual paths
        if (!path.startsWith("/")) {
            throw new BadRequestException("Folder path must start with '/' (absolute path required)");
        }

        // Check for invalid characters in path
        if (path.matches(".*[<>:\"|?*].*")) {
            throw new BadRequestException("Folder path contains invalid characters");
        }

        // Perform Unix-style path normalization and validate
        String normalized = normalizeUnixPathStatic(path);

        if (normalized == null) {
            throw new BadRequestException("Folder path normalization failed due to path traversal attempt");
        }

        // After normalization, ensure it still starts with '/' (hasn't escaped root)
        if (!normalized.startsWith("/")) {
            throw new BadRequestException("Folder path cannot escape the root directory");
        }

        // Additional safety check: verify no ".." segments remain in the normalized
        // path
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if ("..".equals(segment)) {
                throw new BadRequestException("Folder path contains invalid '..' segments");
            }
        }
    }

    /**
     * Static version of normalizeUnixPath for use in validatePath method.
     * Normalizes a Unix-style path by resolving "." and ".." segments.
     * Returns null if the path attempts to escape the root directory.
     * 
     * @param path the path to normalize (must start with '/')
     * @return the normalized path, or null if path traversal would escape root
     */
    private static String normalizeUnixPathStatic(String path) {
        if (path == null || !path.startsWith("/")) {
            return null;
        }

        // Split path into segments, removing empty segments
        String[] segments = path.split("/");
        List<String> normalizedSegments = new ArrayList<>();

        for (String segment : segments) {
            // Skip empty segments and "." (current directory)
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }

            // Handle ".." (parent directory)
            if ("..".equals(segment)) {
                // If we're at the root, attempting to go up is invalid
                if (normalizedSegments.isEmpty()) {
                    return null; // Path traversal would escape root
                }
                // Remove the last segment (go up one level)
                normalizedSegments.remove(normalizedSegments.size() - 1);
            } else {
                // Regular segment, add it
                normalizedSegments.add(segment);
            }
        }

        // Reconstruct the normalized path
        if (normalizedSegments.isEmpty()) {
            return "/"; // Root directory
        }

        StringBuilder normalized = new StringBuilder();
        for (String segment : normalizedSegments) {
            normalized.append("/").append(segment);
        }

        return normalized.toString();
    }
}
