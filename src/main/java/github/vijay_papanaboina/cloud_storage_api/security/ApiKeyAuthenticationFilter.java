package github.vijay_papanaboina.cloud_storage_api.security;

import github.vijay_papanaboina.cloud_storage_api.model.ApiKey;
import github.vijay_papanaboina.cloud_storage_api.repository.ApiKeyRepository;
import github.vijay_papanaboina.cloud_storage_api.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API Key Authentication Filter.
 * Extracts and validates API keys from the X-API-Key header.
 * Sets authentication in SecurityContext if API key is valid.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyService apiKeyService;

    @Value("${app.security.api-key.header-name:X-API-Key}")
    private String apiKeyHeaderName;

    @Autowired
    public ApiKeyAuthenticationFilter(@Lazy ApiKeyRepository apiKeyRepository, @Lazy ApiKeyService apiKeyService) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            // Skip if already authenticated (e.g., by JWT filter)
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                log.debug("Authentication already exists, skipping API key filter");
                filterChain.doFilter(request, response);
                return;
            }

            // Extract API key from header
            String apiKey = extractApiKeyFromRequest(request);

            if (apiKey != null) {
                // Validate API key
                Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKey(apiKey);

                if (apiKeyOpt.isPresent()) {
                    ApiKey key = apiKeyOpt.get();

                    // Validate key is active
                    if (!Boolean.TRUE.equals(key.getActive())) {
                        log.debug("API key is inactive: {}", key.getId());
                        filterChain.doFilter(request, response);
                        return;
                    }

                    // Validate key is not expired
                    if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(Instant.now())) {
                        log.debug("API key has expired: {}", key.getId());
                        filterChain.doFilter(request, response);
                        return;
                    }

                    // Extract user ID from API key
                    if (key.getUser() == null || key.getUser().getId() == null) {
                        log.warn("API key has no associated user: {}", key.getId());
                        filterChain.doFilter(request, response);
                        return;
                    }

                    UUID userId = key.getUser().getId();

                    // Update lastUsedAt timestamp
                    apiKeyService.updateLastUsedAt(key.getId());

                    // Extract permissions and create authorities
                    List<GrantedAuthority> authorities = createAuthoritiesFromPermissions(key.getPermissions());

                    // Create authentication object
                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            authorities);

                    // Set authentication in SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("API key authentication successful for user: {}", userId);
                } else {
                    log.debug("API key not found in database");
                }
            }

            // Continue filter chain
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("Error processing API key authentication", e);
            // Continue filter chain even on error
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Extract API key from X-API-Key header.
     *
     * @param request HTTP request
     * @return API key or null if not found
     */
    private String extractApiKeyFromRequest(HttpServletRequest request) {
        String apiKey = request.getHeader(apiKeyHeaderName);
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        return apiKey.trim();
    }

    /**
     * Create Spring Security authorities from API key permissions.
     *
     * @param permissions API key permissions
     * @return List of GrantedAuthority objects
     */
    private List<GrantedAuthority> createAuthoritiesFromPermissions(
            github.vijay_papanaboina.cloud_storage_api.model.ApiKeyPermission permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        if (permissions == null) {
            // Default to no permissions if null (fail-safe)
            log.warn("API key has null permissions, denying all access");
            return authorities; // Return empty list
        }

        // Add authorities based on permissions
        if (permissions.canRead()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_READ"));
        }
        if (permissions.canWrite()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_WRITE"));
        }
        if (permissions.canDelete()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_DELETE"));
        }
        if (permissions.canManageApiKeys()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_MANAGE_API_KEYS"));
        }

        return authorities;
    }
}
