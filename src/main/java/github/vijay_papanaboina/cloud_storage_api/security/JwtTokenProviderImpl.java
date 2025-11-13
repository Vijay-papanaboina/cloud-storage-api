package github.vijay_papanaboina.cloud_storage_api.security;

import github.vijay_papanaboina.cloud_storage_api.exception.InvalidTokenException;
import github.vijay_papanaboina.cloud_storage_api.model.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class JwtTokenProviderImpl implements JwtTokenProvider {
    private static final Logger log = LoggerFactory.getLogger(JwtTokenProviderImpl.class);
    private static final int MIN_SECRET_LENGTH = 32; // 256 bits for HS256 algorithm

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.jwt.access-token-expiration-web:900000}") // 15 minutes default
    private long accessTokenExpiration;

    @Value("${app.security.jwt.refresh-token-expiration-web:604800000}") // 7 days default
    private long refreshTokenExpiration;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        validateSecret();
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        log.info("JWT signing key initialized successfully");
    }

    private void validateSecret() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("JWT secret must not be null or empty");
        }
        if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    String.format("JWT secret must be at least %d characters (256 bits) for HS256 algorithm. " +
                            "Current length: %d characters", MIN_SECRET_LENGTH, jwtSecret.length()));
        }
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    @Override
    public String generateAccessToken(UUID userId, String username) {
        // Default: grant all permissions to JWT-authenticated users
        // This maintains current behavior while using explicit permissions
        List<String> defaultAuthorities = List.of(
                "ROLE_READ",
                "ROLE_WRITE",
                "ROLE_DELETE",
                "ROLE_MANAGE_API_KEYS");
        return generateAccessToken(userId, username, defaultAuthorities);
    }

    @Override
    public String generateAccessToken(UUID userId, String username, List<String> authorities) {
        long expiration = getTokenExpiration(TokenType.ACCESS);
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiryDate);

        // Add authorities claim if provided
        if (authorities != null && !authorities.isEmpty()) {
            builder.claim("authorities", authorities);
        }

        return builder.signWith(getSigningKey())
                .compact();
    }

    @Override
    public String generateRefreshToken(UUID userId, String username) {
        long expiration = getTokenExpiration(TokenType.REFRESH);
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public UUID getUserIdFromToken(String token) throws InvalidTokenException {
        Claims claims = getClaimsFromToken(token);
        String userIdStr = claims.getSubject();
        if (userIdStr == null || userIdStr.isEmpty()) {
            throw new InvalidTokenException("Token does not contain a valid user ID");
        }
        try {
            return UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid user ID format in token", e);
        }
    }

    @Override
    public String getUsernameFromToken(String token) throws InvalidTokenException {
        Claims claims = getClaimsFromToken(token);
        String username = claims.get("username", String.class);
        if (username == null || username.isEmpty()) {
            throw new InvalidTokenException("Token does not contain a username");
        }
        return username;
    }

    @Override
    public List<String> getAuthoritiesFromToken(String token) throws InvalidTokenException {
        Claims claims = getClaimsFromToken(token);
        Object authoritiesObj = claims.get("authorities");

        if (authoritiesObj == null) {
            // Backward compatibility: if no authorities claim, return empty list
            // This will cause permission checks to fail (fail-closed)
            return new ArrayList<>();
        }

        if (authoritiesObj instanceof List) {
            List<?> authoritiesList = (List<?>) authoritiesObj;
            List<String> authorities = new ArrayList<>();
            for (Object authority : authoritiesList) {
                if (authority instanceof String) {
                    authorities.add((String) authority);
                } else {
                    throw new InvalidTokenException("Invalid authority type in token: expected String but found "
                            + authority.getClass().getSimpleName());

                }
            }
            return authorities;
        }

        throw new InvalidTokenException("Invalid authorities format in token");
    }

    @Override
    public long getTokenExpiration(TokenType tokenType) {
        Objects.requireNonNull(tokenType, "tokenType must not be null");

        if (tokenType == TokenType.ACCESS) {
            return accessTokenExpiration;
        } else if (tokenType == TokenType.REFRESH) {
            return refreshTokenExpiration;
        } else {
            throw new IllegalArgumentException("Unsupported token type: " + tokenType);
        }
    }

    private Claims getClaimsFromToken(String token) throws InvalidTokenException {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Token has expired", e);
        } catch (MalformedJwtException e) {
            throw new InvalidTokenException("Token is malformed", e);
        } catch (SecurityException e) {
            throw new InvalidTokenException("Token signature is invalid", e);
        } catch (JwtException e) {
            throw new InvalidTokenException("Invalid token", e);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("Token is null or empty", e);
        }
    }
}
