package github.vijay_papanaboina.cloud_storage_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Composite Authentication Filter that chains API Key and JWT authentication
 * filters
 * in a deterministic order: API Key filter first, then JWT filter.
 * 
 * This ensures deterministic execution order when both filters are added to the
 * Spring Security filter chain.
 */
@Component
public class CompositeAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    public CompositeAuthenticationFilter(
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Create a filter chain that executes API Key filter, then JWT filter, then the
        // original chain
        FilterChain compositeChain = new FilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest servletRequest,
                    jakarta.servlet.ServletResponse servletResponse)
                    throws IOException, ServletException {
                HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
                HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

                // Execute API Key filter first
                apiKeyAuthenticationFilter.doFilter(httpRequest, httpResponse, new FilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest servletRequest,
                            jakarta.servlet.ServletResponse servletResponse)
                            throws IOException, ServletException {
                        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
                        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

                        // Execute JWT filter second
                        jwtAuthenticationFilter.doFilter(httpRequest, httpResponse, filterChain);
                    }
                });
            }
        };

        compositeChain.doFilter(request, response);
    }
}
