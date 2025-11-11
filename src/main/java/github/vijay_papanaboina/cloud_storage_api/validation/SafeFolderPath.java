package github.vijay_papanaboina.cloud_storage_api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a folder path is safe and does not contain path traversal
 * sequences.
 * 
 * This validator:
 * - Rejects null characters
 * - Rejects ".." path segments (path traversal)
 * - Normalizes the path and verifies it doesn't escape the root
 * - Ensures the path starts with '/' after normalization
 */
@Documented
@Constraint(validatedBy = SafeFolderPathValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeFolderPath {
    String message() default "Folder path contains invalid characters or path traversal sequences";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
