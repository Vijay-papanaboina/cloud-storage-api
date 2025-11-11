package github.vijay_papanaboina.cloud_storage_api.config;

import github.vijay_papanaboina.cloud_storage_api.security.CompositeAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    @Autowired
    private CompositeAuthenticationFilter compositeAuthenticationFilter;

    @Autowired(required = false)
    private CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for stateless API
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/health", "/actuator/health").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated())
                // Add composite authentication filter before
                // UsernamePasswordAuthenticationFilter
                // The composite filter ensures deterministic execution order:
                // API Key filter first, then JWT filter
                .addFilterBefore(compositeAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Set allowed origins from configuration
        List<String> origins;
        if (corsProperties != null && corsProperties.getAllowedOrigins() != null
                && !corsProperties.getAllowedOrigins().isEmpty()) {
            origins = corsProperties.getAllowedOrigins();
        } else {
            // Default fallback
            origins = Arrays.asList("http://localhost:3000", "http://localhost:8080");
        }
        configuration.setAllowedOrigins(origins);

        // Set allowed methods from configuration or defaults
        List<String> methods;
        if (corsProperties != null && corsProperties.getAllowedMethods() != null
                && !corsProperties.getAllowedMethods().isEmpty()) {
            methods = corsProperties.getAllowedMethods();
        } else {
            methods = Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");
        }
        configuration.setAllowedMethods(methods);

        // Set allowed headers from configuration or defaults
        List<String> headers;
        if (corsProperties != null && corsProperties.getAllowedHeaders() != null
                && !corsProperties.getAllowedHeaders().isEmpty()) {
            headers = corsProperties.getAllowedHeaders();
        } else {
            headers = Arrays.asList("*");
        }

        // Set allow credentials from configuration or default (false for security)
        boolean allowCreds = corsProperties != null && corsProperties.isAllowCredentials();
        // CORS spec violation: cannot use wildcard "*" for headers when
        // allowCredentials is true
        // Replace wildcard with explicit list of safe headers if credentials are
        // allowed
        if (allowCreds && headers.contains("*")) {
            // Use explicit list of safe headers when credentials are allowed
            headers = Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin");
        }

        configuration.setAllowedHeaders(headers);
        configuration.setAllowCredentials(allowCreds);

        // Set max age from configuration or default
        long maxAge = corsProperties != null ? corsProperties.getMaxAge() : 3600L;
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
