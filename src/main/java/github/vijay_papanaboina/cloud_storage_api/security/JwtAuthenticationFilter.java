package github.vijay_papanaboina.cloud_storage_api.security;

import github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * JWT Authentication Filter.
 * Extracts and validates JWT tokens from the Authorization header.
 * Sets authentication in SecurityContext if token is valid.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            // Skip if already authenticated (e.g., by API key filter)
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                log.debug("Authentication already exists, skipping JWT filter");
                filterChain.doFilter(request, response);
                return;
            }

            // Extract token from Authorization header
            String token = extractTokenFromRequest(request);
            String authHeader = request.getHeader(AUTHORIZATION_HEADER);

            if (authHeader != null) {
                log.info("Authorization header received: {}... (length: {})",
                        authHeader.length() > 20 ? authHeader.substring(0, 20) : authHeader,
                        authHeader.length());
            } else {
                log.info("No Authorization header found in request to: {}", request.getRequestURI());
            }

            if (token != null) {
                log.info("JWT token extracted, validating...");
                // Validate token
                if (jwtTokenProvider.validateToken(token)) {
                    try {
                        // Extract user ID from token
                        UUID userId = jwtTokenProvider.getUserIdFromToken(token);

                        // Create authentication object
                        Authentication authentication = new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                null // No authorities for now
                        );

                        // Set authentication in SecurityContext
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.info("JWT authentication successful for user: {}", userId);
                    } catch (InvalidTokenException e) {
                        log.warn("Invalid JWT token: {}", e.getMessage());
                        // Continue without authentication - Spring Security will handle 401
                    }
                } else {
                    log.warn("JWT token validation failed");
                }
            } else {
                log.info("No JWT token found in request to: {}", request.getRequestURI());
            }
        } catch (Exception e) {
            log.error("Error processing JWT authentication", e);
            // Log error and let filter chain continue - do not call doFilter here
        }

        // Continue filter chain - always executed exactly once
        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Authorization header.
     * Expected format: "Bearer {token}"
     *
     * @param request HTTP request
     * @return JWT token or null if not found
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
