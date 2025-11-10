package github.vijay_papanaboina.cloud_storage_api.security;

import github.vijay_papanaboina.cloud_storage_api.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Utility class for security-related operations.
 * Provides methods to extract authenticated user information from
 * SecurityContext.
 */
public class SecurityUtils {
    private static final Logger log = LoggerFactory.getLogger(SecurityUtils.class);

    /**
     * Get the authenticated user ID from SecurityContext.
     * 
     * @return The authenticated user ID
     * @throws UnauthorizedException if no user is authenticated or user ID cannot
     *                               be extracted
     */
    public static UUID getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Attempt to access authenticated user ID without authentication");
            throw new UnauthorizedException("User is not authenticated");
        }

        Object principal = authentication.getPrincipal();

        if (principal == null) {
            log.warn("Authentication principal is null");
            throw new UnauthorizedException("User is not authenticated");
        }

        // Handle UUID principal directly
        if (principal instanceof UUID) {
            return (UUID) principal;
        }

        // Handle String principal (UUID as string)
        if (principal instanceof String) {
            try {
                return UUID.fromString((String) principal);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format in principal of type String");
                throw new UnauthorizedException("Invalid user authentication");
            }
        }
        // Handle UserDetails or custom principal with getId() method
        try {
            // Try to get user ID via reflection for custom principal types
            java.lang.reflect.Method getIdMethod = principal.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(principal);

            if (id instanceof UUID) {
                return (UUID) id;
            }
            if (id instanceof String) {
                return UUID.fromString((String) id);
            }
        } catch (Exception e) {
            log.debug("Could not extract user ID from principal: {}", e.getMessage());
        }

        log.warn("Unable to extract user ID from principal type: {}", principal.getClass().getName());
        throw new UnauthorizedException("Unable to extract user ID from authentication");
    }
}
