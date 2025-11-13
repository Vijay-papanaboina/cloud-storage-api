package github.vijay_papanaboina.cloud_storage_api.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for managing httpOnly cookies.
 * Centralizes cookie configuration for security and consistency.
 */
@Component
public class CookieUtils {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/api/auth";

    @Value("${app.security.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${app.security.cookie.same-site:Strict}")
    private String cookieSameSite;

    /**
     * Validate cookieSameSite configuration value after dependency injection.
     * Ensures the value is one of the allowed SameSite cookie values (Strict, Lax,
     * or None).
     * 
     * @throws IllegalStateException if cookieSameSite is not a valid value
     */
    @PostConstruct
    public void validateCookieSameSite() {
        if (cookieSameSite == null || cookieSameSite.isBlank()) {
            throw new IllegalStateException(
                    "cookieSameSite cannot be null or blank. Must be one of: Strict, Lax, or None");
        }

        String normalizedValue = cookieSameSite.trim();
        boolean isValid = normalizedValue.equalsIgnoreCase("Strict")
                || normalizedValue.equalsIgnoreCase("Lax")
                || normalizedValue.equalsIgnoreCase("None");

        if (!isValid) {
            throw new IllegalStateException(
                    String.format(
                            "Invalid cookieSameSite value: '%s'. Must be one of: Strict, Lax, or None (case-insensitive)",
                            cookieSameSite));
        }
    }

    /**
     * Set refresh token as httpOnly cookie in the response.
     *
     * @param response     HTTP response
     * @param refreshToken Refresh token value
     * @param maxAge       Cookie max age in seconds
     */
    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, int maxAge) {
        // Set SameSite attribute via response header (Servlet API doesn't support it
        // directly)
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new IllegalArgumentException("refreshToken cannot be null or empty");
        }
        String encodedToken = URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);
        String sameSiteValue = "SameSite=" + cookieSameSite;
        String secureFlag = cookieSecure ? "Secure; " : "";
        response.addHeader("Set-Cookie",
                String.format("%s=%s; Path=%s; Max-Age=%d; HttpOnly; %s%s",
                        REFRESH_TOKEN_COOKIE_NAME, encodedToken, COOKIE_PATH, maxAge, secureFlag, sameSiteValue));
    }

    /**
     * Clear refresh token cookie from the response.
     *
     * @param response HTTP response
     */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        // Set SameSite attribute via response header
        String sameSiteValue = "SameSite=" + cookieSameSite;
        String secureFlag = cookieSecure ? "Secure; " : "";
        response.addHeader("Set-Cookie",
                String.format("%s=; Path=%s; Max-Age=0; HttpOnly; %s%s",
                        REFRESH_TOKEN_COOKIE_NAME, COOKIE_PATH, secureFlag, sameSiteValue));
    }

    /**
     * Extract refresh token from cookie in the request.
     *
     * @param request HTTP request
     * @return Refresh token value or null if not found
     */
    public String getRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isEmpty()) {
                    return URLDecoder.decode(value, StandardCharsets.UTF_8);
                }
                return null;
            }
        }
        return null;
    }
}
