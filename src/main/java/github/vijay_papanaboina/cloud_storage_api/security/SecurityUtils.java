package github.vijay_papanaboina.cloud_storage_api.security;

import github.vijay_papanaboina.cloud_storage_api.exception.ForbiddenException;
import github.vijay_papanaboina.cloud_storage_api.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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

    /**
     * Check if the current authentication has a specific permission.
     * 
     * This method implements consistent permission checking regardless of
     * authentication
     * method (JWT or API key). Authentication method is orthogonal to
     * authorization.
     * 
     * Security behavior (fail-closed):
     * - If authentication is null or not authenticated: deny access (return false)
     * - If authorities are null or empty: deny access (return false)
     * - Otherwise: check if the required permission is present in authorities
     * 
     * This ensures that:
     * - All users (JWT or API key) are subject to the same permission checks
     * - No implicit privilege escalation based on authentication method
     * - Explicit permissions must be granted in the token/authentication object
     * - Future role-based restrictions can be added without changing this logic
     *
     * @param permission The permission to check (e.g., "ROLE_READ", "ROLE_WRITE",
     *                   "ROLE_DELETE", "ROLE_MANAGE_API_KEYS")
     * @return true if the user has the permission, false otherwise
     */
    public static boolean hasPermission(String permission) {
        if (permission == null) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // Fail-closed: If no authorities are present, deny access
        // This prevents granting permissions to unauthenticated or improperly
        // configured authentication objects
        if (authentication.getAuthorities() == null || authentication.getAuthorities().isEmpty()) {
            log.debug("Authentication has no authorities - denying permission: {}", permission);
            return false;
        }

        // Check if the required permission is present in authorities
        // This works consistently for both JWT and API key authentication
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals(permission));
    }

    /**
     * Check if the current authentication has permission and throw
     * ForbiddenException if not.
     *
     * @param permission The permission to check
     * @throws ForbiddenException if the user does not have the required permission
     */
    public static void requirePermission(String permission) {
        if (permission == null) {
            throw new IllegalArgumentException("Permission parameter cannot be null");
        }
        if (!hasPermission(permission)) {
            throw new ForbiddenException("Insufficient permissions: " + permission + " required");
        }
    }
}
